FROM maven:3.9-eclipse-temurin-8 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package


FROM eclipse-temurin:8-jre

WORKDIR /app
RUN groupadd --system app && useradd --system --gid app --home-dir /app app

COPY --from=build /workspace/target/heyee-comments-*.jar /app/app.jar

USER app
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
