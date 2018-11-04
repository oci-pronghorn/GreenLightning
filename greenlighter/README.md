# GreenLighter (A Maven Archetype for GreenLightning projects)

## What you will need before you start:
#### [Java 8](https://docs.oracle.com/javase/8/docs/technotes/guides/install/install_overview.html) 
#### [Maven](https://maven.apache.org/install.html)
- which downloads and manages the libraries and APIs needed to get the Grove device working.
#### [Git](https://git-scm.com/)
- which clones a template Maven project with the necessary dependencies already set up.
#### [PuTTY](http://www.putty.org/)
- which allows you to interact and control your device

## Starting Your Own Project
```
mvn archetype:generate -DarchetypeGroupId=com.ociweb -DarchetypeArtifactId=greenlighter -DarchetypeVersion=1.0.11
```
The terminal now asks for:

```groupID```: type in  *com.ociweb* then press Enter

```ArtifactID```: type in name of your project, lower case and use - as needed. then press Enter

```version: 0.0.1-SNAPSHOT ```: Ignore, Press Enter

```package: com.ociweb ```: Ignore, Press Enter

```mainClass:``` Type in proper case class name

```Y:```  :  Type *Y*, press Enter


This will create a folder named after your project, which includes all the project files. Let’s call our project *ProjectXYZ*.  
If you’re working from Terminal, open up the file  “ProjectXYZ”/src/main/java/com/ociweb/IoTApp.java . You can start implementing the project code from here. 

If you’re using an IDE, open up the created Maven project - *ProjectXYZ* and start working from IoTApp.java

Once you’re done with the implementation, open your project folder in terminal and type 
```
mvn install
```
.. to build the project. This will create a .jar file named ProjectXYZ.jar in the **/target** folder (note that there are other .jar files  in **/target**, but we don’t have to worry about those). This jar is executable and contains all its needed dependencies. Transfer this .jar file to your device and use the command 
```
java -jar ProjectXYZ.jar 
```
.. to execute it.
 
### Importing the Maven project in Eclipse
Select File -> Import

Click on "Exisiting Maven Projects" under Maven, then click "Next"

Click "Browse" and select the directory (folder) under your project that contains the "src" folder as well as a "pom.xml" 
file.

Click "finish"

### Importing the Maven project in NetBeans 
Select File -> Open Project

Browse to the directory (folder) under your project that contains the "src" folder as well as a "pom.xml" 
file.

Click "Open Project"

Note: In Netbeans, instead of typing ```mvn install```, you can also build your project by clicking "Build".

### Importing the Maven project in IntelliJ
Select File -> Open.

Browse to the directory (folder) under your project that contains the "src" folder as well as a "pom.xml" 
file.

Click "OK".

The import will be performed automatically.

### Building the project
Open your project folder in the terminal of your choosing and type
```

### Signing your project with your own key:
```
We have provided a key to sign your project; however, if you would like to use a key of your own choosing, you can do so by replacing the ocikeystore.jks file located in **projectname/src/main/resources/certificate/ocikeystore.jks** with your own .jks key or one provided from an official Certificate Authority (CA). 

.. To create your own jks file, simply navigate to your jdk_version folder on your computer and locate the bin folder. Within the folder you will see a .exe file called keytool. While at the CMD of this folder type: "keytool -genkey -alias my_certificate_alias -keyalg RSA -keysize 4096 -keystore keystore.jks" press enter and you will prompted with a wizard to fill in the rest. Take note of your password and alias. Next, using whichever IDE you prefer; navigate to the POM file within the main folder and edit the properties section at the top to fit the values of your own key. Replace the alias with the alias you just used as well as the password. Change the last part of the **keystore.path** section from "../ocikeystore.jks" to the name of your "../keystore.filetype". 

.. If you are using a Certificate Authority (CA) to sign your project simply put the information related to the CA in the properties of the POM file. The project will then automatically be signed when you perform a mvn install in the following step.

mvn install
```
.. to build the project. This will create a .jar file named ProjectXYZ.jar in the **/target** folder (note that there are other .jar files  in **/target**, but we don’t have to worry about those). This jar is executable and contains all its needed dependencies. 

### Importing and running the project to your device
After succesfully building the project, ```cd``` into the **/target** folder. Now, use 
```
scp ProjectXYZ.jar username@servername:
``` 
This will send the jar file to your RaspberryPi. You can also send the jar file to a specifc location by adding the file path after the colon. For example, if your username was "pi", the server name was "raspberry" and you wanted to add the .jar file to your Projects folder, the command would look like this ..
```
scp ProjectXYZ.jar pi@raspberry:/Projects/
```

Once the project is your device, use PuTTY to connect to your device. In PuTTY, if needed, ```cd``` into the folder containging your .jar file and then use the following command on your device..
```
java -jar ProjectXYZ.jar
```
.. to execute it. To exit the app at any time, press Ctrl+c.

