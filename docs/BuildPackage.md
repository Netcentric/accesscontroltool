# Building the packages from source
## Requirements

Building the ACTool requires Java 7 and Maven 3.2.

From version 1.9.0 on Jackrabbit API 2.8 is required

## Build package

A full build of ACTool can be executed by running:

```
mvn clean install
```

This command will create an AEM/CQ Package as a ZIP file inside accesscontroltool-package/target called accesscontroltool-package-<VERSION>.zip.

The package can be installed using the AEM Package Manager.
If you run AEM on http://localhost:4502 you can also install from the command line using this command:

```
mvn -PautoInstallPackage install
```

## AEM6.x/Oak

The `oakindex-package` contains an optimized Oak index to cover all queries being issued by the Access Control Tool. To build (and optionally deploy) the content-package use the Maven profile oakindex. This package is only compatible with Oak and even there it is optional (as it will only speed up queries).

To use the package, run all commands with profile `oakindex`, e.g.
 ```
mvn clean install -Poakindex
 ```

Output will be accesscontroltool-oakindex-package/target/accesscontroltool-oakindex-package-<VERSION>.zip