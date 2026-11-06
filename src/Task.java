import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents a single task (job) with pickup and delivery locations.
 */
public class Task {
    private int taskId;
    private double pickupX;
    private double pickupY;
    private double deliveryX;
    private double deliveryY;
    private LocalDateTime deadline;
    private boolean completed;
    private boolean deadlineMet;
    private int assignedDroneId;
    private LocalDateTime createdTime;
    private int priority; // Higher priority = higher number

    public Task(int taskId, double pickupX, double pickupY, 
                double deliveryX, double deliveryY, LocalDateTime deadline) {
        this.taskId = taskId;
        this.pickupX = pickupX;
        this.pickupY = pickupY;
        this.deliveryX = deliveryX;
        this.deliveryY = deliveryY;
        this.deadline = deadline;
        this.completed = false;
        this.deadlineMet = false;
        this.assignedDroneId = -1;
        this.createdTime = LocalDateTime.now();
        this.priority = 0;
    }

    // Getters and Setters
    public int getTaskId() { return taskId; }
    public double getPickupX() { return pickupX; }
    public double getPickupY() { return pickupY; }
    public double getDeliveryX() { return deliveryX; }
    public double getDeliveryY() { return deliveryY; }
    public LocalDateTime getDeadline() { return deadline; }
    public boolean isCompleted() { return completed; }
    public void setCompleted(boolean completed) { this.completed = completed; }
    public boolean isDeadlineMet() { return deadlineMet; }
    public void setDeadlineMet(boolean deadlineMet) { this.deadlineMet = deadlineMet; }
    public int getAssignedDroneId() { return assignedDroneId; }
    public void setAssignedDroneId(int droneId) { this.assignedDroneId = droneId; }
    public LocalDateTime getCreatedTime() { return createdTime; }
    public int getPriority() { return priority; }
    public void setPriority(int priority) { this.priority = priority; }

    /**
     * Calculate time remaining until deadline
     */
    public long getTimeRemainingMinutes() {
        return Math.max(0, java.time.temporal.ChronoUnit.MINUTES.between(LocalDateTime.now(), deadline));
    }

    /**
     * Calculate distance from a point to pickup location
     */
    public double distanceToPickup(double x, double y) {
        return Math.sqrt(Math.pow(x - pickupX, 2) + Math.pow(y - pickupY, 2));
    }

    /**
     * Calculate distance from delivery to a point
     */
    public double distanceFromDelivery(double x, double y) {
        return Math.sqrt(Math.pow(x - deliveryX, 2) + Math.pow(y - deliveryY, 2));
    }

    @Override
    public String toString() {
        return String.format("Task[ID=%d, Pickup(%.1f,%.1f), Delivery(%.1f,%.1f), Deadline=%s, Priority=%d]", 
                taskId, pickupX, pickupY, deliveryX, deliveryY, deadline, priority);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Task)) return false;
        Task task = (Task) o;
        return taskId == task.taskId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(taskId);
    }
}
