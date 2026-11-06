FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
COPY src/ src/
RUN mvn -q package dependency:copy-dependencies -DskipTests
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /src/target/classes/ classes/
COPY --from=build /src/target/dependency/ lib/
COPY public/ public/
RUN mkdir -p /data
EXPOSE 8080
CMD ["java", "-cp", "classes:lib/*", "demo.Main"]
