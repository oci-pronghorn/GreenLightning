# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a simple use of the addStartupListener method.

Demo code: 

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/Startup.java

When executed, the above code will send the string ```"Hello, this message will display once at start"`` as soon as the program begins running. NOTE: while it was not performed here, 
if a transducer uses a startup method, then the startup listener of the transducer will execute before the startup method in the behavior class. Also, if multiple transducers use startup a method, do not worry about an order, it will be done automatically.
