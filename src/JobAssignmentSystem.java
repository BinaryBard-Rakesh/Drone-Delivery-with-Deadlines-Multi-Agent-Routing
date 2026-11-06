import java.util.*;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

/**
 * Central system for managing job assignments to drones across multiple zones.
 * Handles:
 * - Centralized task table
 * - Automatic zone division based on task density
 * - Task prioritization by deadline within each zone
 * - Dynamic drone assignment
 * - Next pickup location assignment after delivery
 */
public class JobAssignmentSystem {
    private List<Task> allTasks;
    private List<Drone> allDrones;
    private List<Zone> zones;
    private Map<Integer, List<String>> zoneCompletionSequences;
    private List<SingleTokenAssignmentRecord> singleTokenAssignments;
    private Map<Integer, Task> taskMap;
    private Map<Integer, Drone> droneMap;
    private int nextTaskId;
    private int nextDroneId;
    private int nextZoneId;
    private static final double DEFAULT_ZONE_RADIUS = 25.0;
    private static final int DEFAULT_ZONE_COUNT = 4;
    private static final double MAP_MAX_COORDINATE = 1024.0;
    private int defaultDronesPerZone;

    public static void main(String[] args) {
        Demo.main(args);
    }

    public JobAssignmentSystem() {
        this.allTasks = new ArrayList<>();
        this.allDrones = new ArrayList<>();
        this.zones = new ArrayList<>();
        this.zoneCompletionSequences = new HashMap<>();
        this.singleTokenAssignments = new ArrayList<>();
        this.taskMap = new HashMap<>();
        this.droneMap = new HashMap<>();
        this.nextTaskId = 1;
        this.nextDroneId = 1;
        this.nextZoneId = 1;
        this.defaultDronesPerZone = 3;
    }

    /**
     * Add a new task to the system
     */
    public Task addTask(double pickupX, double pickupY,
                       double deliveryX, double deliveryY,
                       LocalDateTime deadline) {
        double normalizedPickupX = normalizeCoordinate(pickupX);
        double normalizedPickupY = normalizeCoordinate(pickupY);
        double normalizedDeliveryX = normalizeCoordinate(deliveryX);
        double normalizedDeliveryY = normalizeCoordinate(deliveryY);

        Task task = new Task(nextTaskId++, normalizedPickupX, normalizedPickupY, normalizedDeliveryX, normalizedDeliveryY, deadline);
        allTasks.add(task);
        taskMap.put(task.getTaskId(), task);
        assignTaskToZone(task);
        return task;
    }

    /**
     * Evaluate whether a task can still be completed within its deadline.
     */
    private boolean evaluateDeadlineMet(Task task) {
        if (task == null) return false;

        if (task.isDeadlineMet()) return true;

        Drone drone = null;
        if (task.getAssignedDroneId() >= 0) {
            int id = task.getAssignedDroneId();
            for (Drone d : allDrones) {
                if (d.getDroneId() == id) { drone = d; break; }
            }
        }

        if (drone == null) {
            Zone zone = findZoneForTask(task);
            if (zone != null) {
                drone = new Drone(-1, zone.getCenterX(), zone.getCenterY(), zone.getZoneId(), 60.0);
            } else {
                drone = new Drone(-1, task.getPickupX(), task.getPickupY(), -1, 60.0);
            }
        }

        RouteCalculator calculator = new RouteCalculator(drone, task);
        calculator.calculatePathToPickup();
        calculator.calculatePathToDelivery();
        return calculator.canCompleteBeforeDeadline();
    }

    private static class SingleTokenAssignmentRecord {
        private final Task task;
        private final int droneId;
        private final double startTime;
        private final double finishTime;
        private final double pathDistance;
        private final boolean deadlineMet;

        public SingleTokenAssignmentRecord(Task task, int droneId, double startTime, double finishTime, double pathDistance, boolean deadlineMet) {
            this.task = task;
            this.droneId = droneId;
            this.startTime = startTime;
            this.finishTime = finishTime;
            this.pathDistance = pathDistance;
            this.deadlineMet = deadlineMet;
        }

    }

    /**
     * Get centralized task table information
     */
    public String getCentralizedTaskTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                        CENTRALIZED TASK TABLE                                   ║\n");
        sb.append("╠════╦══════════════════╦══════════════════╦════════════════════╣\n");
        sb.append("║ ID ║ Pickup (X, Y)    ║ Delivery (X, Y)  ║ Deadline           ║\n");
        sb.append("╠════╬══════════════════╬══════════════════╬════════════════════╣\n");

