FROM sbtscala/scala-sbt:eclipse-temurin-17.0.15_6_1.11.3_3.3.6 AS build

WORKDIR /app

COPY build.sbt ./
COPY project ./project/
COPY src ./src/
RUN sbt clean compile assembly

FROM eclipse-temurin:17-jre-ubi9-minimal

WORKDIR /app
COPY --from=build /app/target/scala-*/**.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
CMD []
