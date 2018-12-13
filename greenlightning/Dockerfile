FROM maven:3.6.0-jdk-11 as build
COPY pom.xml pom.xml
COPY src src
RUN mvn install -q

FROM azul/zulu-openjdk-alpine:11.0.1

COPY   demoSite /usr/share/greenlightning/html
COPY --from=build /target/gl.jar gl.jar

ENTRYPOINT [ "sh", "-c" "java -server --illegal-access=warn -XX:+UseNUMA -jar gl.jar" ]
CMD [] 

#NOTE: you should replace 'latest' with the specific version you want
#docker build -t gl:latest .
#docker history
#docker run -d --name gl-instance -p 8080:8080 -p 8098:8098 gl:latest

