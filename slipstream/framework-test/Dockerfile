FROM maven:3.6.0-jdk-11 as maven
   
COPY pom.xml pom.xml
COPY src src
RUN mvn clean install -q

FROM azul/zulu-openjdk-alpine:11.0.1
COPY --from=maven /target/greenlightning-test.jar app.jar

#records to our log all the known network settings on the host connection 
CMD sysctl -a && java -server -Xmx26g -XX:+UseNUMA -jar app.jar

