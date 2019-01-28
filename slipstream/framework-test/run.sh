mvn clean install

java -XX:+UseNUMA -Dtelemetry.port=8098 -Dhost=127.0.0.1 -Xmx3500m -jar ./target/greenlightning-test.jar 
