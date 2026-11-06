/**
 * SystemIntegration class provides integration points between:
 * - JobAssignmentSystem (task/zone/drone management)
 * - MCHRouteGUI (pathfinding and visualization)
 * - RouteCalculator (deadline checking and route feasibility)
 * 
 * This class demonstrates how the routing system (MCHRouteGUI) should:
 * 1. Find shortest paths within deadline constraints
 * 2. Validate routes against drone battery
 * 3. Suggest alternative routes if needed
 * 4. Update job assignment based on route feasibility
 */
public class SystemIntegration {
    private JobAssignmentSystem jobSystem;
    private MCHRouteGUI routeGUI;

    public SystemIntegration(JobAssignmentSystem jobSystem) {
        this.jobSystem = jobSystem;
        // MCHRouteGUI is initialized separately for visualization
    }

    /**
     * Validate and route a drone for its current task
     * Uses MCHRouteGUI to find shortest path, then checks deadline
     */
    public boolean validateAndRouteDrone(Drone drone) {
        Task task = drone.getCurrentTask();
        if (task == null) {
            System.out.println("✗ Drone has no assigned task.");
            return false;
        }

        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║        VALIDATING ROUTE FOR DRONE " + drone.getDroneId() + " - TASK " + task.getTaskId() + "                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        RouteCalculator calculator = new RouteCalculator(drone, task);
        
        // Calculate route to pickup
        System.out.println("\n→ Calculating route to pickup location...");
        boolean canReachPickup = calculator.calculatePathToPickup();
        
        if (!canReachPickup) {
            task.setDeadlineMet(false);
            System.out.println("✗ Task deadline cannot be met!");
            System.out.println("✓ Reassigning task to next available drone...");
            reassignTaskToAlternativeDrone(task);
            return false;
        }

        // Calculate route to delivery
        System.out.println("\n→ Calculating route to delivery location...");
        boolean canCompleteDelivery = calculator.calculatePathToDelivery();
        
        calculator.printRouteDetails();

        if (!canCompleteDelivery) {
            task.setDeadlineMet(false);
            System.out.println("✗ Cannot complete delivery before deadline!");
            return false;
        }

        // Energy is assumed full for this demo, so battery is ignored.
        task.setDeadlineMet(true);
        System.out.println("✓ Route validated successfully!");
        System.out.println(calculator.getRouteSummary());
        return true;
    }

    /**
     * Reassign task to alternative drone if current drone cannot meet deadline
     */
    private void reassignTaskToAlternativeDrone(Task task) {
        Zone taskZone = null;
        for (Zone zone : jobSystem.getZones()) {
            if (zone.getTasksInZone().contains(task)) {
                taskZone = zone;
                break;
            }
        }

        if (taskZone == null) return;

        double minTime = Double.MAX_VALUE;
        Drone bestDrone = null;

        for (Integer droneId : taskZone.getDroneIds()) {
            Drone drone = jobSystem.getAllDrones().stream()
                .filter(d -> d.getDroneId() == droneId)
                .findFirst()
                .orElse(null);

            if (drone == null) continue;

            RouteCalculator calc = new RouteCalculator(drone, task);
            calc.calculatePathToPickup();
            calc.calculatePathToDelivery();

            if (calc.canCompleteBeforeDeadline() && calc.getEstimatedTimeMinutes() < minTime) {
                minTime = calc.getEstimatedTimeMinutes();
                bestDrone = drone;
            }
        }

        if (bestDrone != null) {
            bestDrone.assignTask(task);
            System.out.println("✓ Task reassigned to Drone " + bestDrone.getDroneId());
        } else {
            System.out.println("✗ No alternative drone available for this task.");
        }
    }

    /**
     * Get route suggestions for all pending tasks
     */
    public String getRouteSuggestions() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔═══════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║                        ROUTE SUGGESTIONS                                    ║\n");
        sb.append("╠═════════════════════════════════════════════════════════════════════════════╣\n");

        for (Drone drone : jobSystem.getAllDrones()) {
            Task task = drone.getCurrentTask();
            if (task != null && !task.isCompleted()) {
                RouteCalculator calc = new RouteCalculator(drone, task);
                calc.calculatePathToPickup();
                calc.calculatePathToDelivery();

                String suggestion = String.format(
                    "Drone %d → Task %d: %.2f units in %.2f min | %s",
                    drone.getDroneId(),
                    task.getTaskId(),
                    calc.getTotalDistance(),
                    calc.getEstimatedTimeMinutes(),
                    calc.canCompleteBeforeDeadline() ? "✓ FEASIBLE" : "✗ RISKY"
                );

                sb.append("║ ").append(String.format("%-83s", suggestion)).append(" ║\n");
            }
        }

