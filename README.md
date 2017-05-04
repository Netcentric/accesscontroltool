Access Control Tool for Adobe Experience Manager
================================================

The Access Control Tool for Adobe Experience Manager (AC Tool) simplifies the specification and deployment of complex [Access Control Lists in AEM](http://docs.adobe.com/docs/en/cq/current/administering/security.html#Access%20Control%20Lists%20and%20how%20they%20are%20evaluated).
Instead of [existing solutions](docs/Comparison.md) that build e.g. a content package with actual ACL nodes you can write simple configuration files and deploy them with your content packages.

Features:
* easy-to-read Yaml configuration file format
* run mode support
* automatic installation with install hook
* cleans obsolete ACL entries when configuration is changed
* ACLs can be exported
* stores history of changes
* ensured order of ACLs
* built-in expression language to reduce rule duplication

See also our talk at [AdaptTo 2016](https://adapt.to/2016/en/schedule/ac-tool.html)

# Requirements

The AC Tool requires Java 7 and AEM 6.1 or above (use v1.x for older versions)

# Installation

The package is available via [Maven](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/). Install it e.g. via CRX package manager.

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-package</artifactId>
```

## AEM6.x/Oak

In case you run AEM 6 with Oak (required as of 6.1) we recommend to install the Oak index package.
It will speed up installation of ACLs.

You can get the ZIP file via [Maven](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-oakindex-package/). Install it e.g. via CRX package manager.

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
```

# Migration to AC Tool

You can easily migrate to AC Tool following [four simple steps](docs/Migration.md).

# Configuration of ACL entries

You need to setup [Yaml configuration files](docs/Configuration.md) to specify your users, groups and ACL entries. See also the [best practices](docs/BestPractices.md) for hints on structuring.

There are also some [advanced configuration options](docs/AdvancedFeatures.md) supported such as loops, conditional statements and permissions for anonymous.

# Applying the ACL entries

There are multiple options to [apply the ACL entries](docs/ApplyConfig.md) (e.g. install hook, JMX and upload listener) to your target system.

# JMX interface

The [JMX interface](docs/Jmx.md) provides utility functions such as installing and dumping ACLs or showing the history. 

# History service

A history object collects messages, warnings, and also an exception in case something goes wrong. This history gets saved in CRX under /var/statistics/achistory. The number of histories to be saved can be configured in the history service.

# Building the packages from source

If needed you can [build the AC Tool yourself](docs/BuildPackage.md).

# License

The AC Tool is licensed under the [Eclipse Public License - v 1.0](LICENSE.txt).
