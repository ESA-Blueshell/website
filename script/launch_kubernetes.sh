kubectl apply -f ../api-gateway/api-gateway-deployment.yaml
kubectl apply -f ../blog-parser/blog-parser-deployment.yaml
kubectl apply -f ../blog-service/blog-service-deployment.yaml
kubectl apply -f ../email-parser/email-praser-deployment.yaml
kubectl apply -f ../eureka-server/eureka-server-deployment.yaml
kubectl apply -f ../event-parser/event-parser-deployment.yaml
kubectl apply -f ../file-service/file-service-deployment.yaml
kubectl apply -f ../social-media-service/social-media-service-deployment.yaml
kubectl apply -f ../telemetry-service/telemetry-service-deployment.yaml

kubectl apply -f ../script/mariadb-deployment
kubectl apply -f ../script/rabbitmq-deployment

kubectl apply -f ../api-gateway/api-gateway-service.yaml
kubectl apply -f ../blog-parser/blog-parser-service.yaml
kubectl apply -f ../blog-service/blog-service-service.yaml
kubectl apply -f ../email-parser/email-praser-service.yaml
kubectl apply -f ../eureka-server/eureka-server-service.yaml
kubectl apply -f ../event-parser/event-parser-service.yaml
kubectl apply -f ../file-service/file-service-service.yaml
kubectl apply -f ../social-media-service/social-media-service-service.yaml
kubectl apply -f ../telemetry-service/telemetry-service-service.yaml

kubectl apply -f ../script/mariadb-service.yaml
kubectl apply -f ../script/rabbitmq-service.yaml
