# scheduler-platform (Step 0.1 - full)

This zip contains **all** classes and build files from Step 0.1.

## Eclipse/ Spring Tool Suite import
You already generated the Gradle wrapper, so do:

1. In STS/Eclipse:
 - File -> Import -> Gradle -> Existing Gradle Project
 - Select this folder
 - Finish
2. If Eclipse metadata isn't generated automatically, run:
 - `gradlew eclipse`
 - then refresh the project.

## Build
- Windows: `gradlew.bat build`
- Linux/Mac: `./gradlew build`


## Documentation
See [docs/README.md](docs/README.md)


## Modules
- `scheduler-domain`: pure domain model (definitions, instances, state machines, DAG)


## API Docs
Run `scheduler-api` then open `/swagger-ui/index.html`.
