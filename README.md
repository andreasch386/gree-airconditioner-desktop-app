To run directly:
Execute maven goal spring-boot:run under the ui module.

To package to an exe installer:
1. mvn clean install (in the parent module)
2. mvn -pl ui jpackage:jpackage -f pom.xml
3. this will create an installer in the ui/target/installer directory.
4. This will install it in Program Files directory where you will find the exe. 
