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
