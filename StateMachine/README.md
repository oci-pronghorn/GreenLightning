# What you will need before you start:
-[Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 

-[Maven](https://maven.apache.org/install.html), which downloads and manages the libraries and APIs needed to get the Grove device working.

-[Git](https://git-scm.com/), which clones a template Maven project with the necessary dependencies already set up.

# Starting your Maven project: 
[Starting a mvn project](https://github.com/oci-pronghorn/FogLighter/blob/master/README.md)

# Example project:

The following sketch will demonstrate a simple use of the ```StateChangeListener```.

Demo code:
Main Class

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/StateMachine.java

Behavior classes

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/StateChangeBehavior.java

#### ERROR:  could not read file ./src/main/java/com/ociweb/oe/foglight/api/TimingBehavior.java



These classes are a basic demo of how to use the ```StateChangeListener``` method. In the main class, a stop light is simulated with 3 different states, ```Go```, ```Caution```, and ```Stop```. In the ```declareConnections``` section, the stop light is initialized to the ```Stop``` state to begin with. If a state is initialized there, you use a ```changeState()``` in a StartupListener as the two will clash when starting the program, so you must use one or the other. In the ```TimeBehavior``` class, a TimeListener is being used to change the state the state of the stop light. Every 5 seconds, the state is changed to the next state in the progression. In the ```StateChangeBehavior``` class, there is a StateChangeListener. Whenever it hears a change in state, it will print the new states color and will return true.
