# Building the packages from source
## Requirements

Building the AC Tool requires Java 11+. Maven is implicitly downloaded via the [Maven Wrapper](https://maven.apache.org/wrapper/index.html).

## Build package

A full build of AC Tool can be executed by running:

```
./mvnw clean install
```

This command will create an AEM/CQ Package as a ZIP file inside accesscontroltool-package/target called accesscontroltool-package-<VERSION>.zip.

The package can be installed using the AEM Package Manager.
If you run AEM on http://localhost:4502 you can also install from the command line using this command:

```
./mvnw -PautoInstallPackage install
```

## AEM6.x/Oak

The `oakindex-package` contains an optimized Oak index to cover all queries being issued by the Access Control Tool. To build (and optionally deploy) the content-package use the Maven profile oakindex. This package is only compatible with Oak and even there it is optional (as it will only speed up queries).

To use the package, run all commands with profile `oakindex`, e.g.
 ```
./mvnw clean install -Poakindex
 ```

Output will be accesscontroltool-oakindex-package/target/accesscontroltool-oakindex-package-<VERSION>.zip