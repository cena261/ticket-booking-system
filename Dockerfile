FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY ticket-domain/pom.xml ticket-domain/
COPY ticket-infrastructure/pom.xml ticket-infrastructure/
COPY ticket-application/pom.xml ticket-application/
COPY ticket-controller/pom.xml ticket-controller/
COPY ticket-start/pom.xml ticket-start/
RUN ./mvnw -B -q dependency:go-offline -DskipTests || true
COPY . .
RUN ./mvnw -B -DskipTests clean package

FROM eclipse-temurin:21-jre AS runtime
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /build/ticket-start/target/ticket-app-exec.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
