# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:
 
Demo code:
.include "./src/main/java/com/ociweb/oe/greenlightning/api/HTTPServer.java"
Behavior class:
.include "./src/main/java/com/ociweb/oe/greenlightning/api/RestBehaviorEmptyResponse.java"
.include "./src/main/java/com/ociweb/oe/greenlightning/api/RestBehaviorLargeResponse.java"
.include "./src/main/java/com/ociweb/oe/greenlightning/api/RestBehaviorSmallResponse.java"
This class is a simple demonstration of HTTPServer. HTTP Server will listen for an HTTP Client to send it a request. It will try to process the request, however, if it is unable to process it at that time, it will be reattempted later
