import java.time.LocalDateTime;
import java.util.*;

public class ZonePriorityAssignmentVerification {
    public static void main(String[] args) {
        JobAssignmentSystem system = new JobAssignmentSystem();

        JobAssignmentSystem demoScenario = Demo.createFullScenario(50, 4, 3, 10.0);
        for (Zone zone : demoScenario.getZones()) {
            if (zone.getDroneIds().size() != 3) {
                throw new IllegalStateException("Expected 3 drones in zone " + zone.getZoneId() + " but found " + zone.getDroneIds().size());
            }
        }

        for (int i = 0; i < 12; i++) {
            double pickupX = 100 + (i * 40 % 300);
            double pickupY = 120 + ((i * 25) % 220);
            double deliveryX = 150 + (i * 35 % 300);
            double deliveryY = 200 + ((i * 20) % 220);
            LocalDateTime deadline = LocalDateTime.now().plusMinutes(40 + i);
            system.addTask(pickupX, pickupY, deliveryX, deliveryY, deadline);
        }

        system.divideZonesAutomatically(3);
        for (Zone zone : system.getZones()) {
            double centerX = zone.getCenterX();
            double centerY = zone.getCenterY();
            for (int i = 0; i < 3; i++) {
                double angle = 2 * Math.PI * i / 3.0;
                double startX = centerX + 8.0 * Math.cos(angle);
                double startY = centerY + 8.0 * Math.sin(angle);
                system.registerDrone(startX, startY, zone.getZoneId(), 10.0);
            }
        }

        system.assignJobsToDrones();

        for (Zone zone : system.getZones()) {
            List<Task> prioritized = zone.getTasksPrioritizedByDeadline();
            List<Task> firstThree = prioritized.size() >= 3 ? prioritized.subList(0, 3) : prioritized;
            Set<Integer> assignedDrones = new HashSet<>();
            for (Task task : firstThree) {
                if (task.getAssignedDroneId() < 0) {
                    throw new IllegalStateException("Expected top-priority task " + task.getTaskId() + " to have a zone drone assigned.");
                }
                if (!zone.getDroneIds().contains(task.getAssignedDroneId())) {
                    throw new IllegalStateException("Task " + task.getTaskId() + " was assigned to drone " + task.getAssignedDroneId() + " outside zone " + zone.getZoneId());
                }
                assignedDrones.add(task.getAssignedDroneId());
            }
            if (assignedDrones.size() < Math.min(3, firstThree.size())) {
                throw new IllegalStateException("Expected first " + Math.min(3, firstThree.size()) + " tasks in zone " + zone.getZoneId() + " to use distinct drones.");
            }
        }

        System.out.println("ZonePriorityAssignmentVerification passed.");
    }
}