        sb.append("╚═══════════════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }

    /**
     * Optimize all drone routes based on feasibility
     */
    public void optimizeAllRoutes() {
        System.out.println("\n╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║           OPTIMIZING ALL DRONE ROUTES                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");

        for (Drone drone : jobSystem.getAllDrones()) {
            if (drone.getCurrentTask() != null && !drone.getCurrentTask().isCompleted()) {
                System.out.println("\n→ Processing Drone " + drone.getDroneId());
                validateAndRouteDrone(drone);
            }
        }

        System.out.println("\n✓ Route optimization completed.");
    }

    /**
     * Simulate complete delivery workflow with routing
     */
    public void simulateCompleteDeliveryWorkflow(Drone drone) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  COMPLETE DELIVERY WORKFLOW                                   ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════════════════╝");

        Task task = drone.getCurrentTask();
        if (task == null) {
            System.out.println("✗ No task assigned to drone.");
            return;
        }

        // Step 1: Validate route
        boolean routeOk = validateAndRouteDrone(drone);
        if (!routeOk) {
            System.out.println("⚠ Route validation did not pass, but the simulation will still complete the workflow for reporting purposes.");
        }

        // Step 2: Reach pickup
        System.out.println("\n→ Drone moving to pickup location...");
        drone.reachPickup();
        System.out.println("✓ Pickup completed at (" + task.getPickupX() + ", " + task.getPickupY() + ")");

        // Step 3: Reach delivery
        System.out.println("→ Drone moving to delivery location...");
        drone.reachDelivery();
        System.out.println("✓ Delivery completed at (" + task.getDeliveryX() + ", " + task.getDeliveryY() + ")");

        RouteCalculator deadlineCheck = new RouteCalculator(drone, task);
        deadlineCheck.calculatePathToPickup();
        deadlineCheck.calculatePathToDelivery();
        boolean meetsDeadline = deadlineCheck.canCompleteBeforeDeadline();
        task.setCompleted(true);
        task.setDeadlineMet(meetsDeadline);
        System.out.println("Deadline outcome after task completion: " + (meetsDeadline ? "✓ MEETS DEADLINE" : "✗ MISSES DEADLINE"));

        // Step 4: Find next job
        System.out.println("→ Finding next pickup location...");
        Task nextTask = jobSystem.findNextPickupForDrone(drone);
        
        if (nextTask != null && nextTask.getAssignedDroneId() < 0) {
            System.out.println("✓ Found next task: " + nextTask);
            jobSystem.assignNextJobToDrone(drone);
            System.out.println("✓ Automatically assigned next job to drone.");
        } else {
            System.out.println("✓ No more unassigned tasks in this zone.");
        }

        System.out.println("\nFinal Drone Status: " + drone);
    }

    /**
     * Display integration information
     */
    public String getIntegrationInfo() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n╔════════════════════════════════════════════════════════════════════════════════╗\n");
        sb.append("║          SYSTEM INTEGRATION INFORMATION                                     ║\n");
        sb.append("╠════════════════════════════════════════════════════════════════════════════╣\n");
        sb.append("║ COMPONENTS:                                                                  ║\n");
        sb.append("║ 1. JobAssignmentSystem  - Task, Zone, and Drone Management                  ║\n");
        sb.append("║ 2. MCHRouteGUI           - Shortest Path Finding and Visualization          ║\n");
        sb.append("║ 3. RouteCalculator       - Deadline Validation and Route Feasibility        ║\n");
        sb.append("║ 4. SystemIntegration     - Coordinator Between Components                   ║\n");
        sb.append("║                                                                              ║\n");
        sb.append("║ WORKFLOW:                                                                    ║\n");
        sb.append("║ 1. Add Tasks → 2. Register Drones → 3. Divide Zones → 4. Assign Jobs       ║\n");
        sb.append("║ 5. Validate Routes (MCHRouteGUI finds shortest path within deadline)        ║\n");
        sb.append("║ 6. Execute Delivery → 7. Find Next Nearest Pickup → 8. Auto-Assign Job     ║\n");
        sb.append("║                                                                              ║\n");
        sb.append("║ KEY FEATURES:                                                                ║\n");
        sb.append("║ ✓ Centralized Task Table with Pickup/Delivery/Deadline                      ║\n");
        sb.append("║ ✓ Automatic Zone Division Based on Task Density                              ║\n");
        sb.append("║ ✓ Task Prioritization by Deadline Within Each Zone                           ║\n");
        sb.append("║ ✓ Intelligent Drone-Task Assignment                                          ║\n");
        sb.append("║ ✓ Nearest Neighbor Selection for Next Pickup                                 ║\n");
        sb.append("║ ✓ Deadline Compliance Validation                                             ║\n");
        sb.append("║ ✓ Energy is assumed full for this demo                                      ║\n");
        sb.append("║ ✓ Route Optimization and Visualization                                       ║\n");
        sb.append("╚════════════════════════════════════════════════════════════════════════════╝\n");
        return sb.toString();
    }
}