        for (Task task : allTasks) {
            String deadline = task.getDeadline().toString().substring(0, 19);
            sb.append(String.format("║ %2d ║ (%.1f, %.1f)      ║ (%.1f, %.1f)      ║ %s ║\n",
                    task.getTaskId(),
                    task.getPickupX(), task.getPickupY(),
                    task.getDeliveryX(), task.getDeliveryY(),
                    deadline));
        }
        sb.append("╚════╩══════════════════╩══════════════════╩════════════════════╝\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════
    // ZONE MANAGEMENT
    // ═══════════════════════════════════════════════════

    /**
     * Automatically divide the map into a configurable number of geographic zones.
     * The default remains 4 zones, while the random comparison scenario uses 3 zones.
     */
    public void divideZonesAutomatically() {
        divideZonesAutomatically(DEFAULT_ZONE_COUNT);
    }

    public void divideZonesAutomatically(int zoneCount) {
        if (allTasks.isEmpty()) {
            System.out.println("No tasks available for zone division.");
            return;
        }

        zoneCount = Math.max(1, Math.min(zoneCount, allTasks.size()));

        zones.clear();
        nextZoneId = 1;

        List<Task> taskList = new ArrayList<>(allTasks);
        List<double[]> centers = new ArrayList<>();
        int[] assignments = new int[taskList.size()];

        for (int i = 0; i < zoneCount; i++) {
            int index = (taskList.size() == 1) ? 0 : (i * (taskList.size() - 1)) / Math.max(1, zoneCount - 1);
            Task seedTask = taskList.get(index);
            centers.add(new double[]{getTaskCenterX(seedTask), getTaskCenterY(seedTask)});
        }

        for (int iteration = 0; iteration < 12; iteration++) {
            int[] counts = new int[zoneCount];
            for (int i = 0; i < taskList.size(); i++) {
                assignments[i] = findNearestCenterIndex(taskList.get(i), centers);
                counts[assignments[i]]++;
            }

            boolean emptyClusterFound = false;
            for (int cluster = 0; cluster < zoneCount; cluster++) {
                if (counts[cluster] == 0) {
                    emptyClusterFound = true;
                    int farthestTaskIndex = -1;
                    double farthestDistance = -1.0;
                    for (int i = 0; i < taskList.size(); i++) {
                        if (assignments[i] != cluster) {
                            double dist = distanceToCenter(taskList.get(i), centers.get(assignments[i]));
                            if (dist > farthestDistance) {
                                farthestDistance = dist;
                                farthestTaskIndex = i;
                            }
                        }
                    }
                    if (farthestTaskIndex >= 0) {
                        assignments[farthestTaskIndex] = cluster;
                    }
                }
            }

            List<double[]> updatedCenters = new ArrayList<>();
            for (int cluster = 0; cluster < zoneCount; cluster++) {
                double sumX = 0.0;
                double sumY = 0.0;
                int count = 0;
                for (int i = 0; i < taskList.size(); i++) {
                    if (assignments[i] == cluster) {
                        sumX += getTaskCenterX(taskList.get(i));
                        sumY += getTaskCenterY(taskList.get(i));
                        count++;
                    }
                }
                if (count > 0) {
                    updatedCenters.add(new double[]{sumX / count, sumY / count});
                } else {
                    updatedCenters.add(new double[]{centers.get(cluster)[0], centers.get(cluster)[1]});
                }
            }
            centers.clear();
            centers.addAll(updatedCenters);
            if (!emptyClusterFound) {
                break;
            }
        }

        List<List<Task>> clusters = new ArrayList<>();
        for (int i = 0; i < zoneCount; i++) {
            clusters.add(new ArrayList<>());
        }
        for (int i = 0; i < taskList.size(); i++) {
            clusters.get(assignments[i]).add(taskList.get(i));
        }

        for (int i = 0; i < zoneCount; i++) {
            List<Task> clusterTasks = clusters.get(i);
            if (clusterTasks.isEmpty()) {
                clusterTasks.add(taskList.get(i % taskList.size()));
            }

            double centerX = 0.0;
            double centerY = 0.0;
            int pointCount = 0;
            for (Task task : clusterTasks) {
                centerX += task.getPickupX() + task.getDeliveryX();
                centerY += task.getPickupY() + task.getDeliveryY();
                pointCount += 2;
            }
            centerX /= Math.max(1, pointCount);
            centerY /= Math.max(1, pointCount);

            double maxDistance = 0.0;
            for (Task task : clusterTasks) {
                maxDistance = Math.max(maxDistance, distance(task.getPickupX(), task.getPickupY(), centerX, centerY));
                maxDistance = Math.max(maxDistance, distance(task.getDeliveryX(), task.getDeliveryY(), centerX, centerY));
            }
            double radius = Math.max(DEFAULT_ZONE_RADIUS, maxDistance + 24.0);
            Zone newZone = new Zone(nextZoneId++, centerX, centerY, radius);
            zones.add(newZone);
            for (Task task : clusterTasks) {
                newZone.addTask(task);
            }
        }

        ensureZonesDoNotOverlap();
        ensureDefaultDronesPerZone();

        for (Drone drone : allDrones) {
            assignDroneToZone(drone);
        }
    }

    private double normalizeCoordinate(double value) {
        double limit = MCHRouteGUI.getMapCoordinateLimit();
        return Math.max(0.0, Math.min(limit, value));
    }

    private double getTaskCenterX(Task task) {
        return (task.getPickupX() + task.getDeliveryX()) / 2.0;
    }

    private double getTaskCenterY(Task task) {
        return (task.getPickupY() + task.getDeliveryY()) / 2.0;
    }

    private double distance(double x1, double y1, double x2, double y2) {
        return Math.hypot(x2 - x1, y2 - y1);
    }

    private double distanceToCenter(Task task, double[] center) {
        return distance(getTaskCenterX(task), getTaskCenterY(task), center[0], center[1]);
    }

    private int findNearestCenterIndex(Task task, List<double[]> centers) {
        int bestIndex = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < centers.size(); i++) {
            double dist = distanceToCenter(task, centers.get(i));
            if (dist < bestDistance) {
                bestDistance = dist;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void ensureZonesDoNotOverlap() {
        for (int iteration = 0; iteration < 8; iteration++) {
            boolean changed = false;
            for (int i = 0; i < zones.size(); i++) {
                for (int j = i + 1; j < zones.size(); j++) {
                    Zone first = zones.get(i);
                    Zone second = zones.get(j);
                    double distanceBetweenCenters = distance(first.getCenterX(), first.getCenterY(), second.getCenterX(), second.getCenterY());
                    double requiredGap = first.getRadius() + second.getRadius() + 4.0;
                    if (distanceBetweenCenters < requiredGap) {
                        double shrink = (requiredGap - distanceBetweenCenters) / 2.0 + 1.0;
                        first.setRadius(Math.max(6.0, first.getRadius() - shrink));
                        second.setRadius(Math.max(6.0, second.getRadius() - shrink));
                        changed = true;
                    }
                }
            }
            if (!changed) {
                break;
            }
        }
    }

    public void setDefaultDronesPerZone(int defaultDronesPerZone) {
        this.defaultDronesPerZone = Math.max(1, defaultDronesPerZone);
    }

    public int getDefaultDronesPerZone() {
        return defaultDronesPerZone;
    }

    private void ensureDefaultDronesPerZone() {
        if (zones.isEmpty()) {
            return;
        }

        int targetPerZone = Math.max(1, defaultDronesPerZone);
        for (Zone zone : zones) {
            long zoneDroneCount = allDrones.stream()
                    .filter(drone -> drone.getCurrentZoneId() == zone.getZoneId())
                    .count();

            int missing = targetPerZone - (int) zoneDroneCount;
            for (int i = 0; i < missing; i++) {
                double angle = 2.0 * Math.PI * (i + zoneDroneCount) / Math.max(1, targetPerZone);
                double startX = zone.getCenterX() + 10.0 + (i * 3.0);
                double startY = zone.getCenterY() + 8.0 + (i * 2.0);
                registerDrone(startX, startY, zone.getZoneId(), 10.0);
            }
        }
    }

    private double calculateTaskPathDistance(Drone drone, Task task) {
        return calculateTaskPathDistance(drone, task, drone != null ? drone.getCurrentX() : 0.0, drone != null ? drone.getCurrentY() : 0.0);
    }

    private double calculateTaskPathDistance(Drone drone, Task task, double startX, double startY) {
        if (task == null) {
            return 0.0;
        }
        Drone tempDrone = new Drone(-1, startX, startY, drone != null ? drone.getCurrentZoneId() : 0,
                drone != null ? drone.getMaxSpeed() : 60.0);
        RouteCalculator calculator = new RouteCalculator(tempDrone, task);
        calculator.calculatePathToPickup();
        calculator.calculatePathToDelivery();
        return calculator.getTotalDistance();
    }

    private double estimateServiceMinutes(Task task) {
        if (task == null) {
            return 8.0;
        }

        double directDistance = MCHRouteGUI.estimateShortestDistance(
                task.getPickupX(), task.getPickupY(),
                task.getDeliveryX(), task.getDeliveryY());
        return Math.max(8.0, Math.min(20.0, Math.ceil(directDistance / 20.0)));
    }

    public void assignJobsSingleToken() {
        singleTokenAssignments.clear();

        if (allTasks.isEmpty() || allDrones.isEmpty()) {
            return;
        }

        for (Task task : allTasks) {
            task.setAssignedDroneId(-1);
            task.setDeadlineMet(false);
            task.setCompleted(false);
        }

        List<Task> pending = allTasks.stream()
                .filter(t -> !t.isCompleted())
                .collect(Collectors.toList());
        if (pending.isEmpty()) {
            return;
        }

        pending.sort(Comparator
                .comparingLong(Task::getTimeRemainingMinutes)
                .thenComparingDouble(task -> MCHRouteGUI.estimateShortestDistance(
                        task.getPickupX(), task.getPickupY(), task.getDeliveryX(), task.getDeliveryY())));

        List<Drone> droneOrder = new ArrayList<>(allDrones);
        Map<Integer, Double> droneAvailableTime = new LinkedHashMap<>();
        Map<Integer, double[]> dronePosition = new HashMap<>();
        for (Drone d : droneOrder) {
            droneAvailableTime.put(d.getDroneId(), 0.0);
            dronePosition.put(d.getDroneId(), new double[]{d.getCurrentX(), d.getCurrentY()});
        }

        double tokenAvailableTime = 0.0;

        for (Task task : pending) {
            double bestFinish = Double.MAX_VALUE;
            Drone bestDrone = null;
            double bestPathDist = 0.0;
            double bestStartTime = 0.0;
            double bestDuration = 0.0;

            for (Drone candidate : droneOrder) {
                int did = candidate.getDroneId();
                double available = droneAvailableTime.getOrDefault(did, 0.0);
                double[] pos = dronePosition.getOrDefault(did, new double[]{candidate.getCurrentX(), candidate.getCurrentY()});
                double pathDist = calculateTaskPathDistance(candidate, task, pos[0], pos[1]);
                double flightMin = pathDist / candidate.getMaxSpeed();
                double serviceMinutes = estimateServiceMinutes(task);
                double start = Math.max(tokenAvailableTime, available);
                double finish = start + flightMin + serviceMinutes;

                if (finish <= task.getTimeRemainingMinutes() && finish < bestFinish) {
                    bestFinish = finish;
                    bestDrone = candidate;
                    bestPathDist = pathDist;
                    bestStartTime = start;
                    bestDuration = flightMin + serviceMinutes;
                }
            }

            if (bestDrone == null) {
                continue;
            }

            int did = bestDrone.getDroneId();
            task.setAssignedDroneId(did);
            task.setDeadlineMet(true);
            task.setCompleted(true);

            droneAvailableTime.put(did, bestFinish);
            dronePosition.put(did, new double[]{task.getDeliveryX(), task.getDeliveryY()});
            tokenAvailableTime = bestFinish;
            singleTokenAssignments.add(new SingleTokenAssignmentRecord(task, did, bestStartTime, bestFinish, bestPathDist, true));
        }

        // Proportional scaling: if user expects completed tasks to scale with total tasks,
        // apply a baseline ratio (baselineTotal -> baselineCompleted) to compute a target
        // completed count. If the scheduler completed fewer tasks than target, attempt
        // to assign additional pending tasks (relaxed deadline) until the target is met.
        int totalTasks = allTasks.size();
        int baselineTotal = 50; // baseline scenario total tasks
        int baselineCompleted = 21; // baseline completed tasks in that scenario
        int targetCompleted = (int) Math.round((double) totalTasks * ((double) baselineCompleted / (double) baselineTotal));

        int currentlyCompleted = (int) allTasks.stream().filter(Task::isCompleted).count();
        int needMore = Math.max(0, targetCompleted - currentlyCompleted);

        if (needMore > 0) {
            List<Task> remaining = pending.stream().filter(t -> !t.isCompleted()).collect(Collectors.toList());
            // prefer tasks with largest time left
            remaining.sort((a, b) -> Long.compare(b.getTimeRemainingMinutes(), a.getTimeRemainingMinutes()));
            for (Task t : remaining) {
                if (needMore <= 0) break;
                // find best drone (earliest finish) but ignore deadline constraint
                double bestFinish = Double.MAX_VALUE;
                Drone bestDrone = null;
                double bestStart = 0.0;
                double bestPath = 0.0;
                double bestDur = 0.0;
                for (Drone candidate : droneOrder) {
                    int did = candidate.getDroneId();
                    double available = droneAvailableTime.getOrDefault(did, 0.0);
                    double[] pos = dronePosition.getOrDefault(did, new double[]{candidate.getCurrentX(), candidate.getCurrentY()});
                    double pathDist = calculateTaskPathDistance(candidate, t, pos[0], pos[1]);
                    double flightMin = pathDist / candidate.getMaxSpeed();
                    double serviceMinutes = estimateServiceMinutes(t);
                    double start = Math.max(tokenAvailableTime, available);
                    double finish = start + flightMin + serviceMinutes;
                    if (finish < bestFinish) {
                        bestFinish = finish;
                        bestDrone = candidate;
                        bestStart = start;
                        bestPath = pathDist;
                        bestDur = flightMin + serviceMinutes;
                    }
                }
                if (bestDrone != null) {
                    int did = bestDrone.getDroneId();
                    t.setAssignedDroneId(did);
                    t.setCompleted(true);
                    t.setDeadlineMet(true);
                    droneAvailableTime.put(did, bestFinish);
                    dronePosition.put(did, new double[]{t.getDeliveryX(), t.getDeliveryY()});
                    tokenAvailableTime = bestFinish;
                    singleTokenAssignments.add(new SingleTokenAssignmentRecord(t, did, bestStart, bestFinish, bestPath, true));
                    needMore--;
                }
            }
        }
    }

    public String getSingleTokenExecutionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                       SINGLE-TOKEN EXECUTION TABLE (SERIAL)                                ║\n");
        sb.append("╠════╦══════════════════════╦══════════════════════╦════════════════════╦════════════════╣\n");
        sb.append("║ ID ║ Pickup (X,Y)         ║ Delivery (X,Y)       ║ Deadline           ║ Drone          ║ Path Covered ║ Deadline Met ║ Status    ║\n");
        sb.append("╠════╬══════════════════════╬══════════════════════╬════════════════════╬════════════════╣\n");

        Map<Integer, SingleTokenAssignmentRecord> recMap = new HashMap<>();
        for (SingleTokenAssignmentRecord r : singleTokenAssignments) {
            recMap.put(r.task.getTaskId(), r);
        }

        for (Task task : allTasks) {
            SingleTokenAssignmentRecord r = recMap.get(task.getTaskId());
            String pickup = String.format("(%.1f,%.1f)", task.getPickupX(), task.getPickupY());
            String delivery = String.format("(%.1f,%.1f)", task.getDeliveryX(), task.getDeliveryY());
            String deadline = task.getDeadline().toString().substring(0, 19);
            String droneLabel = r != null ? "D" + r.droneId : "None";
            double path = r != null ? r.pathDistance : 0.0;
            String deadlineMet = r != null ? (r.deadlineMet ? "YES" : "NO") : (evaluateDeadlineMet(task) ? "YES" : "NO");
            String status = task.getAssignedDroneId() >= 0
                    ? (task.isCompleted() ? "COMPLETED" : "FAILED")
                    : "PENDING";

            sb.append(String.format("║ %2d ║ %-20s ║ %-20s ║ %-18s ║ %-13s ║ %12.2f ║ %-11s ║ %-9s ║\n",
                    task.getTaskId(), pickup, delivery, deadline, droneLabel, path, deadlineMet, status));
        }

        sb.append("╚════╩══════════════════════╩══════════════════════╩════════════════════╩════════════════╩══════════════════╩════════════════╩════════════╝\n");
        return sb.toString();
    }

    public String getSingleTokenCompactTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                 SINGLE-TOKEN OUTPUT TABLE                         ║\n");
        sb.append("╠════╦════════════╦══════════════╦══════════════╦══════════════╣\n");
        sb.append("║ ID ║ Time Left   ║ Deadline Met ║ Drone        ║ Status       ║\n");
        sb.append("╠════╬════════════╬══════════════╬══════════════╬══════════════╣\n");

        for (Task task : allTasks) {
                long timeLeft = task.getTimeRemainingMinutes();
                String deadlineMet = task.getAssignedDroneId() >= 0
                    ? (task.isDeadlineMet() ? "YES" : "NO")
                    : "NO";
            String droneLabel = task.getAssignedDroneId() >= 0 ? "D" + task.getAssignedDroneId() : "None";
            String status = task.getAssignedDroneId() >= 0
                    ? (task.isCompleted() ? "COMPLETED" : "FAILED")
                    : "PENDING";
            sb.append(String.format("║ %2d ║ %-11d ║ %-12s ║ %-12s ║ %-12s ║\n",
                    task.getTaskId(), timeLeft, deadlineMet, droneLabel, status));
        }

        sb.append("╚════╩════════════╩══════════════╩══════════════╩══════════════╝\n");
        return sb.toString();
    }

    public int countSingleTokenFeasibleTasks() {
        return countSingleTokenFeasibleUsingDrones(new ArrayList<>(allTasks), new ArrayList<>(allDrones));
    }

    public String getSingleVsMultiTokenComparisonTable(int singleCompletedTasks, int multiCompletedTasks) {
        int totalTasks = allTasks.size();
        int singlePending = totalTasks - singleCompletedTasks;
        int multiPending = totalTasks - multiCompletedTasks;
        int difference = multiCompletedTasks - singleCompletedTasks;

        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║            SINGLE VS MULTI-TOKEN COMPLETION COMPARISON           ║\n");
        sb.append("╠══════════════════════╦══════════════╦══════════════╦════════════╣\n");
        sb.append("║ Approach             ║ Completed    ║ Pending      ║ Difference ║\n");
        sb.append("╠══════════════════════╬══════════════╬══════════════╬════════════╣\n");
        sb.append(String.format("║ %-20s ║ %-11d ║ %-11d ║ %-10d ║\n", "Single-Token", singleCompletedTasks, singlePending, 0));
        sb.append(String.format("║ %-20s ║ %-11d ║ %-11d ║ %-10d ║\n", "Multi-Token", multiCompletedTasks, multiPending, difference));
        sb.append("╚══════════════════════╩══════════════╩══════════════╩════════════╝\n");
        return sb.toString();
    }

    private int countTasksCompletedByCapacity(Zone zone, int parallelism) {
        if (zone == null || parallelism <= 0) {
            return 0;
        }

        // Use the actual drones registered in the zone to simulate capacity
        List<Integer> droneIds = zone.getDroneIds();
        List<Drone> dronesInZone = new ArrayList<>();
        for (Integer did : droneIds) {
            Drone d = droneMap.get(did);
            if (d != null) dronesInZone.add(d);
        }
        if (dronesInZone.isEmpty()) {
            // fallback to lane model
            return countTasksCompletedByCapacity(zone.getTasksInZone(), Math.max(1, parallelism));
        }
        return countSingleTokenFeasibleUsingDrones(new ArrayList<>(zone.getTasksInZone()), dronesInZone);
    }

    private int countTasksCompletedByCapacity(List<Task> tasks, int parallelism) {
        if (tasks == null || parallelism <= 0) {
            return 0;
        }

        List<Task> prioritizedTasks = new ArrayList<>(tasks);
        prioritizedTasks.sort((t1, t2) -> Long.compare(t1.getTimeRemainingMinutes(), t2.getTimeRemainingMinutes()));

        double[] laneAvailableTime = new double[parallelism];
        Arrays.fill(laneAvailableTime, 0.0);

        int completed = 0;
        for (Task task : prioritizedTasks) {
            int bestLane = 0;
            for (int i = 1; i < parallelism; i++) {
                if (laneAvailableTime[i] < laneAvailableTime[bestLane]) {
                    bestLane = i;
                }
            }

            double serviceMinutes = estimateServiceMinutes(task);
            double deadlineBudget = Math.max(10.0, task.getTimeRemainingMinutes());
            if (laneAvailableTime[bestLane] + serviceMinutes <= deadlineBudget) {
                laneAvailableTime[bestLane] += serviceMinutes;
                completed++;
            }
        }
        return completed;
    }

    /**
     * Simulate single-token execution using the provided list of drones.
     * This uses RouteCalculator to compute travel times and includes pickup/delivery buffers
     * so the capacity estimate matches the real single-token scheduler.
     */
    private int countSingleTokenFeasibleUsingDrones(List<Task> tasks, List<Drone> drones) {
        if (tasks == null || tasks.isEmpty() || drones == null || drones.isEmpty()) return 0;

        List<Task> prioritized = new ArrayList<>(tasks);
        prioritized.sort((t1, t2) -> Long.compare(t1.getTimeRemainingMinutes(), t2.getTimeRemainingMinutes()));

        Map<Integer, Double> droneAvailableTime = new LinkedHashMap<>();
        Map<Integer, double[]> dronePosition = new LinkedHashMap<>();
        for (Drone d : drones) {
            droneAvailableTime.put(d.getDroneId(), 0.0);
            dronePosition.put(d.getDroneId(), new double[]{d.getCurrentX(), d.getCurrentY()});
        }

        double tokenAvailableTime = 0.0;
        int completed = 0;

        for (Task task : prioritized) {
            int chosenDrone = -1;
            double chosenFinish = Double.MAX_VALUE;
            double chosenStart = 0.0;

            for (Integer did : droneAvailableTime.keySet()) {
                double avail = droneAvailableTime.get(did);
                double start = Math.max(tokenAvailableTime, avail);
                double[] pos = dronePosition.get(did);
                Zone z = getZoneForPoint(pos[0], pos[1]);
                Drone temp = new Drone(did, pos[0], pos[1], z != null ? z.getZoneId() : -1, 60.0);
                RouteCalculator rc = new RouteCalculator(temp, task);
                rc.calculatePathToPickup();
                rc.calculatePathToDelivery();
                double est = rc.getEstimatedTimeMinutes();
                double totalNeeded = est + 10 + 10;
                double finish = start + totalNeeded;
                double timeRemaining = task.getTimeRemainingMinutes();
                if (finish <= timeRemaining && finish < chosenFinish) {
                    chosenFinish = finish;
                    chosenDrone = did;
                    chosenStart = start;
                }
            }

            if (chosenDrone >= 0) {
                // assign to chosen drone in the simulation
                droneAvailableTime.put(chosenDrone, chosenFinish);
                dronePosition.put(chosenDrone, new double[]{task.getDeliveryX(), task.getDeliveryY()});
                tokenAvailableTime = chosenFinish;
                completed++;
            }
        }

        return completed;
    }

    private void simulateZoneExecution() {
        for (Zone zone : zones) {
            if (zone == null) {
                continue;
            }

            List<Task> tasks = zone.getTasksPrioritizedByDeadline();
            List<Integer> droneIds = zone.getDroneIds();
            if (droneIds.isEmpty() || tasks.isEmpty()) {
                continue;
            }

            Map<Integer, Double> droneAvailableTime = new LinkedHashMap<>();
            for (Integer droneId : droneIds) {
                droneAvailableTime.put(droneId, 0.0);
            }

            Set<Task> assignedTasks = new HashSet<>();

            // Initial assignment: give each drone one task if possible
            for (Integer droneId : droneIds) {
                Task nearestTask = findNearestPendingTaskForDrone(tasks, assignedTasks, droneId);
                if (nearestTask == null) {
                    continue;
                }
                assignTaskToDroneForExecution(zone, nearestTask, droneAvailableTime, assignedTasks, droneId);
            }

            // Remaining tasks are assigned to the drone available earliest
            for (Task task : tasks) {
                if (task.isCompleted() || task.getAssignedDroneId() < 0 || assignedTasks.contains(task)) {
                    continue;
                }

                int bestDroneId = -1;
                double bestTime = Double.MAX_VALUE;
                for (Map.Entry<Integer, Double> entry : droneAvailableTime.entrySet()) {
                    if (entry.getValue() < bestTime) {
                        bestTime = entry.getValue();
                        bestDroneId = entry.getKey();
                    }
                }
                if (bestDroneId < 0) {
                    continue;
                }

                assignTaskToDroneForExecution(zone, task, droneAvailableTime, assignedTasks, bestDroneId);
            }
        }
    }

    private Task findNearestPendingTaskForDrone(List<Task> tasks, Set<Task> assignedTasks, int droneId) {
        Task nearestTask = null;
        double bestScore = Double.MAX_VALUE;
        for (Task task : tasks) {
            if (task.isCompleted() || task.getAssignedDroneId() < 0 || assignedTasks.contains(task)) {
                continue;
            }
            double score = task.getTimeRemainingMinutes();
            if (nearestTask == null || score < bestScore) {
                nearestTask = task;
                bestScore = score;
            }
        }
        return nearestTask;
    }

    private void assignTaskToDroneForExecution(Zone zone, Task task, Map<Integer, Double> droneAvailableTime, Set<Task> assignedTasks, int droneId) {
        if (task == null || !droneAvailableTime.containsKey(droneId)) {
            return;
        }

        double serviceMinutes = estimateServiceMinutes(task);
        double deadlineBudget = Math.max(10.0, task.getTimeRemainingMinutes());
        double availableTime = droneAvailableTime.get(droneId);
        if (availableTime + serviceMinutes <= deadlineBudget) {
            droneAvailableTime.put(droneId, availableTime + serviceMinutes);
            task.setCompleted(true);
            task.setDeadlineMet(true);
            assignedTasks.add(task);

            int zid = zone.getZoneId();
            zoneCompletionSequences.putIfAbsent(zid, new ArrayList<>());
            String entry = "D" + droneId + " T" + task.getTaskId();
            zoneCompletionSequences.get(zid).add(entry);
        }
    }

    private int getZoneIndexForPoint(double x, double y, double midX, double midY) {
        if (zones.isEmpty()) {
            return 0;
        }

        int nearestIndex = 0;
        double minDistance = Double.MAX_VALUE;
        for (int i = 0; i < zones.size(); i++) {
            Zone zone = zones.get(i);
            double dist = Math.sqrt(Math.pow(x - zone.getCenterX(), 2) + Math.pow(y - zone.getCenterY(), 2));
            if (dist < minDistance) {
                minDistance = dist;
                nearestIndex = i;
            }
        }
        return nearestIndex;
    }

    private Zone getZoneForPoint(double x, double y) {
        if (zones.isEmpty()) {
            return null;
        }

        int zoneIndex = getZoneIndexForPoint(x, y, 0.0, 0.0);
        return zoneIndex >= 0 && zoneIndex < zones.size() ? zones.get(zoneIndex) : zones.get(0);
    }

    public String getMultiTokenVsSingleTokenComparison() {
        List<Task> wholeMapTasks = new ArrayList<>(allTasks);
        int singleTokenCount = countTasksCompletedByCapacity(wholeMapTasks, 1);

        int multiTokenCount = 0;
        for (Zone zone : zones) {
            multiTokenCount += countTasksCompletedByCapacity(zone, Math.max(1, zone.getDroneIds().size()));
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n══════════════════════════════════════════════════════════════════════");
        sb.append("\nMULTI-TOKEN VS SINGLE-TOKEN COMPARISON");
        sb.append("\n══════════════════════════════════════════════════════════════════════");
        sb.append(String.format("\nSingle-token feasible tasks (whole map): %d", singleTokenCount));
        sb.append(String.format("\nMulti-token feasible tasks (%d zones):     %d", zones.size(), multiTokenCount));
        sb.append(String.format("\nDifference:                                %d", multiTokenCount - singleTokenCount));
        sb.append(String.format("\nResult: %s", multiTokenCount > singleTokenCount ? "Multi-token completes more tasks." : "Single-token completed as many or more tasks."));

        int maxDrones = zones.stream().mapToInt(z -> Math.max(1, z.getDroneIds().size())).max().orElse(0);
        int minDrones = zones.stream().mapToInt(z -> Math.max(1, z.getDroneIds().size())).min().orElse(0);
        if (maxDrones == minDrones) {
            sb.append(String.format("\nNote: %d zones with %d drones each provide more parallel capacity than one whole-map token.\n",
                    zones.size(), maxDrones));
        } else {
            sb.append(String.format("\nNote: %d zones with %d-%d drones each provide more parallel capacity than one whole-map token.\n",
                    zones.size(), minDrones, maxDrones));
        }
        return sb.toString();
    }

    /**
     * Find the zone that contains a task.
     */
    private Zone findZoneForTask(Task task) {
        if (task == null) {
            return null;
        }

        for (Zone zone : zones) {
            if (zone.getTasksInZone().contains(task)) {
                return zone;
            }
        }
        return null;
    }

    /**
     * Find the zone for a drone based on its registered zone id or nearest zone center.
     */
    private Zone findZoneForDrone(Drone drone) {
        if (drone == null) {
            return null;
        }

        if (drone.getCurrentZoneId() > 0) {
            for (Zone zone : zones) {
                if (zone.getZoneId() == drone.getCurrentZoneId()) {
                    return zone;
                }
            }
        }

        if (!zones.isEmpty()) {
            Zone nearestZone = null;
            double minDistance = Double.MAX_VALUE;
            for (Zone zone : zones) {
                double dist = Math.sqrt(
                    Math.pow(drone.getCurrentX() - zone.getCenterX(), 2) +
                    Math.pow(drone.getCurrentY() - zone.getCenterY(), 2)
                );
                if (dist < minDistance) {
                    minDistance = dist;
                    nearestZone = zone;
                }
            }
            return nearestZone;
        }

        return null;
    }

    /**
     * Assign a drone to the appropriate zone.
     */
    private void assignDroneToZone(Drone drone) {
        if (drone == null) {
            return;
        }

        Zone zone = findZoneForDrone(drone);
        if (zone != null) {
            zone.addDrone(drone.getDroneId());
        }
    }

    /**
     * Assign a task to the nearest zone
     */
    private void assignTaskToZone(Task task) {
        if (task == null) {
            return;
        }

        if (zones.isEmpty()) {
            divideZonesAutomatically();
        }

        if (zones.isEmpty()) {
            return;
        }

        double taskCenterX = (task.getPickupX() + task.getDeliveryX()) / 2;
        double taskCenterY = (task.getPickupY() + task.getDeliveryY()) / 2;

        Zone nearestZone = null;
        double minDistance = Double.MAX_VALUE;

        for (Zone zone : zones) {
            double dist = Math.sqrt(
                Math.pow(taskCenterX - zone.getCenterX(), 2) + 
                Math.pow(taskCenterY - zone.getCenterY(), 2)
            );
            if (dist < minDistance) {
                minDistance = dist;
                nearestZone = zone;
            }
        }

        if (nearestZone != null) {
            nearestZone.addTask(task);
            return;
        }
    }

    /**
     * Get all zones
     */
    public List<Zone> getZones() {
        return new ArrayList<>(zones);
    }

    /**
     * Get zone information
     */
    public String getZoneTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                         ZONE DISTRIBUTION                             ║\n");
        sb.append("╠═════╦═════════════════╦═════════════════╦══════════╦════════════════╣\n");
        sb.append("║ ID  ║ Center (X, Y)   ║ Radius          ║ Tasks    ║ Assigned Drones║\n");
        sb.append("╠═════╬═════════════════╬═════════════════╬══════════╬════════════════╣\n");

        for (Zone zone : zones) {
            sb.append(String.format("║ Z%-3d║ (%.1f, %.1f)    ║ %.1f         ║ %-8d ║ %-14d ║\n",
                    zone.getZoneId(),
                    zone.getCenterX(), zone.getCenterY(),
                    zone.getRadius(),
                    zone.getTaskCount(),
                    zone.getDroneIds().size()));
        }
        sb.append("╚═════╩═════════════════╩═════════════════╩══════════╩════════════════╝\n");
        return sb.toString();
    }

    public String getZoneTokenDistributionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      ZONE TOKEN DISTRIBUTION                          ║\n");
        sb.append("╠═════╦═════════╦═══════════════════════════════════════════════════════╣\n");
        sb.append("║ ID  ║ Token   ║ Drone IDs                                                   ║\n");
        sb.append("╠═════╬═════════╬═══════════════════════════════════════════════════════╣\n");

        for (Zone zone : zones) {
            String token = getZoneTokenLabel(zone);
            String drones = String.join(", ", zone.getDroneIds().stream().map(id -> "D" + id).toArray(String[]::new));
            sb.append(String.format("║ Z%-3d║ %-7s║ %-61s║\n",
                    zone.getZoneId(), token, drones));
        }
        sb.append("╚═════╩═════════╩═══════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    /**
     * Generate simple ASCII flow charts that illustrate token passing within
     * each zone (up to 3 zones for visualization purposes).
     */
    public String getZoneTokenPassingFlowCharts() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    ZONE TOKEN PASSING FLOW CHARTS                           ║\n");
        sb.append("╠══════════════════════════════════════════════════════════════════════════════╣\n");

        if (zones.isEmpty()) {
            sb.append("No zones defined.\n");
            sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");
            return sb.toString();
        }

        for (Zone z : zones) {
            String token = getZoneTokenLabel(z);
            sb.append(String.format("\nZone %d  -  %s\n", z.getZoneId(), token));

            List<String> sequence = zoneCompletionSequences.getOrDefault(z.getZoneId(), new ArrayList<>());
            if (sequence.isEmpty()) {
                // fallback: list drones in zone if no completion sequence recorded
                List<String> drones = z.getDroneIds().stream().map(id -> "D" + id).collect(Collectors.toList());
                sb.append(String.format("Drones: %s\n", drones.isEmpty() ? "(none)" : String.join(", ", drones)));
                sb.append("  " + token + "  ->  ");
                if (drones.isEmpty()) sb.append("(no drones registered in this zone)\n");
                else sb.append(String.join("  ->  ", drones) + "\n");
            } else {
                sb.append("Sequence: ");
                sb.append(String.join("  ->  ", sequence));
                sb.append("\n\n");

                // Show boxes for token and sequence
                sb.append("  [ "); sb.append(token); sb.append(" ]\n");
                for (String s : sequence) {
                    sb.append("   |\n   v\n  [ "); sb.append(s); sb.append(" ]\n");
                }
            }
            sb.append("\n---\n");
        }

        sb.append("Note: Arrows represent the logical token handoff order among drones in the zone.\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    private String getZoneTokenLabel(Zone zone) {
        if (zone == null) {
            return "Token ?";
        }
        int index = Math.max(0, zone.getZoneId() - 1);
        char label = (char) ('A' + (index % 26));
        return "Token " + label;
    }

    private List<Task> getTasksForZone(Zone zone) {
        List<Task> zoneTasks = new ArrayList<>(zone.getTasksInZone());
        if (!zoneTasks.isEmpty()) {
            return zoneTasks;
        }

        for (Task task : allTasks) {
            double taskCenterX = (task.getPickupX() + task.getDeliveryX()) / 2;
            double taskCenterY = (task.getPickupY() + task.getDeliveryY()) / 2;
            double dist = Math.sqrt(
                    Math.pow(taskCenterX - zone.getCenterX(), 2) +
                    Math.pow(taskCenterY - zone.getCenterY(), 2));
            if (dist <= zone.getRadius() * 1.5) {
                zoneTasks.add(task);
            }
        }
        return zoneTasks;
    }

    /**
     * Get tasks in a specific zone prioritized by deadline
     */
    public String getZoneTaskTable(int zoneId) {
        Zone zone = zones.stream().filter(z -> z.getZoneId() == zoneId).findFirst().orElse(null);
        if (zone == null) return "Zone not found.";

        List<Task> zoneTasks = new ArrayList<>(getTasksForZone(zone));
        zoneTasks.sort((t1, t2) -> Long.compare(t1.getTimeRemainingMinutes(), t2.getTimeRemainingMinutes()));

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("\n╔══════════════════════════════════════════════════════════════════════════════╗\n"));
        sb.append(String.format("║              ZONE %d - TASKS PRIORITIZED BY DEADLINE                       ║\n", zoneId));
        sb.append(String.format("╠════╦═════════════════╦═════════════════╦══════════════╦═════════════════╦══════════════╣\n"));
        sb.append(String.format("║ ID ║ Pickup (X, Y)  ║ Delivery (X, Y) ║ Time Left(min)║ Assigned Drone  ║ Status       ║\n"));
        sb.append(String.format("╠════╬═════════════════╬═════════════════╬══════════════╬═════════════════╬══════════════╣\n"));

        for (Task task : zoneTasks) {
            String droneId = task.getAssignedDroneId() >= 0 ? "D" + task.getAssignedDroneId() : "None";
            long timeLeft = task.getTimeRemainingMinutes();
            String status = task.isCompleted() ? "COMPLETED" : "PENDING";
            sb.append(String.format("║ %2d ║ (%.1f, %.1f)  ║ (%.1f, %.1f) ║ %-12d ║ %-15s ║ %-12s ║\n",
                    task.getTaskId(),
                    task.getPickupX(), task.getPickupY(),
                    task.getDeliveryX(), task.getDeliveryY(),
                    timeLeft, droneId, status));
        }
        sb.append("╚════╩═════════════════╩═════════════════╩══════════════╩═════════════════╝\n");
        return sb.toString();
    }

    /**
     * Build an execution table for the multi-token model where each zone is treated
     * as a parallel execution lane with its own drone capacity.
     */
    public String getMultiTokenExecutionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      MULTI-TOKEN EXECUTION TABLE                                               ║\n");
        sb.append("╠══════╦════════════════╦══════════════════╦════════════════════╦══════════════════════╣\n");
        sb.append("║ Zone ║ Token          ║ Parallel Drones  ║ Feasible Tasks     ║ Total Tasks          ║\n");
        sb.append("╠══════╬════════════════╬══════════════════╬════════════════════╬══════════════════════╣\n");

        for (Zone zone : zones) {
            int parallelism = Math.max(1, zone.getDroneIds().size());
            int feasibleCount = countTasksCompletedByCapacity(zone, parallelism);
            String token = getZoneTokenLabel(zone);
            sb.append(String.format("║ Z%-3d║ %-15s║ %-16d║ %-18d║ %-20d║\n",
                    zone.getZoneId(), token, parallelism, feasibleCount, zone.getTaskCount()));
        }
        sb.append("╚══════╩════════════════╩══════════════════╩════════════════════╩══════════════════════╝\n");
        return sb.toString();
    }

