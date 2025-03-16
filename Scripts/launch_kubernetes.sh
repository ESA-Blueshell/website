kubectl apply -f ../APIGateway/apigateway-deployment.yaml
kubectl apply -f ../BlogService/blogservice-deployment.yaml
kubectl apply -f ../EmailParser/emailparser-deployment.yaml
kubectl apply -f ../EventParser/eventparser-deployment.yaml
kubectl apply -f ../SocialMediaService/socialmediaservice-deployment.yaml
kubectl apply -f ../Telemetry/telemetry-deployment.yaml

kubectl apply -f ../APIGateway/apigateway-service.yaml
kubectl apply -f ../BlogService/blogservice-service.yaml
kubectl apply -f ../EmailParser/emailparser-service.yaml
kubectl apply -f ../EventParser/eventparser-service.yaml
kubectl apply -f ../SocialMediaService/socialmediaservice-service.yaml
kubectl apply -f ../Telemetry/telemetry-service.yaml
