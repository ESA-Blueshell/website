# How to run
To launch all applications run
`./run.sh`.

### Requirements
- Docker
- Java 21

It is recommended to prepare a local Maven repository
under `~/.m2` directory. In Jetbrains IntelliJ IDEA you can do it
from the Maven panel. Local repository is copied to the main directory of the
project, where it is used by the Dockerfile to build the image quicker.

### How to connect debugger to a running container

To debug a specific module like (_APIGateway_) open IntelliJ:
1. Run/Debug Configurations
2. Add new configuration (+ on the top left)
3. Remote JVM Debug
4. Set the port to 5005 (For all containers 5005 is exposed by default [debug docker-compose is coming in the next commit])
5. Change Classpath to the module you would like to debug
