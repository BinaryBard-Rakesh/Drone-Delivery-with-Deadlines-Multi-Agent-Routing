import java.time.LocalDateTime;

public class ZoneParallelExecutionVerification {
    public static void main(String[] args) {
        JobAssignmentSystem system = new JobAssignmentSystem();

        system.addTask(10, 20, 50, 60, LocalDateTime.now().plusMinutes(60));
        system.addTask(12, 22, 52, 62, LocalDateTime.now().plusMinutes(70));
        system.addTask(100, 120, 140, 160, LocalDateTime.now().plusMinutes(80));
        system.addTask(105, 125, 145, 165, LocalDateTime.now().plusMinutes(90));

        system.registerDrone(5, 15, 1, 10);
        system.registerDrone(20, 30, 1, 10);
        system.registerDrone(95, 115, 2, 10);
        system.registerDrone(108, 128, 2, 10);

        system.divideZonesAutomatically();
        String report = system.getParallelZoneExecutionReport();

        if (!report.contains("Zone Token") || !report.contains("parallel")) {
            throw new IllegalStateException("Parallel zone execution report was not generated correctly.");
        }

        System.out.println(report);
        System.out.println("Zone parallel execution verification passed.");
    }
}
