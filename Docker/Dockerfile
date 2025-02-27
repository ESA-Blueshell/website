# Use an official Maven image to build the application
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build-parent

# Set the working directory in the container
WORKDIR /app

# Copy the local Maven repository
COPY .m2 /root/.m2

# Copy the parent POM and module POMs
COPY ./pom.xml /app/parent/pom.xml
COPY ./Common/pom.xml /app/parent/Common/pom.xml
COPY ./APIGateway/pom.xml /app/parent/APIGateway/pom.xml
COPY ./BlogService/pom.xml /app/parent/BlogService/pom.xml
COPY ./Common/pom.xml /app/parent/Common/pom.xml
COPY ./EmailParser/pom.xml /app/parent/EmailParser/pom.xml
COPY ./EventParser/pom.xml /app/parent/EventParser/pom.xml
COPY ./SocialMediaService/pom.xml /app/parent/SocialMediaService/pom.xml
COPY ./Telemetry/pom.xml /app/parent/Telemetry/pom.xml

# Download dependencies and build the parent project
RUN mvn dependency:go-offline -f ./parent/pom.xml
RUN mvn install -DskipTests -f ./parent/pom.xml -nsu -o

# Use an official Maven image to build the Common module
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build-common
WORKDIR /app

# Copy the local Maven repository from the previous stage
COPY --from=build-parent /root/.m2 /root/.m2

# Copy the Common module
COPY ./Common/pom.xml ./Common/pom.xml
COPY ./Common/src ./Common/src

# Download dependencies and build the Common module
RUN mvn dependency:go-offline -f ./Common/pom.xml
RUN mvn install -DskipTests -f ./Common/pom.xml -nsu -o

# Use an official Maven image to download dependencies for the service
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build-dependency
WORKDIR /app
ARG SERVICE_NAME

# Copy the service POM and local Maven repository
COPY ./${SERVICE_NAME}/pom.xml .
COPY --from=build-common /root/.m2 /root/.m2

# Download dependencies for the service
RUN mvn dependency:go-offline

# Use an official Maven image to build the service
FROM maven:3.9.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
ARG SERVICE_NAME
ARG PORT

# Copy the service POM, source code, and local Maven repository
COPY ./${SERVICE_NAME}/pom.xml .
COPY --from=build-dependency /root/.m2 /root/.m2
COPY ./${SERVICE_NAME}/src ./src

# Build the service
RUN mvn install -DskipTests -o -nsu

# Use an official OpenJDK image to run the application
FROM eclipse-temurin:21-alpine

# Set the working directory in the container
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/target/*.jar app.jar

# Expose the port the application runs on
EXPOSE ${PORT} 5005

# Run the application
ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005", "-jar", "app.jar"]