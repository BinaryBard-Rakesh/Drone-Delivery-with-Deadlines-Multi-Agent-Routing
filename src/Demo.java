import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Random;
import java.util.Scanner;

/**
 * Demo class showing complete system usage and workflow.
 * Run this to see the system in action with sample data.
 */
public class Demo {
    private static final String DEMO_INPUT_FILE = "demo_input.txt";
    private static final int FIXED_RANDOM_SEED = 42;

    public static void main(String[] args) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║     DRONE JOB ASSIGNMENT & ROUTING SYSTEM - COMPREHENSIVE DEMO               ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝\n");

        JobAssignmentSystem system = new JobAssignmentSystem();
        SystemIntegration integration = new SystemIntegration(system);

        if (args != null && args.length > 0 && "--sample".equalsIgnoreCase(args[0])) {
            runSampleScenario(system, integration, args);
            return;
        }

        if (args != null && args.length > 0 && "--bulk".equalsIgnoreCase(args[0])) {
            runBulkScenario(system, integration);
            return;
        }

        runRandom50ComparisonScenario(system, integration, args);
        return;
    }

    private static void runSampleScenario(JobAssignmentSystem system, SystemIntegration integration, String[] args) {
        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 1: Creating Sample Tasks (Centralized Task Table)");
        System.out.println("═".repeat(80));

        Task[] tasks = new Task[8];
        int zoneCount = parseZoneCount(args, 4);

        if (!loadTasksFromMCHRoute(system)) {
            Random random = new Random(7);
            for (int i = 0; i < tasks.length; i++) {
                double pickupX = random.nextDouble() * 1024.0;
                double pickupY = random.nextDouble() * 1024.0;
                double deliveryX = random.nextDouble() * 1024.0;
                double deliveryY = random.nextDouble() * 1024.0;
                // sample scenario: larger deadlines 30-90 minutes
                LocalDateTime deadline = LocalDateTime.now().plusMinutes(30 + random.nextInt(60));
                tasks[i] = system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
            }
            System.out.println("\n✓ Generated 8 random tasks across the map");
        }

        System.out.println(system.getCentralizedTaskTable());

        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 2: Registering Drones");
        System.out.println("═".repeat(80));

        system.registerDrone(5, 15, 1, 10);
        system.registerDrone(20, 30, 1, 10);
        system.registerDrone(18, 24, 1, 10);
        system.registerDrone(95, 115, 2, 10);
        system.registerDrone(120, 140, 2, 10);
        system.registerDrone(108, 128, 2, 10);

        system.divideZonesAutomatically(zoneCount);
        system.assignJobsToDrones();

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            System.out.println("\n→ MCHRouteGUI will remain hidden in the background by default.");
            MCHRouteGUI.showRoutePreview(10, 10, 60, 60);
            String requestedTaskId = null;
            if (args != null) {
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    if ("--task".equalsIgnoreCase(arg) && i + 1 < args.length) {
                        requestedTaskId = args[i + 1];
                        break;
                    }
                    if (arg.matches("\\d+")) {
                        requestedTaskId = arg;
                        break;
                    }
                }
            }

            if (requestedTaskId != null) {
                try {
                    int taskId = Integer.parseInt(requestedTaskId.trim());
                    for (Task task : tasks) {
                        if (task != null && task.getTaskId() == taskId) {
                            MCHRouteGUI.showTaskPreview(task);
                            break;
                        }
                    }
                } catch (NumberFormatException ex) {
                    System.out.println("Invalid task ID provided. Skipping map preview.");
                }
            }
        } else {
            System.out.println("GUI preview skipped because no display is available.");
        }

        System.out.println(system.getCompleteSystemReport());
        System.out.println(system.getSummaryStatistics());
        System.out.println(integration.getIntegrationInfo());
    }

    public static JobAssignmentSystem createFullScenario(int taskCount, int zoneCount, int dronesPerZone, double droneSpeedUnitsPerMinute) {
        JobAssignmentSystem system = new JobAssignmentSystem();
        int effectiveTaskCount = Math.max(1, taskCount);
        int effectiveZoneCount = Math.max(1, zoneCount);
        int effectiveDronesPerZone = Math.max(1, dronesPerZone);

        Random random = new Random(FIXED_RANDOM_SEED);
        double mapSize = MCHRouteGUI.getMapCoordinateLimit();
        for (int i = 0; i < effectiveTaskCount; i++) {
            double pickupX = random.nextDouble() * mapSize;
            double pickupY = random.nextDouble() * mapSize;
            double deliveryX = random.nextDouble() * mapSize;
            double deliveryY = random.nextDouble() * mapSize;
            LocalDateTime deadline = LocalDateTime.now().plusMinutes(60 + random.nextInt(120));
            system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
        }

        system.setDefaultDronesPerZone(effectiveDronesPerZone);
        system.divideZonesAutomatically(effectiveZoneCount);
        for (Zone zone : system.getZones()) {
            long alreadyRegistered = system.getAllDrones().stream()
                    .filter(drone -> drone.getCurrentZoneId() == zone.getZoneId())
                    .count();
            double centerX = zone.getCenterX();
            double centerY = zone.getCenterY();
            for (int i = (int) alreadyRegistered; i < effectiveDronesPerZone; i++) {
                double angle = 2 * Math.PI * i / Math.max(1, effectiveDronesPerZone);
                double startX = centerX + 10.0 * Math.cos(angle);
                double startY = centerY + 10.0 * Math.sin(angle);
                system.registerDrone(startX, startY, zone.getZoneId(), Math.max(10.0, droneSpeedUnitsPerMinute));
            }
        }

        return system;
    }

    public static JobAssignmentSystem createComparisonScenario(String[] args, int taskCount, int zoneCount, int dronesPerZone, double droneSpeedUnitsPerMinute) {
        JobAssignmentSystem system = createFullScenario(taskCount, zoneCount, dronesPerZone, droneSpeedUnitsPerMinute);
        writeScenarioToDemoInputFile(system, zoneCount, dronesPerZone, droneSpeedUnitsPerMinute);
        return system;
    }

    public static void writeScenarioToDemoInputFile(JobAssignmentSystem system, int zoneCount, int dronesPerZone, double droneSpeedUnitsPerMinute) {
        if (system == null) {
            return;
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(DEMO_INPUT_FILE))) {
            writer.write("taskCount=" + system.getAllTasks().size() + System.lineSeparator());
            writer.write("zoneCount=" + zoneCount + System.lineSeparator());
            writer.write("dronesPerZone=" + dronesPerZone + System.lineSeparator());
            writer.write("totalDrones=" + (zoneCount * dronesPerZone) + System.lineSeparator());
            writer.write("droneSpeedUnitsPerMinute=" + droneSpeedUnitsPerMinute + System.lineSeparator());
            for (Task task : system.getAllTasks()) {
                writer.write(String.format("%.6f %.6f %.6f %.6f %s%n",
                        task.getPickupX(),
                        task.getPickupY(),
                        task.getDeliveryX(),
                        task.getDeliveryY(),
                        task.getDeadline().toString()));
            }
        } catch (IOException ex) {
            System.out.println("Unable to write demo input file: " + ex.getMessage());
        }
    }

    public static class SavedDemoScenario {
        public final JobAssignmentSystem system;
        public final int zoneCount;
        public final int dronesPerZone;
        public final int totalDrones;
        public final double droneSpeedUnitsPerMinute;

        public SavedDemoScenario(JobAssignmentSystem system, int zoneCount, int dronesPerZone, int totalDrones, double droneSpeedUnitsPerMinute) {
            this.system = system;
            this.zoneCount = zoneCount;
            this.dronesPerZone = dronesPerZone;
            this.totalDrones = totalDrones;
            this.droneSpeedUnitsPerMinute = droneSpeedUnitsPerMinute;
        }
    }

    public static SavedDemoScenario loadScenarioFromDemoInputFile() {
        File file = new File(DEMO_INPUT_FILE);
        if (!file.exists() || !file.isFile()) {
            return null;
        }

        JobAssignmentSystem system = new JobAssignmentSystem();
        int zoneCount = 3;
        int dronesPerZone = 3;
        int totalDrones = zoneCount * dronesPerZone;
        double droneSpeedUnitsPerMinute = 10.0;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("taskCount=")) {
                    continue;
                }
                if (line.startsWith("zoneCount=")) {
                    zoneCount = Integer.parseInt(line.substring("zoneCount=".length()));
                    continue;
                }
                if (line.startsWith("dronesPerZone=")) {
                    dronesPerZone = Integer.parseInt(line.substring("dronesPerZone=".length()));
                    continue;
                }
                if (line.startsWith("totalDrones=")) {
                    totalDrones = Integer.parseInt(line.substring("totalDrones=".length()));
                    continue;
                }
                if (line.startsWith("droneSpeedUnitsPerMinute=")) {
                    droneSpeedUnitsPerMinute = Double.parseDouble(line.substring("droneSpeedUnitsPerMinute=".length()));
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length >= 5) {
                    double pickupX = Double.parseDouble(parts[0]);
                    double pickupY = Double.parseDouble(parts[1]);
                    double deliveryX = Double.parseDouble(parts[2]);
                    double deliveryY = Double.parseDouble(parts[3]);
                    LocalDateTime deadline = LocalDateTime.parse(parts[4]);
                    system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
                }
            }
        } catch (IOException | NumberFormatException ex) {
            System.out.println("Unable to read demo input file: " + ex.getMessage());
            return null;
        }

        if (system.getAllTasks().isEmpty()) {
            return null;
        }
        return new SavedDemoScenario(system, zoneCount, dronesPerZone, totalDrones, droneSpeedUnitsPerMinute);
    }

    private static void runRandom50ComparisonScenario(JobAssignmentSystem system, SystemIntegration integration, String[] args) {
        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 1: Generating 50 random tasks for the multi-zone comparison scenario");
        System.out.println("═".repeat(80));

        boolean autoMode = containsArg(args, "--auto");
        Scanner scanner = autoMode ? null : new Scanner(System.in);
        int taskCount = autoMode
                ? 50
                : getIntInput(scanner, "Enter number of tasks for this scenario [default 50]: ", 50, 1, 500);
        int zoneCount = autoMode
                ? 3
                : getIntInput(scanner, "Enter number of zones for this scenario [default 3]: ", 3, 1, 6);
        int dronesPerZone = autoMode
                ? 3
                : getIntInput(scanner, "Enter number of drones per zone [default 3]: ", 3, 1, 6);

        JobAssignmentSystem preparedSystem = createComparisonScenario(args, taskCount, zoneCount, dronesPerZone, 10.0);
        if (preparedSystem != null) {
            system = preparedSystem;
        }

        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 2: Dividing tasks into " + zoneCount + " zones and registering " + (zoneCount * dronesPerZone) + " drones");
        System.out.println("═".repeat(80));

        System.out.println("✓ Registered " + (zoneCount * dronesPerZone) + " drones across " + zoneCount + " zones.");
        system.assignJobsToDrones();

        System.out.println("\nSTEP 1: Centralized Task Table");
        System.out.println(system.getCentralizedTaskTable());

        System.out.println("\nSTEP 2: Zonal Task Distribution Tables");
        System.out.println(system.getZoneTable());
        System.out.println(system.getZoneTokenDistributionTable());
        for (Zone zone : system.getZones()) {
            System.out.println(system.getZoneTaskTable(zone.getZoneId()));
        }

        System.out.println("\nSTEP 3: Token Passing Flow Charts for All Zones");
        System.out.println(system.getZoneTokenPassingFlowCharts());

        System.out.println("\nSTEP 4: Multi-Token Execution Table");
        System.out.println(system.getMultiTokenExecutionTable());

        System.out.println("\nSTEP 5: Parallel Zone Multi-Token Execution Report");
        System.out.println(system.getParallelZoneExecutionReport());

        System.out.println("\nSTEP 6: Drone Path Travelled Report");
        System.out.println(system.getDronePathReport());

        System.out.println("\nSTEP 7: System Statistics");
        System.out.println(system.getSummaryStatistics());
        System.out.println("\nRandom 50-task comparison scenario completed successfully.");
    }

    private static void runBulkScenario(JobAssignmentSystem system, SystemIntegration integration) {
        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 1: Loading 100 tasks from the provided bulk input");
        System.out.println("═".repeat(80));

        loadBulkTasks(system);
        System.out.println("✓ Loaded 100 tasks into the centralized task table.");

        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 2: Registering 12 drones for the bulk scenario");
        System.out.println("═".repeat(80));

        registerBulkDrones(system);
        System.out.println("✓ Registered 12 drones.");

        System.out.println("\n" + "═".repeat(80));
        System.out.println("STEP 3: Dividing zones and assigning jobs");
        System.out.println("═".repeat(80));

        system.divideZonesAutomatically();
        system.assignJobsToDrones();

        System.out.println("\n" + system.getZoneTable());
        System.out.println("\n" + system.getMultiTokenExecutionTable());
        System.out.println(system.getParallelZoneExecutionReport());
        System.out.println(system.getMultiTokenVsSingleTokenComparison());
        System.out.println(system.getDroneStatusTable());
        System.out.println(system.getSummaryStatistics());
        System.out.println("\n" + integration.getIntegrationInfo());
        System.out.println("\nBulk scenario completed successfully with 100 tasks and 12 drones.");
    }

    private static int parseZoneCount(String[] args, int defaultValue) {
        if (args == null) {
            return defaultValue;
        }
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg.startsWith("--zones=")) {
                try {
                    return Integer.parseInt(arg.substring("--zones=".length()));
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
            if ("--zones".equalsIgnoreCase(arg) && i + 1 < args.length) {
                try {
                    return Integer.parseInt(args[i + 1]);
                } catch (NumberFormatException ignored) {
                    return defaultValue;
                }
            }
        }
        return defaultValue;
    }

    private static boolean loadTasksFromMCHRoute(JobAssignmentSystem system) {
        List<Task> routeTasks = MCHRouteGUI.importTasksFromMCHRoute();
        if (routeTasks == null || routeTasks.isEmpty()) {
            return false;
        }

        for (Task routeTask : routeTasks) {
            if (routeTask == null) {
                continue;
            }
            system.addTask(
                    routeTask.getPickupX(),
                    routeTask.getPickupY(),
                    routeTask.getDeliveryX(),
                    routeTask.getDeliveryY(),
                    routeTask.getDeadline()
            );
        }

        System.out.println("✓ Loaded " + routeTasks.size() + " tasks from MCHRoute pickup/delivery input.");
        return true;
    }

    private static boolean containsArg(String[] args, String target) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (target.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static int getIntInput(Scanner scanner, String prompt, int defaultValue, int minValue, int maxValue) {
        System.out.print(prompt);
        String input = scanner.nextLine().trim();
        if (input.isEmpty()) {
            return defaultValue;
        }
        try {
            int result = Integer.parseInt(input);
            if (result < minValue) {
                return minValue;
            }
            if (result > maxValue) {
                return maxValue;
            }
            return result;
        } catch (NumberFormatException e) {
            System.out.println("Invalid number; using default value: " + defaultValue);
            return defaultValue;
        }
    }

    private static void loadBulkTasks(JobAssignmentSystem system) {
        double[][] pickups = {
                {145.5, 136.6}, {18.3, 90.3}, {156.6, 183.9}, {159.7, 30.1}, {34.4, 117.5}, {18.1, 159.7}, {83.5, 194.8}, {43.9, 119.2}, {72.1, 86.9}, {109.8, 7.5},
                {56.1, 39.2}, {109.6, 1.6}, {114.3, 74.2}, {76.8, 23.0}, {161.8, 125.6}, {159.2, 138.8}, {7.3, 96.8}, {27.2, 163.5}, {119.8, 195.2}, {195.7, 36.2},
                {63.0, 88.5}, {182.5, 58.0}, {109.7, 56.4}, {145.9, 1.8}, {116.7, 137.6}, {15.2, 177.7}, {166.7, 52.0}, {87.0, 188.9}, {97.6, 72.3}, {101.0, 18.7},
                {75.2, 172.6}, {103.9, 86.6}, {197.0, 25.8}, {119.5, 149.2}, {77.6, 3.0}, {91.6, 37.0}, {84.6, 10.5}, {0.8, 56.3}, {170.2, 156.9}, {3.0, 58.6},
                {86.9, 109.8}, {91.4, 21.5}, {168.0, 19.4}, {128.1, 199.5}, {103.5, 14.9}, {128.6, 93.0}, {153.4, 105.1}, {164.0, 128.4}, {141.0, 60.7}, {10.7, 125.9},
                {78.6, 90.9}, {87.5, 170.6}, {49.5, 126.5}, {98.2, 145.4}, {128.7, 188.9}, {144.8, 52.7}, {145.1, 135.1}, {145.1, 146.4}, {84.4, 192.7}, {83.7, 35.1},
                {120.2, 186.6}, {139.2, 177.3}, {20.1, 183.3}, {47.4, 50.3}, {122.2, 184.1}, {29.4, 48.7}, {145.5, 5.8}, {46.0, 76.3}, {2.4, 31.0}, {58.9, 125.9},
                {28.9, 90.7}, {182.3, 41.7}, {4.7, 91.4}, {146.1, 185.1}, {141.6, 141.1}, {46.1, 163.8}, {109.0, 195.4}, {112.2, 81.7}, {129.9, 63.8}, {30.3, 186.9},
                {131.3, 39.4}, {119.9, 45.6}, {160.8, 85.9}, {65.5, 116.0}, {97.0, 66.6}, {38.0, 25.2}, {118.5, 55.2}, {126.7, 192.7}, {166.2, 22.9}, {194.3, 161.6},
                {48.4, 124.3}, {15.6, 169.9}, {145.1, 164.6}, {28.9, 26.0}, {184.1, 105.6}, {53.4, 92.3}, {135.2, 161.9}, {156.9, 86.4}, {63.0, 68.1}, {95.6, 61.6}
        };

        double[][] deliveries = {
                {177.9, 167.7}, {53.5, 137.9}, {194.0, 233.9}, {193.2, 60.1}, {84.5, 160.3}, {50.8, 187.7}, {132.1, 234.0}, {71.5, 154.3}, {110.3, 125.8}, {131.9, 59.0},
                {83.3, 93.8}, {159.9, 46.9}, {169.2, 126.4}, {102.7, 54.6}, {200.4, 157.8}, {182.9, 190.4}, {53.5, 132.6}, {86.3, 211.6}, {142.9, 246.2}, {252.5, 67.3},
                {86.9, 138.4}, {240.1, 89.9}, {168.4, 112.6}, {166.6, 33.9}, {158.9, 162.3}, {61.7, 226.0}, {225.2, 87.9}, {142.6, 214.0}, {147.2, 126.5}, {141.4, 73.0},
                {123.1, 199.0}, {154.5, 132.3}, {241.6, 67.5}, {169.7, 207.9}, {101.7, 27.2}, {139.0, 74.7}, {128.8, 37.1}, {42.2, 115.0}, {201.8, 191.6}, {36.2, 104.8},
                {130.0, 158.9}, {149.1, 73.8}, {224.6, 63.9}, {148.9, 244.8}, {143.3, 42.0}, {183.8, 131.7}, {180.5, 153.7}, {222.7, 160.4}, {186.6, 103.4}, {56.1, 158.9},
                {109.4, 130.2}, {108.0, 199.3}, {83.1, 172.7}, {145.1, 169.4}, {172.3, 238.8}, {199.1, 73.1}, {198.7, 174.3}, {204.2, 202.9}, {142.2, 245.8}, {111.2, 73.3},
                {178.8, 231.5}, {182.3, 223.5}, {48.2, 225.8}, {71.3, 107.9}, {143.5, 211.3}, {57.3, 97.0}, {198.0, 41.1}, {73.5, 131.9}, {27.9, 78.0}, {83.0, 158.4},
                {54.8, 140.2}, {236.9, 74.2}, {37.1, 117.6}, {198.0, 214.1}, {182.4, 188.9}, {100.0, 190.4}, {134.6, 228.6}, {158.1, 102.9}, {156.8, 99.3}, {69.8, 211.3},
                {151.6, 66.9}, {173.4, 100.5}, {203.4, 117.5}, {97.1, 150.2}, {133.4, 86.8}, {95.2, 60.3}, {147.4, 104.8}, {150.5, 223.5}, {209.8, 72.5}, {234.7, 215.5},
                {103.1, 182.0}, {60.6, 214.2}, {201.6, 215.8}, {67.5, 71.4}, {238.4, 141.2}, {84.0, 149.4}, {175.0, 212.4}, {180.4, 120.9}, {88.0, 117.2}, {145.8, 81.7}
        };

        String[] deadlines = {
                "2026-07-07T20:34:16", "2026-07-07T21:24:16", "2026-07-07T20:44:16", "2026-07-07T21:51:16", "2026-07-07T20:38:16", "2026-07-07T21:20:16", "2026-07-07T21:16:16", "2026-07-07T20:45:16", "2026-07-07T20:27:16", "2026-07-07T20:53:16",
                "2026-07-07T21:20:16", "2026-07-07T21:00:16", "2026-07-07T20:37:16", "2026-07-07T21:46:16", "2026-07-07T21:46:16", "2026-07-07T20:57:16", "2026-07-07T20:32:16", "2026-07-07T21:50:16", "2026-07-07T21:28:16", "2026-07-07T21:36:16",
                "2026-07-07T20:27:16", "2026-07-07T20:57:16", "2026-07-07T20:56:16", "2026-07-07T21:19:16", "2026-07-07T20:36:16", "2026-07-07T21:41:16", "2026-07-07T21:45:16", "2026-07-07T21:49:16", "2026-07-07T20:29:16", "2026-07-07T21:20:16",
                "2026-07-07T21:37:16", "2026-07-07T21:12:16", "2026-07-07T20:30:16", "2026-07-07T21:33:16", "2026-07-07T21:47:16", "2026-07-07T21:13:16", "2026-07-07T20:32:16", "2026-07-07T20:48:16", "2026-07-07T21:18:16", "2026-07-07T20:56:16",
                "2026-07-07T20:35:16", "2026-07-07T21:28:16", "2026-07-07T20:46:16", "2026-07-07T21:40:16", "2026-07-07T20:37:16", "2026-07-07T21:12:16", "2026-07-07T20:47:16", "2026-07-07T21:53:16", "2026-07-07T20:33:16", "2026-07-07T21:36:16",
                "2026-07-07T21:42:16", "2026-07-07T21:18:16", "2026-07-07T20:27:16", "2026-07-07T21:51:16", "2026-07-07T21:27:16", "2026-07-07T21:07:16", "2026-07-07T20:37:16", "2026-07-07T21:08:16", "2026-07-07T20:58:16", "2026-07-07T21:35:16",
                "2026-07-07T20:35:16", "2026-07-07T20:51:16", "2026-07-07T21:02:16", "2026-07-07T20:53:16", "2026-07-07T20:29:16", "2026-07-07T20:57:16", "2026-07-07T21:17:16", "2026-07-07T21:13:16", "2026-07-07T20:34:16", "2026-07-07T21:21:16",
                "2026-07-07T21:43:16", "2026-07-07T21:43:16", "2026-07-07T20:27:16", "2026-07-07T21:11:16", "2026-07-07T21:35:16", "2026-07-07T21:03:16", "2026-07-07T20:34:16", "2026-07-07T21:21:16", "2026-07-07T21:13:16", "2026-07-07T20:55:16",
                "2026-07-07T20:35:16", "2026-07-07T21:47:16", "2026-07-07T20:52:16", "2026-07-07T21:25:16", "2026-07-07T20:36:16", "2026-07-07T20:49:16", "2026-07-07T21:03:16", "2026-07-07T21:40:16", "2026-07-07T20:36:16", "2026-07-07T21:47:16",
                "2026-07-07T21:40:16", "2026-07-07T21:15:16", "2026-07-07T20:28:16", "2026-07-07T21:32:16", "2026-07-07T21:25:16", "2026-07-07T21:38:16", "2026-07-07T20:36:16", "2026-07-07T21:13:16", "2026-07-07T21:46:16", "2026-07-07T21:45:16"
        };

        for (int i = 0; i < pickups.length; i++) {
            system.addTask(pickups[i][0], pickups[i][1], deliveries[i][0], deliveries[i][1], LocalDateTime.parse(deadlines[i]));
        }
    }

    private static void registerBulkDrones(JobAssignmentSystem system) {
        double[][] starts = {
                {8.0, 12.0}, {16.0, 18.0}, {22.0, 24.0},
                {118.0, 12.0}, {132.0, 18.0}, {146.0, 24.0},
                {8.0, 140.0}, {20.0, 152.0}, {28.0, 166.0},
                {122.0, 140.0}, {138.0, 152.0}, {156.0, 168.0}
        };
        int[] zoneIds = {1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4};

        for (int i = 0; i < starts.length; i++) {
            system.registerDrone(starts[i][0], starts[i][1], zoneIds[i], 10.0);
        }
    }

    /**
     * Alternative: Advanced scenario with battery management
     */
    public static void runAdvancedScenario() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║     ADVANCED SCENARIO: BATTERY MANAGEMENT & EMERGENCY ROUTING                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝\n");

        JobAssignmentSystem system = new JobAssignmentSystem();
        SystemIntegration integration = new SystemIntegration(system);

        for (int i = 0; i < 10; i++) {
            system.addTask(
                Math.random() * 100,
                Math.random() * 100,
                Math.random() * 100 + 50,
                Math.random() * 100 + 50,
                LocalDateTime.now().plusMinutes(30 + (i * 5))
            );
        }

        Drone d1 = system.registerDrone(0, 0, 1, 10);
        Drone d2 = system.registerDrone(50, 50, 1, 10);
        d2.consumeBattery(35);

        Drone d3 = system.registerDrone(100, 100, 2, 10);
        d3.setBattery(15);

        system.divideZonesAutomatically();
        system.assignJobsToDrones();

        System.out.println("Initial Drone Status:");
        System.out.println(system.getDroneStatusTable());

        System.out.println("\n→ Checking battery levels...");
        for (Drone drone : system.getAllDrones()) {
            if (drone.needsCharging()) {
                System.out.println("⚠️  Drone " + drone.getDroneId() + " needs charging (Battery: " +
                    drone.getBattery() + "%). Reassigning tasks...");
                if (drone.getCurrentTask() != null) {
                    drone.getCurrentTask().setAssignedDroneId(-1);
                    drone.getCurrentTask().setCompleted(false);
                }
            }
        }
    }
}
