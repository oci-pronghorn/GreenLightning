### Signing your project with your own key:
```
We have provided a key to sign your project; however, if you would like to use a key of your own choosing, you can do so by replacing the ocikeystore.jks file located in **projectname/src/main/resources/certificate/ocikeystore.jks** with your own .jks key or one provided from an official Certificate Authority (CA). 

.. To create your own jks file, simply navigate to your jdk_version folder on your computer and locate the bin folder. Within the folder you will see a .exe file called keytool. While at the CMD of this folder type: "keytool -genkey -alias my_certificate_alias -keyalg RSA -keysize 4096 -keystore keystore.jks" press enter and you will prompted with a wizard to fill in the rest. Take note of your password and alias. Next, using whichever IDE you prefer; navigate to the POM file within the main folder and edit the properties section at the top to fit the values of your own key. Replace the alias with the alias you just used as well as the password. Change the last part of the **keystore.path** section from "../ocikeystore.jks" to the name of your "../keystore.filetype". 

.. If you are using a Certificate Authority (CA) to sign your project simply put the information related to the CA in the properties of the POM file. The project will then automatically be signed when you perform a mvn install in the following step.

mvn install
```
.. to build the project. This will create a .jar file named ProjectXYZ.jar in the **/target** folder (note that there are other .jar files  in **/target**, but we don’t have to worry about those). This jar is executable and contains all its needed dependencies. 
