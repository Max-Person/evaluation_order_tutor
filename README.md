## SWRLAPI based parsing c language, build evaluation order DAG and fault reasons determining

### Building and Running

To build and run this project you must have the following items installed:

+ [Java 17](https://www.oracle.com/java/technologies/downloads/#java17)
+ A tool for checking out a [Git](http://git-scm.com/) repository
+ Apache's [Maven](http://maven.apache.org/index.html)
+ [Max-Person/its_QuestionGen](https://github.com/Max-Person/its_QuestionGen) and its dependencies installed

Build it with Maven:

    mvn clean install

On build completion, your local Maven repository will contain generated ```ontology_cpp_parsing-${version}.jar```
and ```ontology_cpp_parsing-${version}-jar-with-dependencies.jar``` files.
The ```./target``` directory will also contain these JARs.

You can then run the application as follows:

    mvn exec:java

![Java CI with Maven](https://github.com/ShadowGorn/ontology_cpp_parsing/workflows/Java%20CI%20with%20Maven/badge.svg)
