FROM sbtscala/scala-sbt:eclipse-temurin-17.0.15_6_1.11.2_3.7.1 AS build

WORKDIR /app

COPY build.sbt ./
COPY project ./project/

RUN sbt update

COPY src ./src/
RUN sbt clean assembly

FROM eclipse-temurin:17-jre-ubi9-minimal

WORKDIR /app

COPY --from=build /app/target/scala-*/srvc-io-assembly-*.jar ./app.jar

ENTRYPOINT ["java", "-jar", "app.jar"]
CMD []
