FROM openjdk:10-jre-slim
COPY target/${artifactId}-${version}.jar app.jar

ENTRYPOINT ["java", "-server", "-XX:+UseNUMA", "-XX:+UseParallelGC", "-XX:+AggressiveOpts", "-jar", "app.jar"]
