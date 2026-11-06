import java.util.*;

/**
 * RouteCalculator handles shortest path finding and deadline validation for drones.
 * Integrates with MCHRouteGUI for visualization and actual pathfinding.
 */
public class RouteCalculator {
    private Drone drone;
    private Task targetTask;
    private double maxSpeed;
    private List<String> routeSteps;
    private double totalDistance;
    private double estimatedTimeMinutes;

    public RouteCalculator(Drone drone, Task targetTask) {
        this.drone = drone;
        this.targetTask = targetTask;
        this.maxSpeed = (drone != null) ? drone.getMaxSpeed() : 60.0; // use drone's speed when available
        this.routeSteps = new ArrayList<>();
        this.totalDistance = 0;
        this.estimatedTimeMinutes = 0;
    }

    /**
     * Calculate shortest path from drone to pickup location
     */
    public boolean calculatePathToPickup() {
        double pickupX = targetTask.getPickupX();
        double pickupY = targetTask.getPickupY();

        double distanceToPickup = calculateDistanceWithPath(
            drone.getCurrentX(), drone.getCurrentY(),
            pickupX, pickupY
        );

        estimatedTimeMinutes = distanceToPickup / maxSpeed;

        routeSteps.add("Start at: (" + drone.getCurrentX() + ", " + drone.getCurrentY() + ")");
        routeSteps.add("Fly to Pickup: (" + pickupX + ", " + pickupY + ")");
        routeSteps.add("Distance: " + String.format("%.2f units", distanceToPickup));
        routeSteps.add("Estimated Time: " + String.format("%.2f minutes", estimatedTimeMinutes));

        totalDistance = distanceToPickup;

        return canCompleteBeforeDeadline();
    }

    /**
     * Calculate shortest path from pickup to delivery location
     */
    public boolean calculatePathToDelivery() {
        double pickupX = targetTask.getPickupX();
        double pickupY = targetTask.getPickupY();
        double deliveryX = targetTask.getDeliveryX();
        double deliveryY = targetTask.getDeliveryY();

        double distancePickupToDelivery = calculateDistanceWithPath(pickupX, pickupY, deliveryX, deliveryY);
        double deliveryTimeMinutes = distancePickupToDelivery / maxSpeed;

        routeSteps.add("From Pickup: (" + pickupX + ", " + pickupY + ")");
        routeSteps.add("To Delivery: (" + deliveryX + ", " + deliveryY + ")");
        routeSteps.add("Distance: " + String.format("%.2f units", distancePickupToDelivery));
        routeSteps.add("Estimated Time: " + String.format("%.2f minutes", deliveryTimeMinutes));

        totalDistance += distancePickupToDelivery;
        estimatedTimeMinutes += deliveryTimeMinutes;

        return canCompleteBeforeDeadline();
    }

    /**
     * Check if task can be completed before deadline
     */
    public boolean canCompleteBeforeDeadline() {
        long timeRemainingMinutes = targetTask.getTimeRemainingMinutes();
        
        // Add buffer for pickup and delivery operations (10 minutes each)
        double totalTimeNeededMinutes = estimatedTimeMinutes + 10 + 10;

        routeSteps.add("---");
        routeSteps.add("Total Time Needed: " + String.format("%.2f minutes", totalTimeNeededMinutes));
        routeSteps.add("Time Remaining: " + timeRemainingMinutes + " minutes");

        boolean feasible = totalTimeNeededMinutes <= timeRemainingMinutes;
        if (feasible) {
            routeSteps.add("✓ Task CAN be completed before deadline");
        } else {
            routeSteps.add("✗ Task CANNOT be completed before deadline");
        }

        return feasible;
    }

    /**
     * Calculate Euclidean distance between two points
     */
    private double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2));
    }

    private double calculateDistanceWithPath(double x1, double y1, double x2, double y2) {
        if (MCHRouteGUI.grid != null && MCHRouteGUI.rows > 0 && MCHRouteGUI.cols > 0) {
            int startX = (int) Math.round(x1);
            int startY = (int) Math.round(y1);
            int goalX = (int) Math.round(x2);
            int goalY = (int) Math.round(y2);
            if (startX >= 0 && startX < MCHRouteGUI.cols && startY >= 0 && startY < MCHRouteGUI.rows
                    && goalX >= 0 && goalX < MCHRouteGUI.cols && goalY >= 0 && goalY < MCHRouteGUI.rows) {
                List<MCHRouteGUI.Pt> path = MCHRouteGUI.computeGridShortestPath(new MCHRouteGUI.Pt(startX, startY), new MCHRouteGUI.Pt(goalX, goalY));
                if (path != null && path.size() > 1) {
                    double total = 0.0;
                    for (int i = 1; i < path.size(); i++) {
                        total += MCHRouteGUI.estimateShortestDistance(path.get(i - 1).x, path.get(i - 1).y, path.get(i).x, path.get(i).y);
                    }
                    if (total > 0.0) {
                        return total;
                    }
                }
            }
        }
        return calculateDistance(x1, y1, x2, y2);
    }

    /**
     * Simulate A* pathfinding (simplified version for demonstration)
     * In production, this would integrate with MCHRouteGUI's actual pathfinding
     */
    public List<String> simulateAStar(double startX, double startY, double goalX, double goalY) {
        List<String> path = new ArrayList<>();
        
        // Simplified straight-line path
        path.add("Start: (" + startX + ", " + startY + ")");
        
        // Calculate intermediate waypoints (simplified grid-based)
        int steps = 5;
        for (int i = 1; i < steps; i++) {
            double x = startX + (goalX - startX) * (i / (double) steps);
            double y = startY + (goalY - startY) * (i / (double) steps);
            path.add("Waypoint " + i + ": (" + String.format("%.1f", x) + ", " + String.format("%.1f", y) + ")");
        }
        
        path.add("Goal: (" + goalX + ", " + goalY + ")");
        
        return path;
    }

    /**
     * Get the calculated route steps
     */
    public List<String> getRouteSteps() {
        return new ArrayList<>(routeSteps);
    }

    /**
     * Get total distance of the route
     */
    public double getTotalDistance() {
        return totalDistance;
    }

    /**
     * Get estimated time in minutes
     */
    public double getEstimatedTimeMinutes() {
        return estimatedTimeMinutes;
    }

    /**
     * Print route details
     */
    public void printRouteDetails() {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║              ROUTE CALCULATION DETAILS                             ║");
        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");

        for (String step : routeSteps) {
            System.out.println("║ " + String.format("%-65s", step) + " ║");
        }

        System.out.println("╠═══════════════════════════════════════════════════════════════════╣");
        System.out.println("║ Energy is assumed full for this demo.                                 ║");
        System.out.println("║ Drone route feasibility is based on time and deadline only.           ║");
        System.out.println("╚═══════════════════════════════════════════════════════════════════╝\n");
    }

    /**
     * Get route summary
     */
    public String getRouteSummary() {
        return String.format(
            "Task %d: Distance=%.2f units, EstTime=%.2f min, Feasible=%s",
            targetTask.getTaskId(),
            totalDistance,
            estimatedTimeMinutes,
            canCompleteBeforeDeadline() ? "Yes" : "No"
        );
    }
}
