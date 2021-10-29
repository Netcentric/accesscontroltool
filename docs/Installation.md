# Installation

The AC Tool [content package](https://jackrabbit.apache.org/filevault) contains the tool itself and is available from [the Maven Central repository](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/). 

In addition, there needs to be another package containing the project/platform specific [AC Tool configuration](Configuration.md) which is typically part of the regular project source code and released along with it (e.g. as `myproject-permissions`).

Due to the different nature of on-premise and cloud service infrastructure setup, the maven setup for installation of the two packages is slightly different (while the AC Tool configuration as such is compatible to both and can usually easily be lifted from on-premise to the cloud service).

## Installation AEM On-Premise

For AEM 6.4/6.5 use the following appraoch:

* Use a complete package that contains all project-related packages as sub package (hence in `/etc/packages`)
* Include the `accesscontroltool-package` *without* the `cloud` classifier (this has the [Sling Installer Provider Install Hook](https://sling.apache.org/documentation/bundles/installer-provider-installhook.html) in place)
* Include the permissions package containing the project/platform specific [AC Tool configuration](Configuration.md)
 * Ensure this permissions package declares the [dependency](#declaring-the-dependency) correctly


Use the package with the GAV 

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-package</artifactId>
```

without the `cloud` classifier.


## Installation AEM Cloud Service

For AEMaaCS use the use the following appraoch:

* Use an `all` package that contains all project-related packages as embedds (hence in `/apps`)
* Include the `accesscontroltool-package` *with* the `cloud` classifier (this contains the startup hook bundle for installation)
* Include the permissions package containing the project/platform specific [AC Tool configuration](Configuration.md)
 * Ensure this permissions package declares the [dependency](#declaring-the-dependency) correctly


Use the package with the GAV and classifier `cloud`:
 
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

You can get the content package containing the [index definition](http://jackrabbit.apache.org/oak/docs/query/indexing.html#index-defnitions) via [Maven Central](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-oakindex-package/) with the coordinates  

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
```
(for AEM Classic/On Premise) or

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
    <classifier>cloud</classifier>
```
(for AEM as a Cloud Service)

Install it afterwards e.g. via AEM's package manager.