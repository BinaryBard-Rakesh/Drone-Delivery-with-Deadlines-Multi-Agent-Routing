import java.time.LocalDateTime;

public class ZoneAssignmentVerification {
    public static void main(String[] args) {
        JobAssignmentSystem system = new JobAssignmentSystem();

        Task zoneOneTask = system.addTask(10, 10, 20, 20, LocalDateTime.now().plusHours(2));
        Task zoneTwoTask = system.addTask(500, 500, 600, 600, LocalDateTime.now().plusHours(2));

        Drone zoneOneDrone = system.registerDrone(10, 10, 1, 20);
        Drone zoneTwoDrone = system.registerDrone(500, 500, 2, 20);

        boolean assignedToWrongZone = system.assignTaskToDroneIfZoneMatches(zoneTwoDrone, zoneOneTask);
        boolean assignedToCorrectZone = system.assignTaskToDroneIfZoneMatches(zoneOneDrone, zoneOneTask);

        if (assignedToWrongZone) {
            throw new IllegalStateException("Cross-zone assignment was allowed.");
        }

        if (!assignedToCorrectZone) {
            throw new IllegalStateException("Valid intra-zone assignment was rejected.");
        }

        System.out.println("Zone assignment verification passed.");
    }
}
