# Build stage: Use JDK to compile the application
FROM eclipse-temurin:17-jdk-alpine AS build
WORKDIR /app

# Copy Maven wrapper and project definition files
COPY .mvn .mvn
COPY mvnw pom.xml ./

# FIX: Grant execute permission to the Maven wrapper (fixes exit code 126)
RUN chmod +x mvnw
# Download dependencies (this layer is cached to speed up future builds)
RUN ./mvnw dependency:go-offline

# Copy the source code and build the JAR file, skipping tests for speed
COPY src ./src
RUN ./mvnw package -DskipTests

#------------------------------------------------------------------

# Run stage: Use a smaller JRE image for the final container
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Copy only the built JAR file from the build stage
COPY --from=build /app/target/*.jar app.jar

# FIX: Render requires the app to listen on port 10000
ENV SERVER_PORT=10000
EXPOSE 10000

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
