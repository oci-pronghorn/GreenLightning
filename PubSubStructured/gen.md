# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:
 
The following sketch will demonstrate a basic demo for using a PubSub Structured.
 
Demo code:

.include "./src/main/java/com/ociweb/oe/foglight/api/PubSubStructured.java"

Behavior class:

.include "./src/main/java/com/ociweb/oe/foglight/api/KickoffBehavior.java"

.include "./src/main/java/com/ociweb/oe/foglight/api/DecrementValueBehavior.java"

.include "./src/main/java/com/ociweb/oe/foglight/api/ThingBehavior.java"


This class is a simple demonstration of PubSub Structured. While similar to the normal PubSub, PubSub Structured is meant for larger messages instead of just simpler ones. 
