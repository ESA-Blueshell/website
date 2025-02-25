# Use an official Maven image to build the application
FROM maven:3.9.9-eclipse-temurin-17 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the pom.xml and download the dependencies
COPY ./pom.xml /app/parent/pom.xml
COPY ./EventParser/pom.xml /app/EventParser/pom.xml
COPY ./Common/target/Common-1.0-SNAPSHOT.jar /app/Common/target/Common-1.0-SNAPSHOT.jar

COPY ./APIGateway/pom.xml /app/parent/APIGateway/pom.xml
COPY ./BlogService/pom.xml /app/parent/BlogService/pom.xml
COPY ./Common/pom.xml /app/parent/Common/pom.xml
COPY ./EmailParser/pom.xml /app/parent/EmailParser/pom.xml
COPY ./EventParser/pom.xml /app/parent/EventParser/pom.xml
COPY ./SocialMediaService/pom.xml /app/parent/SocialMediaService/pom.xml
COPY ./Telemetry/pom.xml /app/parent/Telemetry/pom.xml

RUN mvn install:install-file \
    -Dfile=/app/Common/target/Common-1.0-SNAPSHOT.jar \
    -DgroupId=io.github.jakstepn \
    -DartifactId=common \
    -Dversion=1.0-SNAPSHOT \
    -Dpackaging=jar

RUN mvn install -f /app/parent/pom.xml

# Copy the source code and build the application
WORKDIR /app/EventParser
RUN mvn dependency:go-offline
COPY ./EventParser/src ./src
RUN mvn install -DskipTests

# Use an official OpenJDK image to run the application
FROM eclipse-temurin:17

# Set the working directory in the container
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build /app/EventParser/target/*.jar app.jar
COPY --from=build /app/Common/target/Common-1.0-SNAPSHOT.jar /Common/target/Common-1.0-SNAPSHOT.jar

# Expose the port the application runs on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]