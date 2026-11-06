# Project index

## Main application and simulation sources

- `Demo.java` - saved demonstration scenario.
- `job.java` - interactive task-assignment entry point.
- `JobAssignmentSystem.java`, `Task.java`, `Drone.java`, `Zone.java` - task, drone, and zone models and assignment workflow.
- `RouteCalculator.java`, `SystemIntegration.java` - route feasibility and integration logic.
- `MCHRouteGUI.java`, `MCHRouteGUI_WithAStar.java` - Swing route visualizations.
- `singletoken.java` - single-token comparison implementation.
- `PathBenchmark.java` - path benchmark support.

## Verification sources

Java verification programs are kept in `tests/`; they are provided as source and are not part of the default demo command.

## Evidence and documentation

- `docs/project-report.pdf` - full academic report.
- `benchmarks/` - 100-run and 1,000-run PRS-versus-A* workbooks.
- `examples/` - sample input and selected scenario outputs.
