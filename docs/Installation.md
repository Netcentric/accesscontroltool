# Installation

The AC Tool [content package](https://jackrabbit.apache.org/filevault) contains the tool itself and is available from [the Maven Central repository](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/). 

In addition, there needs to be another package containing the project/platform specific [AC Tool configuration](Configuration.md) which is typically part of the regular project source code and released along with it (e.g. as `myproject-permissions`).

Due to the different nature of on-premise and cloud service infrastructure setup, the Maven setup for installation of the two packages is slightly different (while the AC Tool configuration as such is compatible with both setups and can usually easily be lifted from on-premise to the cloud service).

## Installation AEM On-Premise

For AEM 6.4/6.5 use the following approach:

* Use a [container package](https://jackrabbit.apache.org/filevault/packagetypes.html) that contains all project-related packages as sub package (hence in `/etc/packages`)
* Include the `accesscontroltool-package` *without* the `cloud` classifier (this leverages the [Sling Installer Provider Install Hook](https://sling.apache.org/documentation/bundles/installer-provider-installhook.html) )
* Include the permissions package containing the project/platform specific [AC Tool configuration](Configuration.md)
 * Ensure this permissions package declares the [dependency](#declaring-the-dependency) correctly


Use the package with the following Maven coordinates:

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-package</artifactId>
```

without the `cloud` classifier.


## Installation AEM Cloud Service

For AEMaaCS use the use the following approach:

* Use a  container package that contains all project-related packages as embedded artifacts (hence in `/apps`)
* Include the `accesscontroltool-package` *with* the `cloud` classifier (this contains the startup hook bundle for installation and AEMaaCS specific configuration)
* Include the permissions package containing the project/platform specific [AC Tool configuration](Configuration.md)
* Ensure this permissions package declares the [dependency](#declaring-the-dependency) correctly


Use the package with the group id, artifact id and classifier `cloud`:
 
```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-package</artifactId>
    <classifier>cloud</classifier>
```
 
## Declaring the Dependency

The permissions package containing the project/platform specific [AC Tool configuration](Configuration.md) should declare a dependency to the AC tool software package as follows: 

```
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.jackrabbit</groupId>
            <artifactId>filevault-package-maven-plugin</artifactId>
            <configuration>
                <properties>
                    <!-- declare the install hook also for cloud to ease local development with the AEM SDK (when installed in the actual cloud service the startup hook is used automatically instead, even with this configuration --> 
                    <installhook.actool.class>biz.netcentric.cq.tools.actool.installhook.AcToolInstallHook</installhook.actool.class>
                </properties>
                <!-- package type mixed is required due to the install hook -->
                <packageType>mixed</packageType>
                <dependencies>
                    <dependency>
                        <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
                        <artifactId>accesscontroltool-package</artifactId>
                        <!-- without classifier leave our for on-premise -->
                        <classifier>cloud</classifier>
                    </dependency>
                </dependencies>
            </configuration>
        </plugin>
    </plugins>
</build>
<dependencies>
    <dependency>
        <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
        <artifactId>accesscontroltool-package</artifactId>
        <!-- without classifier leave our for on-premise -->
        <classifier>cloud</classifier>
        <!-- version should come from parent/dependency management -->
        <type>zip</type>
        <scope>provided</scope>
    </dependency>
</dependencies>
```


# Oak Index for rep:ACL

To retrieve all ACLs in the system, an Apache Oak index for node type `rep:ACL` is 

* required for versions < 2.4.0 (otherwise the performance degrades significantly)
* beneficial for large installations for versions >= 2.4.0 (see [#386](https://github.com/Netcentric/accesscontroltool/issues/386), most installations will be fine without index)

You can get the content package containing the [index definition for AEM 6.5 (LTS)](http://jackrabbit.apache.org/oak/docs/query/indexing.html#index-definitions) via [Maven Central](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-oakindex-package/) with the coordinates  

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
```

To deploy it make sure to either embed it in your container package or install it manually.

**AEMaaCS ships with a suitable index since 2026.7.27293 (see [#894](https://github.com/Netcentric/accesscontroltool/issues/894#issuecomment-5239713446)), therefore it is no longer necessary to install an additional index package for AEMaaCS.**

For older AEMaaCS versions you can still download an explicit index definition package with the coordinates

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
    <classifier>cloud</classifier>
```

However, it is recommended to rather update to a newer AEMaaCS and remove the explicit index from your container package.

