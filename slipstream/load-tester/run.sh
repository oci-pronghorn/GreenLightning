mvn clean install

java -Xmx28g -jar ./target/load-tester.jar -h 127.0.0.1 -p 8080 -c 2097152 -r /json -b 6 -t 128 -i 512 -m false
