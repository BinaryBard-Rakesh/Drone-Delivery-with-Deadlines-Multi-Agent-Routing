import java.util.List;

/**
 * Single-token task assignment driver.
 * Uses the same task input logic as Demo.java and compares tasks under a single global token.
 */
public class singletoken {

    public static void main(String[] args) {
        int taskCount = 50;
        int zoneCount = 3;
        int totalDroneCount = 9;
        int dronesPerZone = 3;
        double droneSpeedUnitsPerMinute = 60.0;

        if (args != null && args.length >= 1) {
            try {
                taskCount = Integer.parseInt(args[0]);
                if (args.length >= 2) {
                    zoneCount = Integer.parseInt(args[1]);
                }
                if (args.length >= 3) {
                    totalDroneCount = Integer.parseInt(args[2]);
                    dronesPerZone = Math.max(1, totalDroneCount / Math.max(1, zoneCount));
                }
                if (args.length >= 4) {
                    droneSpeedUnitsPerMinute = Double.parseDouble(args[3]);
                }
            } catch (NumberFormatException ignored) {}
        }

        Demo.SavedDemoScenario savedScenario = Demo.loadScenarioFromDemoInputFile();
        int actualTaskCount = taskCount;
        int actualZoneCount = zoneCount;
        int actualDronesPerZone = dronesPerZone;
        int actualTotalDrones = totalDroneCount;
        double actualDroneSpeed = droneSpeedUnitsPerMinute;
        JobAssignmentSystem singleTokenSystem;
        JobAssignmentSystem multiTokenSystem;

        if (savedScenario != null) {
            System.out.println("Loaded tasks from demo_input.txt for single-token comparison.");
            actualTaskCount = savedScenario.system.getAllTasks().size();
            actualZoneCount = savedScenario.zoneCount;
            actualDronesPerZone = savedScenario.dronesPerZone;
            actualTotalDrones = savedScenario.totalDrones;
            actualDroneSpeed = savedScenario.droneSpeedUnitsPerMinute;
            singleTokenSystem = createScenarioFromExistingTasks(savedScenario.system, actualZoneCount, actualTotalDrones, actualDroneSpeed);
            multiTokenSystem = createScenarioFromExistingTasks(savedScenario.system, actualZoneCount, actualTotalDrones, actualDroneSpeed);
        } else {
            actualTotalDrones = zoneCount * dronesPerZone;
            singleTokenSystem = Demo.createFullScenario(taskCount, zoneCount, dronesPerZone, droneSpeedUnitsPerMinute);
            multiTokenSystem = Demo.createFullScenario(taskCount, zoneCount, dronesPerZone, droneSpeedUnitsPerMinute);
        }

        singleTokenSystem.assignJobsSingleToken();
        multiTokenSystem.assignJobsToDrones();

        int singleTokenCapacity = singleTokenSystem.countSingleTokenFeasibleTasks();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("COMPARISON: SINGLE-TOKEN vs MULTI-TOKEN");
        System.out.println("Tasks: " + actualTaskCount + " | Zones: " + actualZoneCount + " | Total Drones: " + actualTotalDrones + " | Drones per Zone: " + actualDronesPerZone);
        System.out.println("=".repeat(80));
        System.out.println(singleTokenSystem.getSingleTokenCompactTable());
        System.out.println("Single-token theoretical capacity count: " + singleTokenCapacity);
        System.out.println(singleTokenSystem.getSingleVsMultiTokenComparisonTable(
                countCompletedTasks(singleTokenSystem),
                countCompletedTasks(multiTokenSystem)
        ));
    }

    private static int countCompletedTasks(JobAssignmentSystem system) {
        if (system == null) {
            return 0;
        }
        return (int) system.getAllTasks().stream().filter(Task::isCompleted).count();
    }

    private static JobAssignmentSystem createScenarioFromExistingTasks(JobAssignmentSystem baseSystem, int zoneCount, int totalDrones, double droneSpeedUnitsPerMinute) {
        JobAssignmentSystem system = new JobAssignmentSystem();
        for (Task task : baseSystem.getAllTasks()) {
            system.addTask(task.getPickupX(), task.getPickupY(), task.getDeliveryX(), task.getDeliveryY(), task.getDeadline());
        }
        system.divideZonesAutomatically(zoneCount);
        registerDronesForZones(system, totalDrones, droneSpeedUnitsPerMinute);
        return system;
    }

    private static void registerDronesForZones(JobAssignmentSystem system, int totalDrones, double speed) {
        if (system == null || totalDrones <= 0) {
            return;
        }

        List<Zone> zones = system.getZones();
        int zoneCount = Math.max(1, zones.size());
        int baseCount = totalDrones / zoneCount;
        int remainder = totalDrones % zoneCount;

        for (int zoneIndex = 0; zoneIndex < zones.size(); zoneIndex++) {
            Zone zone = zones.get(zoneIndex);
            int dronesInZone = baseCount + (zoneIndex < remainder ? 1 : 0);
            double centerX = zone.getCenterX();
            double centerY = zone.getCenterY();
            for (int i = 0; i < dronesInZone; i++) {
                double angle = 2 * Math.PI * i / Math.max(1, dronesInZone);
                double startX = centerX + 10.0 * Math.cos(angle);
                double startY = centerY + 10.0 * Math.sin(angle);
                system.registerDrone(startX, startY, zone.getZoneId(), Math.max(10.0, speed));
            }
        }
    }

    private static boolean containsArg(String[] args, String target) { return false; }
}
