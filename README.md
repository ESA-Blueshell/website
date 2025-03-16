# How to run
To launch all applications run
`./run.sh [-f]`. Flag `-f` is optional and forces the update of the current Dockerfile in respective service folders.

### Requirements
- Docker
- Java 21

#### For Local Kubernetes Development
- Minikube (https://minikube.sigs.k8s.io/docs/)

### Kubernetes Launch

To launch local cluster with the entire application run:
`./run-kubernetes.sh [-f]`. Flag `-f` is optional
and will force the update of the current Dockerfile, kubernetes deployment and kubernetes service
configuration files in respective service folders (recommended when changes to these files were made in the templates located in the `Docker` foler).

Script `run-kubernetes.sh` will launch the local cluster with the entire application as well as a proxy through which 
the minikube dashboard is visible. It will automatically open in the browser. It can be used to administer
the cluster (deployments, pods, services). The proxy is running in the current terminal window untill stopped.

#### To reach the APIGateway through URL
1. Kubernetes deployment must be running.
2. Kubernetes service (LoadBalancer) must be pending (should be by default).
3. Create a tunnel to the LoadBalancer with `minikube tunnel`.
4. The APIGateway can be reached through the URL `http://localhost:80`.

NOTE: Docker must be running.

#### To remove existing cluster
To remove the existing cluster run:
`minikube stop` and `minikube delete --all`.

### How to connect debugger to a running container

To debug a specific module container like (_APIGateway_) open IntelliJ:
1. Run/Debug Configurations
2. Add new configuration (+ on the top left)
3. Remote JVM Debug
4. Set the port to e.g. 5005 (Full port list below)
5. Change Classpath to the module you would like to debug

NOTE: Container with the module you would like to debug must be running.

#### Debug ports
If invalid look in docker-compose-base.yml for the port mapped to 5005.
- APIGateway: 5005
- BlogService: 5006
- EmailParser: 5007
- EventParser: 5008
- SocialMediaService: 5009
- Telemetry: 5010