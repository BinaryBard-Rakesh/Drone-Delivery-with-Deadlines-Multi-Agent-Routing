import java.time.LocalDateTime;
import java.util.Random;

public class RandomTaskComparisonVerification {
    public static void main(String[] args) {
        JobAssignmentSystem system = new JobAssignmentSystem();
        Random random = new Random(42);

        for (int i = 0; i < 50; i++) {
            double pickupX = 10 + random.nextDouble() * 180;
            double pickupY = 10 + random.nextDouble() * 180;
            double deliveryX = 10 + random.nextDouble() * 180;
            double deliveryY = 10 + random.nextDouble() * 180;
            LocalDateTime deadline = LocalDateTime.now().plusMinutes(30 + random.nextInt(180));
            system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
        }

        system.registerDrone(10, 10, 1, 10);
        system.registerDrone(20, 18, 1, 10);
        system.registerDrone(28, 24, 1, 10);
        system.registerDrone(110, 12, 2, 10);
        system.registerDrone(126, 20, 2, 10);
        system.registerDrone(144, 26, 2, 10);
        system.registerDrone(10, 140, 3, 10);
        system.registerDrone(24, 154, 3, 10);
        system.registerDrone(34, 168, 3, 10);

        system.divideZonesAutomatically(3);
        system.assignJobsToDrones();

        String centralizedReport = system.getCentralizedTaskTable();
        String zoneReport = system.getZoneTable();
        String multiTokenReport = system.getMultiTokenExecutionTable();
        String singleTokenReport = system.getSingleTokenExecutionTable();
        String comparisonReport = system.getMultiTokenVsSingleTokenComparison();

        if (!centralizedReport.contains("CENTRALIZED TASK TABLE")) {
            throw new IllegalStateException("Centralized task table was not generated.");
        }
        if (!zoneReport.contains("ZONE DISTRIBUTION")) {
            throw new IllegalStateException("Zone division table was not generated.");
        }
        if (!multiTokenReport.contains("MULTI-TOKEN") || !singleTokenReport.contains("SINGLE-TOKEN")) {
            throw new IllegalStateException("Multi-token or single-token execution tables were not generated.");
        }
        if (!comparisonReport.contains("COMPARISON")) {
            throw new IllegalStateException("Comparison report was not generated.");
        }

        System.out.println("Random task comparison verification passed.");
    }
}
