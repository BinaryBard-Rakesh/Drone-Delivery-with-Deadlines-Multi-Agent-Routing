import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * PathBenchmark
 * -------------
 * Benchmarks the PRS (Perimeter-Recursive Smoothing) geometric routing
 * algorithm — the exact core algorithm used in MCHRouteGUI.java
 * (computeDetourPath + smoothPath, running on exact traced/inflated
 * concave obstacle perimeters) — against a plain grid A* search.
 *
 * 1000 random (source, destination) pairs are generated on the loaded
 * map. For each pair both algorithms compute a path; execution time and
 * path length are recorded. Results are printed as a table and exported
 * to a real .xlsx spreadsheet (no external libraries required).
 */
public class PathBenchmark {

    static final double EPS = 1e-9;
    static final int POINT_PRECISION = 6;

    static int rows, cols;
    static char[][] grid;
    static List<List<Pt>> allHulls = new ArrayList<>();
    static Map<String, Boolean> segmentValidCache = new HashMap<>();
    static Random rand = new Random();

    // The map you want to test on
    static String mapPath = "Coding Java/MAPF/Boston_0_1024.map";

    public static void main(String[] args) {
        System.out.println("==================================================================================");
        System.out.println("         PRS (MCHRouteGUI core) vs A* PATHFINDING BENCHMARK (1000 ITERATIONS)     ");
        System.out.println("==================================================================================");

        try {
            System.out.print("Loading map... ");
            loadMap(mapPath);
            buildHulls();
            System.out.println("Done! Map size: " + cols + "x" + rows + ". Hulls: " + allHulls.size());
        } catch (Exception e) {
            System.out.println("\n[!] Error loading map: " + e.getMessage());
            System.out.println("Please check your mapPath variable.");
            return;
        }

        // --- WARMUP RUNS ---
        System.out.print("Warming up JVM... ");
        for (int i = 0; i < 5; i++) {
            Pt s = getRandomFreePoint();
            Pt d = getRandomFreePoint();
            computeGridAStar(s, d);
            segmentValidCache.clear();
            List<Pt> raw = computePRSPath(s, d, allHulls);
            smoothPath(raw, allHulls);
        }
        System.out.println("Done!\n");

        int successfulRuns = 0;
        int totalTarget = 1000;
        double totalPrsTime = 0, totalAStarTime = 0;
        double totalPrsLen = 0, totalAStarLen = 0;

        List<Object[]> exportRows = new ArrayList<>();

        System.out.println("-------------------------------------------------------------------------------------------------");
        System.out.printf("| %-4s | %-13s | %-13s | %-13s | %-13s | %-11s | %-11s |\n",
                "Run", "Source", "Dest", "PRS Time(ms)", "A* Time(ms)", "PRS Length", "A* Length");
        System.out.println("-------------------------------------------------------------------------------------------------");

        while (successfulRuns < totalTarget) {
            Pt src = getRandomFreePoint();
            Pt dst = getRandomFreePoint();
            if (samePoint(src, dst)) continue;

            // 1. RUN PURE GRID A*
            long startAStar = System.nanoTime();
            List<Pt> aStarRaw = computeGridAStar(src, dst);
            List<Pt> aStarSmooth = simplifyGridPath(aStarRaw);
            long timeAStarNs = System.nanoTime() - startAStar;
            double aStarTimeMs = timeAStarNs / 1_000_000.0;
            double aStarLen = totalLength(aStarSmooth);

            // Verify a path was actually found (avoid completely walled-off pairs)
            if (aStarRaw.size() <= 2 && !isStrictLOSClear(src, dst)) {
                continue;
            }

            // 2. RUN PRS — exact core algorithm from MCHRouteGUI.java
            segmentValidCache.clear();
            long startPrs = System.nanoTime();
            List<Pt> rawPrs = computePRSPath(src, dst, allHulls);
            List<Pt> prsPath = smoothPath(rawPrs, allHulls);
            long timePrsNs = System.nanoTime() - startPrs;
            double prsTimeMs = timePrsNs / 1_000_000.0;
            double prsLen = totalLength(prsPath);

            successfulRuns++;
            totalPrsTime += prsTimeMs;
            totalAStarTime += aStarTimeMs;
            totalPrsLen += prsLen;
            totalAStarLen += aStarLen;

            String sStr = String.format("(%d, %d)", (int) src.x, (int) src.y);
            String dStr = String.format("(%d, %d)", (int) dst.x, (int) dst.y);

            exportRows.add(new Object[]{
                    successfulRuns, sStr, dStr, prsTimeMs, aStarTimeMs, prsLen, aStarLen
            });

            if (successfulRuns % 25 == 0 || successfulRuns == 1 || successfulRuns == totalTarget) {
                System.out.printf("| %-4d | %-13s | %-13s | %-13.3f | %-13.3f | %-11.2f | %-11.2f |\n",
                        successfulRuns, sStr, dStr, prsTimeMs, aStarTimeMs, prsLen, aStarLen);
            }
        }
        System.out.println("-------------------------------------------------------------------------------------------------");

        // --- EXPORT TO REAL .XLSX ---
        String userHome = System.getProperty("user.home");
        File outDir = new File(userHome, "Desktop");
        if (!outDir.exists()) outDir = new File(userHome);
        File xlsxFile = new File(outDir, "benchmark_results.xlsx");

        String[] headers = {"Run", "Source", "Destination", "PRS Time (ms)", "A* Time (ms)", "PRS Length", "A* Length"};
        try {
            writeXlsx(xlsxFile, "Benchmark Results", headers, exportRows);
            System.out.println("\n[SUCCESS] Exported " + exportRows.size() + " rows to: " + xlsxFile.getAbsolutePath());
        } catch (IOException e) {
            System.out.println("\n[!] ERROR writing xlsx: " + e.getMessage());
        }

        // --- PRINT SUMMARY ---
        System.out.println("\n==================================================================================");
        System.out.println("                              BENCHMARK SUMMARY (1000 RUNS)                       ");
        System.out.println("==================================================================================");
        System.out.printf("Average PRS Time  : %.3f ms\n", (totalPrsTime / totalTarget));
        System.out.printf("Average A* Time   : %.3f ms\n", (totalAStarTime / totalTarget));
        System.out.println("----------------------------------------------------------------------------------");
        System.out.printf("Average PRS Path Length : %.2f px\n", (totalPrsLen / totalTarget));
        System.out.printf("Average A* Path Length  : %.2f px\n", (totalAStarLen / totalTarget));
        System.out.println("==================================================================================\n");
    }

