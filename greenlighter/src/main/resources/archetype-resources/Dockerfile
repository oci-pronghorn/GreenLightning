FROM openjdk:11-jdk-alpine
COPY target/${artifactId}-${version}.jar /app.jar

ENTRYPOINT ["java", "-server", "-XX:+UseNUMA", "-XX:+UseParallelGC", "-XX:+AggressiveOpts", "-jar", "/app.jar"]

#docker build -t ${artifactId}:latest .
#docker history
#docker run -d --name demo-default -p 8080:8080 -p 8098:8098 ${artifactId}:latest
