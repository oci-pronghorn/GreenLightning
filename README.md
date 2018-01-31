# GreenLightning
GreenLightning is a performant light-weight microservice runtime that can execute garbage-free and lock-free in a Java compact-1 JVM. HTTP and MQTT are built in with easy and security-first APIs.

The framework is based on a declarative event driven actor model. Your resulting code ends up being smaller in complexity and easier to maintain. Operational logic such as thread synchronization, exception handling, and null object conditions are not only removed from the business logic, but is handled by the framework.

In other words you can easily write multithreaded asynchronous logic without having to schedule a single task or lock a single block of memory!

Another stand-out feature of GreenLightning is live fine-grained telemetry/operational information published via the built in web-server. You can collect instantaneous metrics such as CPU% on a class level and data throughput without a specialized build and deployment. The information is available in production.

Starter Project Instructions:
https://github.com/oci-pronghorn/GreenLighter

Sample Projects:
https://github.com/oci-pronghorn/GreenLightning-API
https://github.com/oci-pronghorn/GreenLightning-Examples

The code on GitHub:
https://github.com/oci-pronghorn/GreenLightning

The build server:
https://pronghorn.ci.cloudbees.com/job/GreenLightning/

Performance Comparisons:
https://github.com/oci-pronghorn/GreenLoader

