# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

-[Mosquitto](https://mosquitto.org/download/), which is an MQTT message broker

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:
 
The following sketch will demonstrate a basic demo for using a MQTT.
 
Demo code:

.include "./src/main/java/com/ociweb/oe/foglight/api/MQTTClient.java"

Behavior class:

.include "./src/main/java/com/ociweb/oe/foglight/api/behaviors/TimeBehavior.java"

.include "./src/main/java/com/ociweb/oe/foglight/api/behaviors/IngressBehavior.java"

.include "./src/main/java/com/ociweb/oe/foglight/api/behaviors/EgressBehavior.java"


This class is a simple demonstration of MQTT (Message Queue Telemetry Transport). A lightweight messaging protocal, it was inititially designed for constrained devices and low-bandwidth, high-latency or unreliable networks. This demo uses Mosquitto as a message broker, which means that the messages that are published will go through Mosquitto, which will send them to and subsrcibers of the topic. 
