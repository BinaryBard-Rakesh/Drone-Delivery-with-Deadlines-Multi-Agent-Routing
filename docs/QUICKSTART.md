# Quickstart

## Requirements

- A Java Development Kit installed and available on your command line.

## Compile the Java sources

Run these commands from the repository root:

```bash
javac -d build src/*.java
```

## Run a demo

```bash
java -cp build Demo
```

To open the interactive task-assignment menu:

```bash
java -cp build job
```

To launch a routing visualization:

```bash
java -cp build MCHRouteGUI
```

The routing GUI asks for a compatible map file. Map files are not included in this repository. The source code uses the default package, so preserve the supplied filenames when compiling.

## Supporting files

- Example input and console outputs are under `examples/`.
- The report and project overview are under `docs/`.
- Route-length benchmark spreadsheets are under `benchmarks/`.
