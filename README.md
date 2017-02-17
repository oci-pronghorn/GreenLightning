# GreenLightning
Fast small footprint HTTP server in Java


This is early days for the project but we have enough here to prove out the core design. 
Please consider sponsoring continued work on this BSD open source project. 

Drop a note to info@ociweb.com if you would like to set up a meeting to talk about Green Lightning.  
What featrues are most important to you?


When running tests with a small 30 byte file I was able to to hit the following numbers
for unencrypted http calls on a 4 core box.  

The test makes 16K simultaneous (in flight) requests of 32 connections. Many more permutations are left to still be tested.

Green Lightning:   1,000K RPS (saturated 1Gb connection)  (in large mode)

Green Lightning:   500K RPS (in small mode mode between 60-160MB of memory)

NGINX:             260K RPS

Netty:             160K RPS


The build server:
https://pronghorn.ci.cloudbees.com/job/GreenLightning/

The code on GitHub:
https://github.com/oci-pronghorn/GreenLightning

To build it just run:
mvn install
then run the go.sh shell file to start up the demo web page
review the go.sh to see how its called.

--t is for turning on or off TLS (https) support, keys are built in cant change yet

--s is the folder holding the static site pages,  these can not be changed while its running
     note if this points to the index.html file it will be used as the default for the domain. 
     
--l is for running in large mode where it allocates many GB for performance
    with -l False  (the small mode) it is still much more performant than any other 
    server I could find.
    
--h what host to run this on (eg ip) if not provided it will guess from network settings.

--p what port to run on the default is 8080 even when TLS is turned on.


When running small mode on a compact 1 JVM the app will only use about 60MB.
When running small mode on a normal JVM the app will use about 160MB.