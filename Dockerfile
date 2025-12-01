# --- Stage 1: Build the Application ---
# We use a specific Maven image that supports Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy project files into the container
COPY pom.xml .
COPY src ./src

# Build the JAR file inside the container
RUN mvn clean package -DskipTests

# --- Stage 2: Run the Application ---
# We use a lightweight Java 21 runtime
FROM eclipse-temurin:21-jre
WORKDIR /app

# Copy the JAR we built in Stage 1
# This wildcard *.jar automatically grabs your correct filename
COPY --from=build /app/target/*.jar app.jar

# Expose port 8080 (Required for Render)
EXPOSE 8080

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]