# Build the jar. Tests run with ./mvnw verify outside Docker (they need Docker themselves).
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
# Download dependencies in their own layer, so code changes don't download them again.
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -o package -DskipTests \
    && cp target/tickgeek-*.jar app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 10001 tickgeek
WORKDIR /app
COPY --from=build /app/app.jar app.jar
USER tickgeek
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