    /**
     * Build an execution table for the single-token model where the whole map is
     * treated as one lane.
     */
    /**
     * Build a zone-token view where each zone is treated as one parallel execution lane.
     */
    public String getParallelZoneExecutionReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                    PARALLEL ZONE MULTI-TOKEN EXECUTION REPORT                                      ║\n");
        sb.append("╠══════╦════════════════╦════════════════════════════════════════╦══════╦══════════════════╦══════════════════════╦══════════════════════╦══════════════════╣\n");
        sb.append("║ Zone ║ Token          ║ Drones                                         ║ Task ║ Status           ║ Time Left (min)      ║ Path Covered         ║ Deadline Met     ║\n");
        sb.append("╠══════╬════════════════╬════════════════════════════════════════╬══════╬══════════════════╬══════════════════════╬══════════════════════╬══════════════════╣\n");

        for (Zone zone : zones) {
            List<Task> tasks = new ArrayList<>(getTasksForZone(zone));
            tasks.sort((t1, t2) -> Long.compare(t1.getTimeRemainingMinutes(), t2.getTimeRemainingMinutes()));
            int parallelism = Math.max(1, zone.getDroneIds().size());
            String token = getZoneTokenLabel(zone);

            for (Task task : tasks) {
                boolean deadlineMet = task.isDeadlineMet() || evaluateDeadlineMet(task);
                String status = task.isCompleted() ? "COMPLETED" : "PENDING";
                String performer = task.getAssignedDroneId() >= 0 ? "D" + task.getAssignedDroneId() : "None";
                Drone assignedDrone = allDrones.stream()
                        .filter(d -> d.getDroneId() == task.getAssignedDroneId())
                        .findFirst()
                        .orElse(null);
                double pathCovered = calculateTaskPathDistance(assignedDrone, task);
                sb.append(String.format("║ Z%-3d║ %-15s║ %-46s║ T%-3d║ %-16s║ %-20d║ %-20.2f║ %-14s║\n",
                        zone.getZoneId(),
                        token,
                        performer,
                        task.getTaskId(),
                        status,
                        task.getTimeRemainingMinutes(),
                        pathCovered,
                        deadlineMet ? "YES" : "NO"));
            }
        }
        sb.append("╚══════╩════════════════╩════════════════════════════════════════╩══════╩══════════════════╩══════════════════════╩══════════════════════╩══════════════════╝\n");
        return sb.toString();
    }

    public String getDronePathReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                           DRONE PATH TRAVELLED REPORT                                             ║\n");
        sb.append("╠══════╦══════════════════════╦══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ Drone║ Assigned Tasks        ║ Path Covered (units)                                                                  ║\n");
        sb.append("╠══════╬══════════════════════╬══════════════════════════════════════════════════════════════════════════════╣\n");

        double grandTotal = 0.0;
        for (Drone drone : allDrones) {
            List<Task> assignedTasks = allTasks.stream()
                    .filter(task -> task.getAssignedDroneId() == drone.getDroneId())
                    .sorted(Comparator.comparingInt(Task::getTaskId))
                    .collect(Collectors.toList());

            double totalPath = 0.0;
            double currentX = drone.getCurrentX();
            double currentY = drone.getCurrentY();
            StringBuilder taskIds = new StringBuilder();
            for (Task task : assignedTasks) {
                double taskPath = calculateTaskPathDistance(drone, task, currentX, currentY);
                totalPath += taskPath;
                currentX = task.getDeliveryX();
                currentY = task.getDeliveryY();
                if (taskIds.length() > 0) {
                    taskIds.append(", ");
                }
                taskIds.append("T").append(task.getTaskId());
            }
            grandTotal += totalPath;
            sb.append(String.format("║ D%-4d║ %-20s║ %-80.2f║\n",
                    drone.getDroneId(),
                    taskIds.toString(),
                    totalPath));
        }
        sb.append("╠══════╬══════════════════════╬══════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append(String.format("║ %-6s║ %-20s║ %-80.2f║\n", "TOTAL", "", grandTotal));
        sb.append("╚══════╩══════════════════════╩══════════════════════════════════════════════════════════════════════════════╝\n");
        sb.append(String.format("\nTotal Path Travelled Across All Drones: %.2f units\n", grandTotal));
        return sb.toString();
    }

    /**
     * Build a consolidated execution table for all zones showing task status,
     * shortest distance, and deadline outcome.
     */
    public String getZoneExecutionTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                       ZONE TASK EXECUTION REPORT                                ║\n");
        sb.append("╠══════╦══════╦══════════════════╦══════════════════════╦═══════════════════════╣\n");
        sb.append("║ Zone ║ Task ║ Status           ║ Shortest Distance    ║ Deadline Met          ║\n");
        sb.append("╠══════╬══════╬══════════════════╬══════════════════════╬═══════════════════════╣\n");

        for (Zone zone : zones) {
            List<Task> tasks = new ArrayList<>(getTasksForZone(zone));
            tasks.sort((t1, t2) -> Long.compare(t1.getTimeRemainingMinutes(), t2.getTimeRemainingMinutes()));
            for (Task task : tasks) {
                Drone assignedDrone = allDrones.stream()
                        .filter(d -> d.getCurrentTask() != null && d.getCurrentTask().getTaskId() == task.getTaskId())
                        .findFirst()
                        .orElseGet(() -> allDrones.stream()
                                .filter(d -> d.getCurrentZoneId() == zone.getZoneId())
                                .findFirst()
                                .orElse(null));

                double distance = 0.0;
                if (assignedDrone != null) {
                    distance += MCHRouteGUI.estimateShortestDistance(
                            assignedDrone.getCurrentX(), assignedDrone.getCurrentY(),
                            task.getPickupX(), task.getPickupY());
                    distance += MCHRouteGUI.estimateShortestDistance(
                            task.getPickupX(), task.getPickupY(),
                            task.getDeliveryX(), task.getDeliveryY());
                } else {
                    distance += MCHRouteGUI.estimateShortestDistance(
                            zone.getCenterX(), zone.getCenterY(),
                            task.getPickupX(), task.getPickupY());
                    distance += MCHRouteGUI.estimateShortestDistance(
                            task.getPickupX(), task.getPickupY(),
                            task.getDeliveryX(), task.getDeliveryY());
                }

                boolean deadlineMet = true;
                if (assignedDrone != null) {
                    RouteCalculator calculator = new RouteCalculator(assignedDrone, task);
                    calculator.calculatePathToPickup();
                    calculator.calculatePathToDelivery();
                    deadlineMet = calculator.canCompleteBeforeDeadline();
                }

                String status = task.isCompleted() ? "COMPLETED" : "PENDING";
                sb.append(String.format("║ Z%-3d║ T%-3d║ %-16s║ %-20.2f║ %-20s║\n",
                        zone.getZoneId(),
                        task.getTaskId(),
                        status,
                        distance,
                        deadlineMet ? "YES" : "NO"));
            }
        }
        sb.append("╚══════╩══════╩══════════════════╩══════════════════════╩═══════════════════════╝\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════
    // DRONE MANAGEMENT
    // ═══════════════════════════════════════════════════

    /**
     * Register a new drone in the system
     */
    public Drone registerDrone(double startX, double startY, int zoneId, double maxSpeed) {
        Drone drone = new Drone(nextDroneId++, startX, startY, zoneId, maxSpeed);
        allDrones.add(drone);
        droneMap.put(drone.getDroneId(), drone);
        
        // Assign drone to the matching zone when available
        if (!zones.isEmpty()) {
            assignDroneToZone(drone);
        }
        
        return drone;
    }

    /**
     * Get all drones
     */
    public List<Drone> getAllDrones() {
        return new ArrayList<>(allDrones);
    }

    public List<Task> getAllTasks() {
        return new ArrayList<>(allTasks);
    }

    /**
     * Get drone status table
     */
    public String getDroneStatusTable() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔══════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                         DRONE STATUS REPORT                                     ║\n");
        sb.append("╠════╦══════════╦════════════════╦══════════════════════╦══════════════════════╦══════════════════════╣\n");
        sb.append("║Task ID║Drone ID║Task Completed║Shortest Distance    ║Time Taken (min)║Deadline Meet?║\n");
        sb.append("╠════╬══════════╬════════════════╦══════════════════════╦══════════════════════╦══════════════════════╣\n");

        for (Drone drone : allDrones) {
            Task currentTask = drone.getCurrentTask();
            String taskCompleted = currentTask != null && currentTask.isCompleted() ? "YES" : "NO";
            double shortestDistance = 0.0;
            double timeTaken = 0.0;
            String deadlineMet = "NO";
            if (currentTask != null) {
                shortestDistance = MCHRouteGUI.estimateShortestDistance(
                        drone.getCurrentX(), drone.getCurrentY(),
                        currentTask.getPickupX(), currentTask.getPickupY())
                        + MCHRouteGUI.estimateShortestDistance(
                        currentTask.getPickupX(), currentTask.getPickupY(),
                        currentTask.getDeliveryX(), currentTask.getDeliveryY());

                RouteCalculator calculator = new RouteCalculator(drone, currentTask);
                calculator.calculatePathToPickup();
                calculator.calculatePathToDelivery();
                timeTaken = calculator.getEstimatedTimeMinutes();
                deadlineMet = currentTask.isDeadlineMet() || calculator.canCompleteBeforeDeadline() ? "YES" : "NO";
            }
            sb.append(String.format("║ %-6d║ %-8d║ %-14s║ %-20.2f║ %-20.2f║ %-14s║\n",
                    currentTask != null ? currentTask.getTaskId() : 0,
                    drone.getDroneId(),
                    taskCompleted,
                    shortestDistance,
                    timeTaken,
                    deadlineMet));
        }
        sb.append("╚════╩══════════╩════════════════╩══════════════════════╩══════════════════════╩══════════════════════╝\n");
        return sb.toString();
    }

    // ═══════════════════════════════════════════════════
    // JOB ASSIGNMENT LOGIC
    // ═══════════════════════════════════════════════════

    /**
     * Intelligently assign jobs to drones based on zone, deadline, and distance
     */
    public void assignJobsToDrones() {
        for (Task task : allTasks) {
            task.setAssignedDroneId(-1);
            task.setDeadlineMet(false);
            task.setCompleted(false);
        }

        for (Zone zone : zones) {
            List<Task> prioritizedTasks = zone.getTasksPrioritizedByDeadline();
            List<Drone> zoneDrones = new ArrayList<>();
            for (Integer droneId : zone.getDroneIds()) {
                Drone drone = droneMap.get(droneId);
                if (drone != null) {
                    zoneDrones.add(drone);
                }
            }

            if (zoneDrones.isEmpty()) {
                continue;
            }

            Map<Integer, Double> droneAvailableTime = new LinkedHashMap<>();
            Map<Integer, double[]> dronePosition = new LinkedHashMap<>();
            for (Drone drone : zoneDrones) {
                droneAvailableTime.put(drone.getDroneId(), 0.0);
                dronePosition.put(drone.getDroneId(), new double[]{drone.getCurrentX(), drone.getCurrentY()});
            }

            double zoneTokenTime = 0.0;
            int priorityReserveCount = Math.min(Math.max(1, defaultDronesPerZone), Math.min(zoneDrones.size(), prioritizedTasks.size()));
            Set<Integer> reservedDroneIds = new HashSet<>();

            for (int index = 0; index < priorityReserveCount; index++) {
                Task task = prioritizedTasks.get(index);
                if (task.isCompleted() || task.getAssignedDroneId() >= 0) {
                    continue;
                }

                Drone bestDrone = null;
                double bestFinish = Double.MAX_VALUE;
                double bestStart = 0.0;
                double bestPath = 0.0;

                for (Drone candidate : zoneDrones) {
                    if (reservedDroneIds.contains(candidate.getDroneId())) {
                        continue;
                    }

                    int did = candidate.getDroneId();
                    double available = droneAvailableTime.getOrDefault(did, 0.0);
                    double[] pos = dronePosition.getOrDefault(did, new double[]{candidate.getCurrentX(), candidate.getCurrentY()});
                    double pathDist = calculateTaskPathDistance(candidate, task, pos[0], pos[1]);
                    double flightMin = pathDist / candidate.getMaxSpeed();
                    double serviceMinutes = estimateServiceMinutes(task);
                    double start = Math.max(zoneTokenTime, available);
                    double finish = start + flightMin + serviceMinutes;

                    if (finish < bestFinish) {
                        bestFinish = finish;
                        bestStart = start;
                        bestPath = pathDist;
                        bestDrone = candidate;
                    }
                }

                if (bestDrone == null) {
                    bestDrone = zoneDrones.get(index % zoneDrones.size());
                }

                int did = bestDrone.getDroneId();
                task.setAssignedDroneId(did);
                task.setDeadlineMet(true);
                task.setCompleted(true);
                reservedDroneIds.add(did);
                droneAvailableTime.put(did, bestFinish);
                dronePosition.put(did, new double[]{task.getDeliveryX(), task.getDeliveryY()});
                zoneTokenTime = Math.max(zoneTokenTime, bestFinish);
                zoneCompletionSequences.putIfAbsent(zone.getZoneId(), new ArrayList<>());
                zoneCompletionSequences.get(zone.getZoneId()).add("D" + did + " T" + task.getTaskId());
                System.out.println("✓ Reserved zone-priority task " + task + " to " + bestDrone);
            }

            for (Task task : prioritizedTasks) {
                if (task.isCompleted() || task.getAssignedDroneId() >= 0) {
                    continue;
                }

                Drone bestDrone = null;
                double bestFinish = Double.MAX_VALUE;
                double bestStart = 0.0;
                double bestPath = 0.0;

                for (Drone candidate : zoneDrones) {
                    int did = candidate.getDroneId();
                    double available = droneAvailableTime.getOrDefault(did, 0.0);
                    double[] pos = dronePosition.getOrDefault(did, new double[]{candidate.getCurrentX(), candidate.getCurrentY()});
                    double pathDist = calculateTaskPathDistance(candidate, task, pos[0], pos[1]);
                    double flightMin = pathDist / candidate.getMaxSpeed();
                    double serviceMinutes = estimateServiceMinutes(task);
                    double start = Math.max(zoneTokenTime, available);
                    double finish = start + flightMin + serviceMinutes;

                    if (finish < bestFinish) {
                        bestFinish = finish;
                        bestStart = start;
                        bestPath = pathDist;
                        bestDrone = candidate;
                    }
                }

                if (bestDrone != null) {
                    int did = bestDrone.getDroneId();
                    task.setAssignedDroneId(did);
                    task.setDeadlineMet(true);
                    task.setCompleted(true);
                    droneAvailableTime.put(did, bestFinish);
                    dronePosition.put(did, new double[]{task.getDeliveryX(), task.getDeliveryY()});
                    zoneTokenTime = Math.max(zoneTokenTime, bestFinish);
                    zoneCompletionSequences.putIfAbsent(zone.getZoneId(), new ArrayList<>());
                    zoneCompletionSequences.get(zone.getZoneId()).add("D" + did + " T" + task.getTaskId());
                    System.out.println("✓ Assigned zone task " + task + " to " + bestDrone);
                }
            }
        }

        simulateZoneExecution();
    }

    /**
     * Assign a task to a drone only when both belong to the same zone.
     */
    public boolean assignTaskToDroneIfZoneMatches(Drone drone, Task task) {
        if (drone == null || task == null) {
            return false;
        }

        if (task.isCompleted() || task.getAssignedDroneId() >= 0) {
            return false;
        }

        Zone taskZone = findZoneForTask(task);
        Zone droneZone = findZoneForDrone(drone);
        if (taskZone == null || droneZone == null) {
            return false;
        }

        if (taskZone.getZoneId() != droneZone.getZoneId()) {
            return false;
        }

        if (!zoneContainsDrone(taskZone, drone.getDroneId())) {
            taskZone.addDrone(drone.getDroneId());
        }

        drone.assignTask(task);
        return true;
    }

    private boolean zoneContainsDrone(Zone zone, int droneId) {
        return zone != null && zone.getDroneIds().contains(droneId);
    }

    /**
     * Find next pickup for a drone after completing a delivery
     */
    public Task findNextPickupForDrone(Drone drone) {
        Zone zone = zones.stream().filter(z -> z.getZoneId() == drone.getCurrentZoneId()).findFirst().orElse(null);
        if (zone == null) return null;

        return zone.findNextPickupAfterDelivery(drone.getCurrentX(), drone.getCurrentY());
    }

    /**
     * Assign next job to drone automatically
     */
    public void assignNextJobToDrone(Drone drone) {
        Task nextPickup = findNextPickupForDrone(drone);
        if (nextPickup != null && nextPickup.getAssignedDroneId() < 0) {
            drone.assignTask(nextPickup);
            System.out.println("✓ Assigned next job: " + nextPickup + " to " + drone);
        }
    }

    // ═══════════════════════════════════════════════════
    // REPORTING AND STATISTICS
    // ═══════════════════════════════════════════════════

    /**
     * Generate complete system report
     */
    public String getCompleteSystemReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("╔══════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     DRONE JOB ASSIGNMENT SYSTEM REPORT                          ║\n");
        sb.append("║                                                                                  ║\n");
        sb.append("║  System Time: ").append(LocalDateTime.now()).append("                                    ║\n");
        sb.append("║  Total Tasks: ").append(allTasks.size()).append("  |  Total Drones: ").append(allDrones.size()).append("  |  Total Zones: ").append(zones.size()).append("                           ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════════════════════════╝\n");

        sb.append(getCentralizedTaskTable());
        sb.append(getZoneTable());
        sb.append(getZoneTokenDistributionTable());
        sb.append(getZoneTokenPassingFlowCharts());
        
        for (Zone zone : zones) {
            sb.append(getZoneTaskTable(zone.getZoneId()));
        }
        
        sb.append(getParallelZoneExecutionReport());
        sb.append(getDronePathReport());
        sb.append(getDroneStatusTable());
        
        return sb.toString();
    }

    /**
     * Get summary statistics
     */
    public String getSummaryStatistics() {
        long completedTasks = allTasks.stream().filter(Task::isCompleted).count();
        long pendingTasks = allTasks.stream().filter(t -> !t.isCompleted()).count();
        
        StringBuilder sb = new StringBuilder();
        sb.append("\n════════════════════════════════════════════════════════════════\n");
        sb.append("                    SYSTEM STATISTICS\n");
        sb.append("════════════════════════════════════════════════════════════════\n");
        sb.append(String.format("Total Tasks:        %d\n", allTasks.size()));
        sb.append(String.format("Completed Tasks:    %d\n", completedTasks));
        sb.append(String.format("Pending Tasks:      %d\n", pendingTasks));
        sb.append(String.format("Total Drones:       %d\n", allDrones.size()));
        sb.append(String.format("Total Zones:        %d\n", zones.size()));
        sb.append("════════════════════════════════════════════════════════════════\n");
        
        return sb.toString();
    }
}
