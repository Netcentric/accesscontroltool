Access Control Tool for Adobe Experience Manager
================================================

The Access Control Tool for Adobe Experience Manager (ACTool) is a tool that simplifies the specification and deployment of complex [Access Control Lists in AEM] (http://docs.adobe.com/docs/en/cq/current/administering/security.html#Access%20Control%20Lists%20and%20how%20they%20are%20evaluated).
Instead of building a content package with actual ACL nodes you can write simple configuration files and deploy them with your content packages.

Features:
* compatible with AEM 6.x and CQ 5.6.1
* easy-to-read Yaml configuration file format
* run mode support
* automatic installation with install hook
* cleans obsolete ACL entries when configuration is changed
* ACLs can be dumped
* stores history of changes

# Requirements

The ACTool requires Java 7 and AEM 6.0 - 6.2 or CQ 5.6.1 (min. SP2).

# Installation

The package is available via [Maven](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/). Install it e.g. via CRX package manager.

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-package</artifactId>
```

## AEM6.x/Oak

In case you run AEM 6 with Oak (required as of 6.1) we recommend to install the Oak index package.
It will speed up installation of ACLs.

You can get the ZIP file via [Maven](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/). Install it e.g. via CRX package manager.

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
```

# Configuration of ACL entries

You need to setup [Yaml configuration files](docs/Configuration.md) to specify your users, groups and ACL entries.

## Advanced configuration options

The ACTool also supports [loops, conditional statements and permissions for anonymous](docs/AdvancedFeatures.md).

# Installation of ACL entries

There are multiple options to [install the ACL entries](docs/Deploy.md) (e.g. install hook, JMX and upload listener) to your target system.

## JMX interface

The [JMX interface](docs/Jmx.md) provides utility functions such as installing and dumping ACLs or showing the history. 

## History Service

A history object collects messages, warnings, and also an exception in case something goes wrong. This history gets saved in CRX under /var/statistics/achistory. The number of histories to be saved can be configured in the history service.

## Building the packages from source

If needed you can [build the ACTool yourself](docs/BuildPackage.md).

## License

The ACTool is licensed under the [Eclipse Public License - v 1.0](LICENSE.txt).