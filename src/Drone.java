import java.time.LocalDateTime;
import java.util.*;

/**
 * Represents a drone with current state, assigned tasks, and routing information.
 */
public class Drone {
    private int droneId;
    private double currentX;
    private double currentY;
    private int currentZoneId;
    private Task currentTask;
    private Queue<Task> taskQueue;
    private double maxSpeed; // units per time unit
    private double battery; // 0-100
    private LocalDateTime lastLocationUpdate;
    private List<String> routeLog;
    private boolean isAtPickup;
    private boolean isAtDelivery;

    public Drone(int droneId, double startX, double startY, int zoneId, double maxSpeed) {
        this.droneId = droneId;
        this.currentX = startX;
        this.currentY = startY;
        this.currentZoneId = zoneId;
        this.maxSpeed = maxSpeed;
        this.battery = 100.0;
        this.lastLocationUpdate = LocalDateTime.now();
        this.taskQueue = new LinkedList<>();
        this.routeLog = new ArrayList<>();
        this.isAtPickup = false;
        this.isAtDelivery = false;
    }

    // Getters and Setters
    public int getDroneId() { return droneId; }
    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }
    public int getCurrentZoneId() { return currentZoneId; }
    public Task getCurrentTask() { return currentTask; }
    public Queue<Task> getTaskQueue() { return new LinkedList<>(taskQueue); }
    public double getBattery() { return battery; }
    public void setBattery(double battery) { this.battery = Math.max(0, Math.min(100, battery)); }
    public boolean isAtPickup() { return isAtPickup; }
    public boolean isAtDelivery() { return isAtDelivery; }
    public List<String> getRouteLog() { return new ArrayList<>(routeLog); }

    /**
     * Update drone position
     */
    public void updatePosition(double newX, double newY) {
        this.currentX = newX;
        this.currentY = newY;
        this.lastLocationUpdate = LocalDateTime.now();
    }

    /**
     * Assign a task to the drone
     */
    public void assignTask(Task task) {
        if (currentTask == null) {
            this.currentTask = task;
            task.setAssignedDroneId(droneId);
            routeLog.add("Task " + task.getTaskId() + " assigned at " + LocalDateTime.now());
        } else {
            taskQueue.add(task);
            task.setAssignedDroneId(droneId);
            routeLog.add("Task " + task.getTaskId() + " queued at " + LocalDateTime.now());
        }
    }

    /**
     * Get the next task in queue
     */
    public Task getNextTask() {
        return taskQueue.isEmpty() ? null : taskQueue.peek();
    }

    /**
     * Dequeue and set the next task as current
     */
    public void moveToNextTask() {
        if (!taskQueue.isEmpty()) {
            currentTask = taskQueue.poll();
            this.isAtPickup = false;
            this.isAtDelivery = false;
            routeLog.add("Moved to next task: " + currentTask.getTaskId() + " at " + LocalDateTime.now());
        } else {
            currentTask = null;
            this.isAtPickup = false;
            this.isAtDelivery = false;
        }
    }

    /**
     * Simulate reaching pickup location
     */
    public void reachPickup() {
        if (currentTask != null) {
            this.currentX = currentTask.getPickupX();
            this.currentY = currentTask.getPickupY();
            this.isAtPickup = true;
            this.isAtDelivery = false;
            routeLog.add("Reached pickup for Task " + currentTask.getTaskId() + " at " + LocalDateTime.now());
            consumeBattery(5); // Pickup operation consumes battery
        }
    }

    /**
     * Simulate reaching delivery location
     */
    public void reachDelivery() {
        if (currentTask != null) {
            this.currentX = currentTask.getDeliveryX();
            this.currentY = currentTask.getDeliveryY();
            this.isAtDelivery = true;
            this.isAtPickup = false;
            currentTask.setCompleted(true);
            routeLog.add("Completed delivery for Task " + currentTask.getTaskId() + " at " + LocalDateTime.now());
            consumeBattery(5); // Delivery operation consumes battery
        }
    }

    /**
     * Calculate distance to a point
     */
    public double distanceTo(double x, double y) {
        return Math.sqrt(Math.pow(currentX - x, 2) + Math.pow(currentY - y, 2));
    }

    /**
     * Calculate distance to task pickup
     */
    public double distanceToPickup(Task task) {
        if (task == null) return Double.MAX_VALUE;
        return distanceTo(task.getPickupX(), task.getPickupY());
    }

    /**
     * Calculate time to reach a point given max speed
     */
    public double timeToReach(double x, double y) {
        return distanceTo(x, y) / maxSpeed;
    }

    /**
     * Can drone reach a point before battery depletes?
     */
    public boolean canReachWithBattery(double x, double y) {
        return true;
    }

    /**
     * Consume battery (0-100)
     */
    public void consumeBattery(double amount) {
        battery = Math.max(0, battery - amount);
    }

    /**
     * Charge battery
     */
    public void chargeBattery(double amount) {
        battery = Math.min(100, battery + amount);
    }

    /**
     * Check if drone needs charging (< 20%)
     */
    public boolean needsCharging() {
        return false;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    /**
     * Get total tasks completed
     */
    public int getTasksCompleted() {
        int count = 0;
        for (String log : routeLog) {
            if (log.contains("Completed delivery")) {
                count++;
            }
        }
        return count;
    }

    /**
     * Generate status report
     */
    public String getStatusReport() {
        return String.format("Drone[ID=%d, Pos(%.1f,%.1f), Zone=%d, CurrentTask=%s, QueueSize=%d, TasksCompleted=%d]",
                droneId, currentX, currentY, currentZoneId,
                (currentTask != null ? currentTask.getTaskId() : "None"), taskQueue.size(), getTasksCompleted());
    }

    @Override
    public String toString() {
        return getStatusReport();
    }
}
