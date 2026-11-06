import java.time.LocalDateTime;

public class ZoneReportVerification {
    public static void main(String[] args) {
        JobAssignmentSystem system = new JobAssignmentSystem();
        system.addTask(100, 120, 180, 220, LocalDateTime.now().plusMinutes(20));
        system.addTask(500, 520, 680, 720, LocalDateTime.now().plusMinutes(25));
        system.addTask(900, 100, 950, 180, LocalDateTime.now().plusMinutes(30));
        system.registerDrone(110, 130, 1, 10);
        system.registerDrone(510, 530, 2, 10);
        system.registerDrone(920, 110, 3, 10);
        system.divideZonesAutomatically(3);
        system.assignJobsToDrones();

        String parallelReport = system.getParallelZoneExecutionReport();
        if (!parallelReport.contains("Path Covered")) {
            throw new RuntimeException("Parallel zone report is missing the Path Covered column");
        }

        String dronePathReport = system.getDronePathReport();
        if (!dronePathReport.contains("Total Path Travelled")) {
            throw new RuntimeException("Drone path report is missing the total path section");
        }

        System.out.println("Zone report verification passed.");
    }
}