    // ==========================================================================
    // RANDOM POINT GENERATOR
    // ==========================================================================
    static Pt getRandomFreePoint() {
        while (true) {
            int r = rand.nextInt(rows);
            int c = rand.nextInt(cols);
            if (grid[r][c] != '@') {
                return new Pt(c + 0.5, r + 0.5);
            }
        }
    }

    // ==========================================================================
    // POINT CLASS
    // ==========================================================================
    static class Pt {
        final double x, y;
        Pt(double x, double y) { this.x = x; this.y = y; }
        @Override
        public boolean equals(Object o) {
            if (o == this) return true;
            if (!(o instanceof Pt)) return false;
            Pt p = (Pt) o;
            return Math.abs(x - p.x) <= 1e-6 && Math.abs(y - p.y) <= 1e-6;
        }
        @Override
        public int hashCode() { return Objects.hash(Math.round(x * 1000), Math.round(y * 1000)); }
        @Override
        public String toString() { return String.format("(%.2f, %.2f)", x, y); }
    }

    // ==========================================================================
    // MAP LOADING
    // ==========================================================================
    static void loadMap(String file) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(file));
        List<String> lines = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("type") || line.startsWith("height") || line.startsWith("width") || line.startsWith("map")) continue;
            if (!line.isEmpty()) lines.add(line);
        }
        br.close();
        rows = lines.size();
        cols = lines.get(0).length();
        grid = new char[rows][cols];
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                char c = lines.get(i).charAt(j);
                if (c == 'T' || c == 'O' || c == 'W' || c == '@') grid[i][j] = '@';
                else grid[i][j] = '.';
            }
        }
    }

    // ════════════════════════════════════════════════════════
    // EXACT INFLATED CONCAVE POLYGON EXTRACTION
    // ════════════════════════════════════════════════════════
    static void buildHulls() {
        allHulls.clear();
        boolean[][] seen = new boolean[rows][cols];
        double inf = 0.05;

        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == '@' && !seen[i][j]) {
                    List<Pt> perimeter = tracePerimeter(i, j);
                    if (!perimeter.isEmpty()) {
                        perimeter = simplifyPolygon(perimeter);
                        perimeter = inflatePolygon(perimeter, inf);
                        allHulls.add(perimeter);
                        floodFillCluster(i, j, seen);
                    }
                }
            }
        }
    }

    static List<Pt> tracePerimeter(int startR, int startC) {
        List<Pt> perim = new ArrayList<>();
        int[] dr = {-1, -1, 0, 1, 1, 1, 0, -1};
        int[] dc = {0, 1, 1, 1, 0, -1, -1, -1};

        int bDir = -1;
        for (int i = 0; i < 8; i++) {
            int r = startR + dr[i], c = startC + dc[i];
            if (r < 0 || c < 0 || r >= rows || c >= cols || grid[r][c] == '.') { bDir = i; break; }
        }
        if (bDir == -1) { perim.add(new Pt(startC, startR)); return perim; }

        int currR = startR, currC = startC;
        int currB = bDir;
        Set<Integer> added = new HashSet<>();

        do {
            if (!added.contains(currR * 100000 + currC)) {
                perim.add(new Pt(currC, currR));
                added.add(currR * 100000 + currC);
            }

            boolean found = false;
            for (int i = 1; i <= 8; i++) {
                int checkDir = (currB + i) % 8;
                int nr = currR + dr[checkDir];
                int nc = currC + dc[checkDir];

                if (nr >= 0 && nc >= 0 && nr < rows && nc < cols && grid[nr][nc] == '@') {
                    int freeDir = (currB + i - 1) % 8;
                    int freeR = currR + dr[freeDir];
                    int freeC = currC + dc[freeDir];

                    currR = nr; currC = nc;
                    currB = -1;
                    for (int j = 0; j < 8; j++) {
                        if (currR + dr[j] == freeR && currC + dc[j] == freeC) { currB = j; break; }
                    }
                    if (currB == -1) currB = (checkDir + 4) % 8;

                    found = true; break;
                }
            }
            if (!found || perim.size() > rows * cols) break;
        } while (!(currR == startR && currC == startC));

        return perim;
    }

    static void floodFillCluster(int sr, int sc, boolean[][] seen) {
        Queue<int[]> q = new LinkedList<>();
        q.add(new int[]{sr, sc}); seen[sr][sc] = true;
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        while (!q.isEmpty()) {
            int[] p = q.poll();
            for (int[] d : dirs) {
                int nr = p[0] + d[0], nc = p[1] + d[1];
                if (nr >= 0 && nc >= 0 && nr < rows && nc < cols && grid[nr][nc] == '@' && !seen[nr][nc]) {
                    seen[nr][nc] = true; q.add(new int[]{nr, nc});
                }
            }
        }
    }

    static List<Pt> simplifyPolygon(List<Pt> poly) {
        if (poly.size() <= 2) return poly;
        List<Pt> sim = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt prev = poly.get((i - 1 + n) % n);
            Pt curr = poly.get(i);
            Pt next = poly.get((i + 1) % n);
            if (Math.abs(cross(prev, curr, next)) > 1e-4) {
                sim.add(curr);
            }
        }
        return sim;
    }

    static List<Pt> inflatePolygon(List<Pt> poly, double inf) {
        if (poly.size() < 3) return poly;
        List<Pt> inflated = new ArrayList<>();
        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt prev = poly.get((i - 1 + n) % n);
            Pt curr = poly.get(i);
            Pt next = poly.get((i + 1) % n);

            double dx1 = curr.x - prev.x, dy1 = curr.y - prev.y;
            double len1 = Math.hypot(dx1, dy1);
            if (len1 > 0) { dx1 /= len1; dy1 /= len1; }
            double nx1 = dy1, ny1 = -dx1;

            double dx2 = next.x - curr.x, dy2 = next.y - curr.y;
            double len2 = Math.hypot(dx2, dy2);
            if (len2 > 0) { dx2 /= len2; dy2 /= len2; }
            double nx2 = dy2, ny2 = -dx2;

            Pt p1Start = new Pt(prev.x + nx1 * inf, prev.y + ny1 * inf);
            Pt p1End = new Pt(curr.x + nx1 * inf, curr.y + ny1 * inf);
            Pt p2Start = new Pt(curr.x + nx2 * inf, curr.y + ny2 * inf);
            Pt p2End = new Pt(next.x + nx2 * inf, next.y + ny2 * inf);

            Pt inter = lineIntersect(p1Start, p1End, p2Start, p2End);
            if (inter != null) inflated.add(inter);
            else inflated.add(new Pt(curr.x + nx1 * inf, curr.y + ny1 * inf));
        }
        return inflated;
    }

    // ════════════════════════════════════════════════════════
    // PRS — CORE GEOMETRIC ALGORITHM
    // ════════════════════════════════════════════════════════

    static class IntersectionResult {
        Pt pt; double t; int edgeIdx;
        IntersectionResult(Pt pt, double t, int edgeIdx) { this.pt = pt; this.t = t; this.edgeIdx = edgeIdx; }
    }

    static class HullAndIntersections {
        List<Pt> hull; List<IntersectionResult> intersections;
        HullAndIntersections(List<Pt> h, List<IntersectionResult> i) { this.hull = h; this.intersections = i; }
    }

    static HullAndIntersections findFirstIntersectingHull(Pt current, Pt destination, List<List<Pt>> hulls) {
        List<Pt> bestHull = null;
        double bestT = Double.MAX_VALUE;
        List<IntersectionResult> bestIntersections = null;

        for (List<Pt> hull : hulls) {
            if (!segmentCrossesPolygon(current, destination, hull)) continue;

            List<IntersectionResult> intersections = new ArrayList<>();
            int n = hull.size();
            for (int i = 0; i < n; i++) {
                Pt a = hull.get(i);
                Pt b = hull.get((i + 1) % n);
                Pt point = segmentIntersectionPoint(current, destination, a, b);

                if (point != null) {
                    double t = segmentParameterUnclamped(current, destination, point);
                    if (t >= -EPS && t <= 1 + EPS) {
                        addIntersection(intersections, point, t, i);
                    }
                }
            }

            if (intersections.isEmpty()) continue;

            double entryT = Double.MAX_VALUE;
            for (IntersectionResult r : intersections) {
                entryT = Math.min(entryT, r.t);
            }

            if (entryT < bestT) {
                bestT = entryT;
                bestHull = hull;
                bestIntersections = intersections;
            }
        }
        return new HullAndIntersections(bestHull, bestIntersections);
    }

    static void addIntersection(List<IntersectionResult> intersections, Pt point, double t, int edgeIdx) {
        for (IntersectionResult ex : intersections) {
            if (samePoint(ex.pt, point)) return;
        }
        intersections.add(new IntersectionResult(point, t, edgeIdx));
    }

    static class BoundItem {
        double pos; Pt point; boolean isVertex; int edgeIdx;
        BoundItem(double pos, Pt pt, boolean isVertex, int edgeIdx) {
            this.pos = pos; this.point = pt; this.isVertex = isVertex; this.edgeIdx = edgeIdx;
        }
    }

    static List<List<Pt>> shorterAndLongerPathVertices(List<Pt> polygon, List<IntersectionResult> intersections) {
        if (intersections.size() < 2) return new ArrayList<>();

        intersections.sort(Comparator.comparingDouble(a -> a.t));
        IntersectionResult startInter = intersections.get(0);
        IntersectionResult endInter = intersections.get(intersections.size() - 1);

        List<Double> edgeLengths = new ArrayList<>();
        List<Double> cum = new ArrayList<>();
        cum.add(0.0);
        int n = polygon.size();
        for (int i = 0; i < n; i++) {
            double len = distance(polygon.get(i), polygon.get((i + 1) % n));
            edgeLengths.add(len);
            cum.add(cum.get(cum.size() - 1) + len);
        }
        double perimeter = cum.get(cum.size() - 1);

        List<BoundItem> items = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            items.add(new BoundItem(cum.get(i), polygon.get(i), true, i));
        }

        for (IntersectionResult r : Arrays.asList(startInter, endInter)) {
            Pt edgeStart = polygon.get(r.edgeIdx);
            Pt edgeEnd = polygon.get((r.edgeIdx + 1) % n);

            boolean exists = false;
            for (BoundItem it : items) {
                if (samePoint(r.pt, it.point)) { exists = true; break; }
            }
            if (exists) continue;

            double t = segmentParameterUnclamped(edgeStart, edgeEnd, r.pt);
            t = Math.max(0.0, Math.min(1.0, t));
            double pos = cum.get(r.edgeIdx) + t * edgeLengths.get(r.edgeIdx);
            items.add(new BoundItem(pos, r.pt, false, r.edgeIdx));
        }

        items.sort(Comparator.comparingDouble(a -> a.pos));

        int startIdx = -1, endIdx = -1;
        for (int i = 0; i < items.size(); i++) {
            if (samePoint(items.get(i).point, startInter.pt)) startIdx = i;
            if (samePoint(items.get(i).point, endInter.pt)) endIdx = i;
        }

        if (startIdx == -1 || endIdx == -1) return new ArrayList<>();

        double posA = items.get(startIdx).pos;
        double posB = items.get(endIdx).pos;
        double forwardDist = posB >= posA ? posB - posA : perimeter - (posA - posB);
        double backwardDist = perimeter - forwardDist;

        List<Pt> forwardVertices = new ArrayList<>();
        int idx = startIdx;
        while (true) {
            idx = (idx + 1) % items.size();
            if (idx == endIdx) break;
            if (items.get(idx).isVertex) forwardVertices.add(items.get(idx).point);
        }

        List<Pt> backwardVertices = new ArrayList<>();
        idx = startIdx;
        while (true) {
            idx = (idx - 1 + items.size()) % items.size();
            if (idx == endIdx) break;
            if (items.get(idx).isVertex) backwardVertices.add(items.get(idx).point);
        }

        List<List<Pt>> res = new ArrayList<>();
        if (forwardDist <= backwardDist) {
            res.add(forwardVertices);
            res.add(backwardVertices);
        } else {
            res.add(backwardVertices);
            res.add(forwardVertices);
        }

        if (res.get(0).isEmpty() && res.get(1).isEmpty()) {
            Set<Pt> fallback = new HashSet<>();
            fallback.add(polygon.get(startInter.edgeIdx));
            fallback.add(polygon.get((startInter.edgeIdx + 1) % n));
            fallback.add(polygon.get(endInter.edgeIdx));
            fallback.add(polygon.get((endInter.edgeIdx + 1) % n));
            res.set(0, new ArrayList<>(fallback));
        }
        return res;
    }

    /** UPDATED: Macro-Evaluation Implementation */
    static List<Pt> computePRSPath(Pt source, Pt destination, List<List<Pt>> hulls) {
        List<Pt> path = new ArrayList<>();
        path.add(source);
        Pt current = source;
        Set<String> visited = new HashSet<>();
        visited.add(roundPoint(source).toString());

        int iter = 0;
        while (!samePoint(current, destination) && iter++ < 200) {
            if (segmentValidCached(current, destination, hulls)) {
                break;
            }

            HullAndIntersections hi = findFirstIntersectingHull(current, destination, hulls);
            if (hi == null || hi.hull == null || hi.intersections.isEmpty()) break;

            // Identify the exact entry and exit points on this specific obstacle
            hi.intersections.sort(Comparator.comparingDouble(a -> a.t));
            Pt startInter = hi.intersections.get(0).pt;
            Pt endInter = hi.intersections.get(hi.intersections.size() - 1).pt;

            List<List<Pt>> arcGroups = shorterAndLongerPathVertices(hi.hull, hi.intersections);
            if (arcGroups.isEmpty()) {
                arcGroups.add(new ArrayList<>());
            }

            List<Pt> bestMacroPath = null;
            double bestScore = Double.MAX_VALUE;

            // Macro-Evaluation: Test both full paths around the obstacle
            for (List<Pt> arc : arcGroups) {
                List<Pt> candidateArc = new ArrayList<>();
                candidateArc.add(current);
                candidateArc.add(startInter);
                candidateArc.addAll(arc);
                candidateArc.add(endInter);

                // Clean up any duplicate consecutive points
                List<Pt> cleanArc = new ArrayList<>();
                for (Pt p : candidateArc) {
                    if (cleanArc.isEmpty() || !samePoint(cleanArc.get(cleanArc.size()-1), p)) {
                        cleanArc.add(p);
                    }
                }

                // Preemptively smooth the arc. This bridges concave pockets automatically!
                List<Pt> smoothedArc = smoothPath(cleanArc, hulls);
                
                // Score based on actual traversal distance, not just a greedy straight line
                double cost = totalLength(smoothedArc) + distance(endInter, destination);

                if (cost < bestScore) {
                    bestScore = cost;
                    bestMacroPath = smoothedArc;
                }
            }

            if (bestMacroPath != null && bestMacroPath.size() > 1) {
                // Append the winning macro-path to our global route
                for (int i = 1; i < bestMacroPath.size(); i++) {
                    Pt pt = bestMacroPath.get(i);
                    String rounded = roundPoint(pt).toString();
                    if (!visited.contains(rounded)) {
                        path.add(pt);
                        visited.add(rounded);
                        current = pt;
                    }
                }
            } else {
                break; // Fallback to prevent infinite loops if math fails
            }
        }

        if (!samePoint(path.get(path.size() - 1), destination)) {
            path.add(destination);
        }
        return path;
    }

    static List<Pt> smoothPath(List<Pt> path, List<List<Pt>> hulls) {
        if (path == null || path.size() <= 2) return path;
        List<Pt> smoothed = new ArrayList<>();
        smoothed.add(path.get(0));
        int i = 0, n = path.size();
        while (i < n - 1) {
            int j = n - 1;
            boolean found = false;
            while (j > i) {
                if (segmentValidCached(smoothed.get(smoothed.size() - 1), path.get(j), hulls)) {
                    smoothed.add(path.get(j));
                    i = j;
                    found = true;
                    break;
                }
                j--;
            }
            if (!found) {
                smoothed.add(path.get(i + 1));
                i++;
            }
        }
        return smoothed;
    }

    // ════════════════════════════════════════════════════════
    // ROBUST VECTOR GEOMETRY
    // ════════════════════════════════════════════════════════
    
    static Pt getClosestPointOnSegment(Pt p, Pt a, Pt b) {
        double l2 = Math.pow(a.x - b.x, 2) + Math.pow(a.y - b.y, 2);
        if (l2 < EPS) return a;
        double t = ((p.x - a.x) * (b.x - a.x) + (p.y - a.y) * (b.y - a.y)) / l2;
        t = Math.max(0.0, Math.min(1.0, t));
        return new Pt(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y));
    }

    static boolean segmentValid(Pt p1, Pt p2, List<List<Pt>> hulls) {
        for (List<Pt> hull : hulls) {
            if (segmentCrossesPolygon(p1, p2, hull)) return false;
        }
        return true;
    }

    static boolean segmentValidCached(Pt p1, Pt p2, List<List<Pt>> hulls) {
        String key = segmentKey(p1, p2);
        if (segmentValidCache.containsKey(key)) return segmentValidCache.get(key);
        boolean valid = segmentValid(p1, p2, hulls);
        segmentValidCache.put(key, valid);
        return valid;
    }

    static boolean segmentCrossesPolygon(Pt p1, Pt p2, List<Pt> poly) {
        List<Double> ts = new ArrayList<>();
        ts.add(0.0);
        ts.add(1.0);

        int n = poly.size();
        for (int i = 0; i < n; i++) {
            Pt a = poly.get(i);
            Pt b = poly.get((i + 1) % n);

            double denom = (p1.x - p2.x) * (a.y - b.y) - (p1.y - p2.y) * (a.x - b.x);
            if (Math.abs(denom) > EPS) {
                double t = ((p1.x - a.x) * (a.y - b.y) - (p1.y - a.y) * (a.x - b.x)) / denom;
                double u = -((p1.x - p2.x) * (p1.y - a.y) - (p1.y - p2.y) * (p1.x - a.x)) / denom;
                if (t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS) {
                    ts.add(Math.max(0.0, Math.min(1.0, t)));
                }
            } else {
                if (Math.abs(cross(p1, p2, a)) <= EPS) {
                    double ta = segmentParameterUnclamped(p1, p2, a);
                    double tb = segmentParameterUnclamped(p1, p2, b);
                    if ((ta >= -EPS && ta <= 1 + EPS) || (tb >= -EPS && tb <= 1 + EPS) || (ta < 0 && tb > 1) || (tb < 0 && ta > 1)) {
                        ts.add(Math.max(0.0, Math.min(1.0, ta)));
                        ts.add(Math.max(0.0, Math.min(1.0, tb)));
                    }
                }
            }
        }

        Collections.sort(ts);

        for (int i = 0; i < ts.size() - 1; i++) {
            double t0 = ts.get(i);
            double t1 = ts.get(i + 1);
            if (t1 - t0 > 1e-4) {
                double midT = (t0 + t1) / 2.0;
                Pt midP = new Pt(p1.x + midT * (p2.x - p1.x), p1.y + midT * (p2.y - p1.y));
                if (pointStrictlyInPolygon(midP, poly)) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean pointStrictlyInPolygon(Pt pt, List<Pt> poly) {
        boolean inside = false;
        int n = poly.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            Pt pi = poly.get(i);
            Pt pj = poly.get(j);

            if (pointOnSegment(pt, pi, pj)) return false;

            if (((pi.y > pt.y) != (pj.y > pt.y)) &&
                    (pt.x < (pj.x - pi.x) * (pt.y - pi.y) / (pj.y - pi.y) + pi.x)) {
                inside = !inside;
            }
        }
        return inside;
    }

    static boolean pointOnSegment(Pt pt, Pt a, Pt b) {
        if (Math.min(a.x, b.x) - EPS <= pt.x && pt.x <= Math.max(a.x, b.x) + EPS &&
                Math.min(a.y, b.y) - EPS <= pt.y && pt.y <= Math.max(a.y, b.y) + EPS) {
            return Math.abs(cross(a, b, pt)) <= EPS;
        }
        return false;
    }

    static double segmentParameterUnclamped(Pt a, Pt b, Pt pt) {
        double dx = b.x - a.x, dy = b.y - a.y;
        if (Math.abs(dx) >= Math.abs(dy)) return Math.abs(dx) < EPS ? 0.0 : (pt.x - a.x) / dx;
        return Math.abs(dy) < EPS ? 0.0 : (pt.y - a.y) / dy;
    }

    static Pt segmentIntersectionPoint(Pt p1, Pt p2, Pt q1, Pt q2) {
        double denom = (p1.x - p2.x) * (q1.y - q2.y) - (p1.y - p2.y) * (q1.x - q2.x);
        if (Math.abs(denom) > EPS) {
            double t = ((p1.x - q1.x) * (q1.y - q2.y) - (p1.y - q1.y) * (q1.x - q2.x)) / denom;
            double u = -((p1.x - p2.x) * (p1.y - q1.y) - (p1.y - p2.y) * (p1.x - q1.x)) / denom;
            if (t >= -EPS && t <= 1 + EPS && u >= -EPS && u <= 1 + EPS) {
                return new Pt(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y));
            }
        } else {
            if (Math.abs(cross(p1, p2, q1)) <= EPS) {
                double t1 = segmentParameterUnclamped(p1, p2, q1);
                double t2 = segmentParameterUnclamped(p1, p2, q2);
                double tStart = Math.min(t1, t2);
                double tEnd = Math.max(t1, t2);
                if (tEnd >= -EPS && tStart <= 1 + EPS) {
                    double tInt = Math.max(0.0, Math.min(1.0, tStart));
                    return new Pt(p1.x + tInt * (p2.x - p1.x), p1.y + tInt * (p2.y - p1.y));
                }
            }
        }
        return null;
    }

    static Pt lineIntersect(Pt p1, Pt p2, Pt p3, Pt p4) {
        double denom = (p1.x - p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x - p4.x);
        if (Math.abs(denom) < EPS) return null;
        double px = ((p1.x * p2.y - p1.y * p2.x) * (p3.x - p4.x) - (p1.x - p2.x) * (p3.x * p4.y - p3.y * p4.x)) / denom;
        double py = ((p1.x * p2.y - p1.y * p2.x) * (p3.y - p4.y) - (p1.y - p2.y) * (p3.x * p4.y - p3.y * p4.x)) / denom;
        return new Pt(px, py);
    }

    static boolean samePoint(Pt a, Pt b) { return Math.abs(a.x - b.x) <= 1e-6 && Math.abs(a.y - b.y) <= 1e-6; }

    static Pt roundPoint(Pt pt) {
        double factor = Math.pow(10, POINT_PRECISION);
        return new Pt(Math.round(pt.x * factor) / factor, Math.round(pt.y * factor) / factor);
    }

    static String segmentKey(Pt p1, Pt p2) {
        Pt a = roundPoint(p1), b = roundPoint(p2);
        if (a.x < b.x || (a.x == b.x && a.y <= b.y)) return a.toString() + "-" + b.toString();
        return b.toString() + "-" + a.toString();
    }

    static double cross(Pt o, Pt a, Pt b) { return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x); }
    static double distance(Pt a, Pt b) { return Math.hypot(a.x - b.x, a.y - b.y); }

    static double totalLength(List<Pt> path) {
        double total = 0.0;
        for (int i = 0; i < path.size() - 1; i++) {
            total += distance(path.get(i), path.get(i + 1));
        }
        return total;
    }

    // ════════════════════════════════════════════════════════
    // GRID A* (baseline comparison algorithm)
    // ════════════════════════════════════════════════════════

    static class Node {
        int r, c; Node parent; double g, f;
        Node(int r, int c, Node p, double g, double h) { this.r = r; this.c = c; this.parent = p; this.g = g; this.f = g + h; }
    }

    static List<Pt> computeGridAStar(Pt start, Pt goal) {
        int sr = (int) start.y, sc = (int) start.x, gr = (int) goal.y, gc = (int) goal.x;
        PriorityQueue<Node> open = new PriorityQueue<>(Comparator.comparingDouble(n -> n.f));
        Map<Integer, Node> allNodes = new HashMap<>();

        Node startNode = new Node(sr, sc, null, 0, distance(start, goal));
        open.add(startNode); allNodes.put(sr * cols + sc, startNode);

        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {-1, 1}, {1, -1}, {1, 1}};

        while (!open.isEmpty()) {
            Node curr = open.poll();
            if (curr.r == gr && curr.c == gc) {
                List<Pt> path = new ArrayList<>();
                while (curr != null) { path.add(0, new Pt(curr.c + 0.5, curr.r + 0.5)); curr = curr.parent; }
                path.set(0, start); path.set(path.size() - 1, goal);
                return path;
            }
            for (int[] d : dirs) {
                int nr = curr.r + d[0], nc = curr.c + d[1];
                if (nr >= 0 && nc >= 0 && nr < rows && nc < cols && grid[nr][nc] != '@') {
                    if (Math.abs(d[0]) == 1 && Math.abs(d[1]) == 1) {
                        if (grid[curr.r][nc] == '@' || grid[nr][curr.c] == '@') continue;
                    }
                    double cost = curr.g + (Math.abs(d[0]) + Math.abs(d[1]) == 2 ? 1.414 : 1.0);
                    int key = nr * cols + nc;
                    Node existing = allNodes.get(key);
                    if (existing == null || cost < existing.g) {
                        Node next = new Node(nr, nc, curr, cost, Math.hypot(nr - gr, nc - gc));
                        allNodes.put(key, next); open.add(next);
                    }
                }
            }
        }
        return new ArrayList<>(Arrays.asList(start, goal));
    }

    static List<Pt> simplifyGridPath(List<Pt> raw) {
        if (raw.size() <= 2) return raw;
        List<Pt> res = new ArrayList<>();
        res.add(raw.get(0));
        int curr = 0;
        while (curr < raw.size() - 1) {
            int furthest = curr + 1;
            for (int i = raw.size() - 1; i > curr; i--) {
                if (isStrictLOSClear(raw.get(curr), raw.get(i))) { furthest = i; break; }
            }
            res.add(raw.get(furthest));
            curr = furthest;
        }
        return res;
    }

    static boolean isStrictLOSClear(Pt a, Pt b) {
        List<int[]> line = bresenham((int) a.y, (int) a.x, (int) b.y, (int) b.x);
        for (int[] p : line) {
            if (p[0] >= 0 && p[1] >= 0 && p[0] < rows && p[1] < cols) {
                if (grid[p[0]][p[1]] == '@') return false;
            }
        }
        return true;
    }

    static List<int[]> bresenham(int r0, int c0, int r1, int c1) {
        List<int[]> cells = new ArrayList<>();
        int dr = Math.abs(r1 - r0), sr = r0 < r1 ? 1 : -1;
        int dc = Math.abs(c1 - c0), sc = c0 < c1 ? 1 : -1;
        int err = dr - dc;
        while (true) {
            cells.add(new int[]{r0, c0});
            if (r0 == r1 && c0 == c1) break;
            int e2 = 2 * err;
            if (e2 > -dc) { err -= dc; r0 += sr; }
            if (e2 < dr) { err += dr; c0 += sc; }
        }
        return cells;
    }

    // ════════════════════════════════════════════════════════
    // MINIMAL DEPENDENCY-FREE .XLSX WRITER
    // ════════════════════════════════════════════════════════

    static void writeXlsx(File file, String sheetName, String[] headers, List<Object[]> rows) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(file))) {
            addEntry(zos, "[Content_Types].xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                    "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                    "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                    "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                    "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                    "</Types>");

            addEntry(zos, "_rels/.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                    "</Relationships>");

            addEntry(zos, "xl/workbook.xml",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                    "<sheets><sheet name=\"" + escapeXml(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>" +
                    "</workbook>");

            addEntry(zos, "xl/_rels/workbook.xml.rels",
                    "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                    "</Relationships>");

            addEntry(zos, "xl/worksheets/sheet1.xml", buildSheetXml(headers, rows));
        }
    }

    static String buildSheetXml(String[] headers, List<Object[]> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">");
        sb.append("<sheetData>");

        // Header row
        sb.append("<row r=\"1\">");
        for (int c = 0; c < headers.length; c++) {
            sb.append(stringCell(colLetter(c) + "1", headers[c]));
        }
        sb.append("</row>");

        // Data rows
        int rowNum = 2;
        for (Object[] row : rows) {
            sb.append("<row r=\"").append(rowNum).append("\">");
            for (int c = 0; c < row.length; c++) {
                String ref = colLetter(c) + rowNum;
                Object val = row[c];
                if (val instanceof Number) {
                    sb.append(numberCell(ref, ((Number) val).doubleValue()));
                } else {
                    sb.append(stringCell(ref, String.valueOf(val)));
                }
            }
            sb.append("</row>");
            rowNum++;
        }

        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    static String stringCell(String ref, String value) {
        return "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">" + escapeXml(value) + "</t></is></c>";
    }

    static String numberCell(String ref, double value) {
        String num;
        if (value == Math.rint(value) && !Double.isInfinite(value)) {
            num = String.valueOf((long) value);
        } else {
            num = String.format(Locale.ROOT, "%.6f", value);
        }
        return "<c r=\"" + ref + "\"><v>" + num + "</v></c>";
    }

    static String colLetter(int idx) {
        StringBuilder sb = new StringBuilder();
        idx++;
        while (idx > 0) {
            int rem = (idx - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            idx = (idx - 1) / 26;
        }
        return sb.toString();
    }

    static String escapeXml(String s) {
        StringBuilder sb = new StringBuilder();
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                case '\'': sb.append("&apos;"); break;
                default: sb.append(ch);
            }
        }
        return sb.toString();
    }

    static void addEntry(ZipOutputStream zos, String name, String content) throws IOException {
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(data);
        zos.closeEntry();
    }
}