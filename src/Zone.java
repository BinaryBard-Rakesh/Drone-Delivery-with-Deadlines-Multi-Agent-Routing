import java.util.*;
import java.time.LocalDateTime;

/**
 * Represents a geographic zone with centralized pickup/delivery points.
 * Tasks are prioritized by deadline and assigned to drones in this zone.
 */
public class Zone {
    private int zoneId;
    private double centerX;
    private double centerY;
    private double radius;
    private List<Task> tasksInZone;
    private List<Integer> droneIds; // Drones assigned to this zone
    private Map<Integer, LocalDateTime> lastTaskCompletionTime; // Track completion per drone

    public Zone(int zoneId, double centerX, double centerY, double radius) {
        this.zoneId = zoneId;
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.tasksInZone = new ArrayList<>();
        this.droneIds = new ArrayList<>();
        this.lastTaskCompletionTime = new HashMap<>();
    }

    // Getters and Setters
    public int getZoneId() { return zoneId; }
    public double getCenterX() { return centerX; }
    public double getCenterY() { return centerY; }
    public double getRadius() { return radius; }
    public void setRadius(double radius) { this.radius = Math.max(6.0, radius); }
    public List<Task> getTasksInZone() { return new ArrayList<>(tasksInZone); }
    public List<Integer> getDroneIds() { return new ArrayList<>(droneIds); }

    /**
     * Add a task to this zone
     */
    public void addTask(Task task) {
        if (!tasksInZone.contains(task)) {
            tasksInZone.add(task);
        }
    }

    /**
     * Remove a task from this zone
     */
    public void removeTask(Task task) {
        tasksInZone.remove(task);
    }

    /**
     * Add a drone to this zone
     */
    public void addDrone(int droneId) {
        if (!droneIds.contains(droneId)) {
            droneIds.add(droneId);
            lastTaskCompletionTime.put(droneId, LocalDateTime.now());
        }
    }

    /**
     * Get all non-completed tasks sorted by priority (deadline-based)
     */
    public List<Task> getTasksPrioritizedByDeadline() {
        List<Task> pendingTasks = new ArrayList<>();
        for (Task task : tasksInZone) {
            if (!task.isCompleted()) {
                pendingTasks.add(task);
            }
        }
        
        // Sort by deadline (earlier deadline = higher priority)
        pendingTasks.sort((t1, t2) -> {
            long timeLeft1 = t1.getTimeRemainingMinutes();
            long timeLeft2 = t2.getTimeRemainingMinutes();
            return Long.compare(timeLeft1, timeLeft2);
        });
        
        return pendingTasks;
    }

    /**
     * Find nearest uncompleted task to a given location
     */
    public Task findNearestTask(double x, double y) {
        Task nearest = null;
        double minDistance = Double.MAX_VALUE;

        for (Task task : tasksInZone) {
            if (!task.isCompleted()) {
                double dist = task.distanceToPickup(x, y);
                if (dist < minDistance) {
                    minDistance = dist;
                    nearest = task;
                }
            }
        }
        
        return nearest;
    }

    /**
     * Find next pickup location after drone completes a delivery
     */
    public Task findNextPickupAfterDelivery(double deliveryX, double deliveryY) {
        return findNearestTask(deliveryX, deliveryY);
    }

    /**
     * Check if a point is within this zone
     */
    public boolean isPointInZone(double x, double y) {
        double distance = Math.sqrt(Math.pow(x - centerX, 2) + Math.pow(y - centerY, 2));
        return distance <= radius;
    }

    /**
     * Get count of tasks in this zone
     */
    public int getTaskCount() {
        return tasksInZone.size();
    }

    /**
     * Get count of pending (non-completed) tasks
     */
    public int getPendingTaskCount() {
        return (int) tasksInZone.stream().filter(t -> !t.isCompleted()).count();
    }

    /**
     * Record task completion for a drone
     */
    public void recordTaskCompletion(int droneId) {
        lastTaskCompletionTime.put(droneId, LocalDateTime.now());
    }

    @Override
    public String toString() {
        return String.format("Zone[ID=%d, Center(%.1f,%.1f), Radius=%.1f, Tasks=%d, Drones=%d]", 
                zoneId, centerX, centerY, radius, tasksInZone.size(), droneIds.size());
    }
}
