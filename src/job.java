import java.time.LocalDateTime;
import java.util.Scanner;

/**
 * Main entry point for the Drone Job Assignment System.
 * Coordinates task management, zone division, drone assignment, and routing.
 */
public class job {

    private static JobAssignmentSystem system;
    private static Scanner scanner;

    public static void main(String[] args) {
        system = new JobAssignmentSystem();
        scanner = new Scanner(System.in);

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║    DRONE JOB ASSIGNMENT & ROUTING SYSTEM - WELCOME            ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        boolean running = true;
        while (running) {
            displayMainMenu();
            int choice = getIntInput();

            switch (choice) {
                case 1:
                    addNewTask();
                    break;
                case 2:
                    registerNewDrone();
                    break;
                case 3:
                    divideZones();
                    break;
                case 4:
                    assignJobs();
                    break;
                case 5:
                    simulateDroneDelivery();
                    break;
                case 6:
                    viewCompleteReport();
                    break;
                case 7:
                    viewStatistics();
                    break;
                case 8:
                    running = false;
                    System.out.println("\n✓ System shutdown. Goodbye!");
                    break;
                default:
                    System.out.println("✗ Invalid choice. Please try again.");
            }
        }
        scanner.close();
    }

    private static void displayMainMenu() {
        System.out.println("\n┌────────────────────────────────────────┐");
        System.out.println("│        MAIN MENU - SELECT OPTION       │");
        System.out.println("├────────────────────────────────────────┤");
        System.out.println("│ 1. Add New Task                        │");
        System.out.println("│ 2. Register New Drone                  │");
        System.out.println("│ 3. Divide Zones (Auto Distribution)    │");
        System.out.println("│ 4. Assign Jobs to Drones               │");
        System.out.println("│ 5. Simulate Drone Delivery             │");
        System.out.println("│ 6. View Complete System Report         │");
        System.out.println("│ 7. View System Statistics              │");
        System.out.println("│ 8. Exit                                │");
        System.out.println("└────────────────────────────────────────┘");
        System.out.print("Enter your choice (1-8): ");
    }

    private static void addNewTask() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║         ADD NEW TASK                       ║");
        System.out.println("╚════════════════════════════════════════════╝");

        System.out.print("Enter Pickup X coordinate: ");
        double pickupX = getDoubleInput();

        System.out.print("Enter Pickup Y coordinate: ");
        double pickupY = getDoubleInput();

        System.out.print("Enter Delivery X coordinate: ");
        double deliveryX = getDoubleInput();

        System.out.print("Enter Delivery Y coordinate: ");
        double deliveryY = getDoubleInput();

        System.out.println("\nEnter deadline (format: YYYY-MM-DD HH:MM:SS or press Enter for 4 hours from now):");
        String deadlineStr = scanner.nextLine();
        LocalDateTime deadline;

        if (deadlineStr.isEmpty()) {
            deadline = LocalDateTime.now().plusHours(4);
            System.out.println("Default deadline set to: " + deadline);
        } else {
            try {
                deadline = LocalDateTime.parse(deadlineStr.replace(" ", "T"));
            } catch (Exception e) {
                deadline = LocalDateTime.now().plusHours(4);
                System.out.println("Invalid format. Using default 4 hours from now: " + deadline);
            }
        }

        Task task = system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
        System.out.println("\n✓ Task added successfully: " + task);
    }

    private static void registerNewDrone() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║       REGISTER NEW DRONE                   ║");
        System.out.println("╚════════════════════════════════════════════╝");

        System.out.print("Enter Drone Start X coordinate: ");
        double startX = getDoubleInput();

        System.out.print("Enter Drone Start Y coordinate: ");
        double startY = getDoubleInput();

        System.out.print("Enter Zone ID (1 for Zone 1, 2 for Zone 2, etc., or 1 for default): ");
        int zoneId = getIntInput();
        if (zoneId < 1) zoneId = 1;

        System.out.print("Enter Max Speed (units per time, default 10): ");
        double maxSpeed = getDoubleInput();
        if (maxSpeed <= 0) maxSpeed = 10.0;

        Drone drone = system.registerDrone(startX, startY, zoneId, maxSpeed);
        System.out.println("\n✓ Drone registered successfully: " + drone);
    }

    private static void divideZones() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      DIVIDING ZONES (Auto Distribution)    ║");
        System.out.println("╚════════════════════════════════════════════╝");

        system.divideZonesAutomatically();
        System.out.println("\n✓ Zones divided successfully.");
        System.out.println(system.getZoneTable());

        // Show zone details
        for (Zone zone : system.getZones()) {
            System.out.println(system.getZoneTaskTable(zone.getZoneId()));
        }
    }

    private static void assignJobs() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      ASSIGNING JOBS TO DRONES              ║");
        System.out.println("╚════════════════════════════════════════════╝");

        system.assignJobsToDrones();
        System.out.println("\n✓ Job assignment completed.");
        System.out.println(system.getDroneStatusTable());
    }

    private static void simulateDroneDelivery() {
        System.out.println("\n╔════════════════════════════════════════════╗");
        System.out.println("║      SIMULATE DRONE DELIVERY               ║");
        System.out.println("╚════════════════════════════════════════════╝");

        java.util.List<Drone> drones = system.getAllDrones();
        if (drones.isEmpty()) {
            System.out.println("✗ No drones registered.");
            return;
        }

        System.out.println("\nAvailable Drones:");
        for (int i = 0; i < drones.size(); i++) {
            System.out.println((i + 1) + ". " + drones.get(i));
        }

        System.out.print("Select drone number: ");
        int droneIdx = getIntInput() - 1;

        if (droneIdx < 0 || droneIdx >= drones.size()) {
            System.out.println("✗ Invalid drone selection.");
            return;
        }

        Drone drone = drones.get(droneIdx);
        Task currentTask = drone.getCurrentTask();

        if (currentTask == null) {
            System.out.println("✗ Drone has no assigned task.");
            return;
        }

        System.out.println("\n--- Simulating Delivery for " + drone + " ---");
        System.out.println("Current Task: " + currentTask);

        // Simulate reaching pickup
        System.out.println("\n→ Flying to pickup location (" + currentTask.getPickupX() + ", " + currentTask.getPickupY() + ")");
        drone.reachPickup();
        System.out.println("✓ Reached pickup location");
        System.out.println("  Energy: Full");

        // Simulate reaching delivery
        System.out.println("\n→ Flying to delivery location (" + currentTask.getDeliveryX() + ", " + currentTask.getDeliveryY() + ")");
        drone.reachDelivery();
        System.out.println("✓ Reached delivery location");
        System.out.println("  Energy: Full");
        System.out.println("  Task Status: COMPLETED");

        // Find next pickup
        Task nextPickup = system.findNextPickupForDrone(drone);
        if (nextPickup != null && nextPickup.getAssignedDroneId() < 0) {
            System.out.println("\n→ Finding next pickup location...");
            System.out.println("✓ Found nearest unassigned task: " + nextPickup);
            system.assignNextJobToDrone(drone);
        } else {
            System.out.println("\n✓ No more unassigned tasks for this drone.");
        }

        System.out.println("\nUpdated Drone Status: " + drone);
    }

    private static void viewCompleteReport() {
        System.out.println(system.getCompleteSystemReport());
    }

    private static void viewStatistics() {
        System.out.println(system.getSummaryStatistics());
    }

    // ═══════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════

    private static int getIntInput() {
        try {
            return Integer.parseInt(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static double getDoubleInput() {
        try {
            return Double.parseDouble(scanner.nextLine().trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }
}
