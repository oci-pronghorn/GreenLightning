# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a basic demo for using a ```I2CListener()```.

Demo code:

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/I2CListener.java

Behavior class:

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/I2CListenerBehavior.java

This class is a simple demonstration of how to use the ```I2CListener()```. This demonstration uses the I2C ADC, which is an analog to I2C converter. Also, take note that inside of the I2CListenerBehavior, there is also a startup listener which is necessary to begin an I2CListener. 
