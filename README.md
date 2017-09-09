# FogLight-API
API examples of oeFogLight features
## What It Is ##
FogLight is a Java 8 functional API for embedded systems that's built on top of [GreenLightning](https://github.com/oci-pronghorn/GreenLightning), a small footprint, garbage free compact 1 Java web server and message routing platform, 

FogLight is...
- Fast - Built on top of GreenLightning, FogLight is a garbage-free, lock-free and low latency way to talk directly to hardware.
- Simple - Taking advantage of the latest Java 8 APIs, FogLight has a clean and fluent set of APIs that make it easy to learn and apply with minimal training.
- Secure - By taking advantage of the compile-time graph validation system, all FogLight applications can be compiled and compressed to a point where injecting malicious code into the final production JAR would prove difficult, if not impossible.

## How It Works ##
Every FogLight application starts with an `FogApp` implementation which initializes the `FogRuntime` by defining various hardware connections and behaviors for handling state changes in those connections.  

## What You Need Before You Start:
### Hardware
- [Raspberry Pi](https://www.raspberrypi.org/)
- [GrovePi+ Board](https://www.dexterindustries.com/shop/grovepi-board/)
### Software
- [Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html)
- [Maven](https://maven.apache.org/install.html)
- [Git](https://git-scm.com/)
### Hardware Examples
- [Foglight-Grove](https://github.com/oci-pronghorn/FogLight-Grove/blob/master/README.md)
## Starting Your Maven Project
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)
## Information and Demos 
- AnalogListener
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/AnalogListener/README.md)
- AnalogTransducer
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/AnalogTransducer/README.md)
- CommandChannel
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/CommandChannel/README.md)
- DeclareConnections
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/DeclareConnections/README.md)
- DigitalListener
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/DigitalListener/README.md)
- HTTPClient
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/HTTPClient/README.md)
- HTTPServer
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/HTTPServer/README.md)
- I2CListener
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/I2CListener/README.md)
- MQTTClient
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/MQTTClient/README.md)
- PubSub
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/PubSub/README.md)
- PubSubStructured
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/PubSubStructured/README.md)
- SerialListener
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/SerialListener/README.md)
- Shutdown
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/Shutdown/README.md)
- Startup
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/Startup/README.md)
- StateMachine
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/StateMachine/README.md)
- Timer
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/Timer/README.md)
- Transducer
  - [demo](https://github.com/oci-pronghorn/FogLight-API/blob/master/TransducerDemo/README.md)
