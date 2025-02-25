# Use an official Maven image to build the application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and download the dependencies
ARG SERVICE_NAME
COPY ./pom.xml /app/parent/pom.xml
COPY ./${SERVICE_NAME}/pom.xml /app/${SERVICE_NAME}/pom.xml

COPY ./Common/pom.xml /app/parent/Common/pom.xml
COPY ./Common/src /app/parent/Common/src
RUN mvn install -f /app/parent/Common/pom.xml -DskipTests

COPY ./APIGateway/pom.xml /app/parent/APIGateway/pom.xml
COPY ./BlogService/pom.xml /app/parent/BlogService/pom.xml
COPY ./EmailParser/pom.xml /app/parent/EmailParser/pom.xml
COPY ./EventParser/pom.xml /app/parent/EventParser/pom.xml
COPY ./SocialMediaService/pom.xml /app/parent/SocialMediaService/pom.xml
COPY ./Telemetry/pom.xml /app/parent/Telemetry/pom.xml

RUN mvn install -f /app/parent/pom.xml -DskipTests

# Copy the source code and build the application
WORKDIR /app/${SERVICE_NAME}
RUN mvn dependency:go-offline
COPY ./${SERVICE_NAME}/src ./src
RUN mvn install -DskipTests

# Use an official OpenJDK image to run the application
FROM eclipse-temurin:21-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the built jar file from the build stage
ARG SERVICE_NAME
COPY --from=build /app/${SERVICE_NAME}/target/*.jar app.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]