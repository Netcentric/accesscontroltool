[![Maven Central](https://maven-badges.herokuapp.com/maven-central/biz.netcentric.cq.tools.accesscontroltool/accesscontroltool/badge.svg)](https://maven-badges.herokuapp.com/maven-central/biz.netcentric.cq.tools.accesscontroltool/accesscontroltool)
[![License](https://img.shields.io/badge/License-EPL%201.0-red.svg)](https://opensource.org/licenses/EPL-1.0)
[![Build Status](https://github.com/netcentric/accesscontroltool/actions/workflows/maven.yml/badge.svg?branch=develop)](https://github.com/Netcentric/accesscontroltool/actions/workflows/maven.yml)
[![SonarCloud Status](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_accesscontroltool&metric=alert_status)](https://sonarcloud.io/dashboard?id=Netcentric_accesscontroltool)
[![SonarCloud Coverage](https://sonarcloud.io/api/project_badges/measure?project=Netcentric_accesscontroltool&metric=coverage)](https://sonarcloud.io/component_measures/metric/coverage/list?id=Netcentric_accesscontroltool)

Access Control Tool for Adobe Experience Manager
================================================

The Access Control Tool for Adobe Experience Manager (AC Tool) simplifies the specification and deployment of complex [Access Control Lists in AEM](http://docs.adobe.com/docs/en/cq/current/administering/security.html#Access%20Control%20Lists%20and%20how%20they%20are%20evaluated).
Instead of existing solutions that build e.g. a content package with actual ACL nodes you can write simple configuration files and deploy them with your content packages. See [comparison to other approaches](docs/Comparison.md) for a comprehensive overview.

Features:

* easy-to-read Yaml configuration file format
* run mode support
* automatic installation with install hook
* cleans obsolete ACL entries when configuration is changed
* ACLs can be exported
* stores history of changes
* ensured order of ACLs
* built-in expression language to reduce rule duplication

See also our talk at [adaptTo() 2016](https://adapt.to/2016/en/schedule/ac-tool.html)

# Requirements

The AC Tool requires **Java 8 and AEM 6.4** or above (use v2.x for older AEM versions which runs on Java 7 and AEM 6.1 SP1 or above) for on-premise installations. Since v2.5.0 **[AEM as a Cloud Service](https://www.adobe.com/marketing/experience-manager/cloud-service.html)** is supported, see [Startup Hook](https://github.com/Netcentric/accesscontroltool/blob/develop/docs/ApplyConfig.md#startup-hook) for details.

It is also possible to run the AC Tool on **Apache Sling 11** or above (ensure system user `actool-service` has `jcr:all` permissions on root). When using the AC Tool with Sling, actions in ACE definitions and encrypted passwords cannot be used. To use the `externalId` attribute, ensure bundle `oak-auth-external` installed (not part of default Sling distribution).

# Installation

The [content package](https://jackrabbit.apache.org/filevault) is available from [the Maven Central repository](https://repo1.maven.org/maven2/biz/netcentric/cq/tools/accesscontroltool/accesscontroltool-package/) 

For quick ad hoc testing and getting to know the AC Tool, the easiest is to

* Install the latest version via AEM's package manager
* Create a sample YAML file in CRXDE (e.g. `/apps/actool-test/test.yaml`)
* Apply this config using the UI (see [User Interface](#user-interface) below)

For properly integrating the AC Tool in your own deployment package see [Installation](docs/Installation.md).

# Configuration of the AC Tool

You need to setup [Yaml configuration files](docs/Configuration.md) to specify your users, groups and ACL entries. See also the [best practices](docs/BestPractices.md) for hints on structuring.

There are also some [advanced configuration options](docs/AdvancedFeatures.md) supported such as loops, conditional statements and permissions for anonymous.

# User Interface

There is a Felix Web Console plugin (at `/system/console/actool`) as well as a Touch UI console (at `/mnt/overlay/netcentric/actool/content/overview.html`) to apply configurations and to inspect previous executions of the tool. Additionally there is a [JMX interface](docs/Jmx.md) for some advanced use cases.

# Applying AC Tool Configurations

Best practice is to apply AC Tool Configurations using the install hook (or startup hook for Cloud Service) during your project's software package installation. See [applying the ACL entries](docs/ApplyConfig.md) for a full list of options.

# Migration to AC Tool

You can easily migrate to AC Tool following [four simple steps](docs/Migration.md).

# Questions

If you have any questions which are still answered after reading the [documentation](docs/) feel free to raise them in the [discussion forum](https://github.com/Netcentric/accesscontroltool/discussions).

# Contributions

Contributions are highly welcome in the form of [issue reports](https://github.com/Netcentric/accesscontroltool/issues), [pull request](https://docs.github.com/en/free-pro-team@latest/github/collaborating-with-issues-and-pull-requests/creating-a-pull-request-from-a-fork) or providing help in our [discussion forum](https://github.com/Netcentric/accesscontroltool/discussions).

# Building the packages from source

If needed you can [build the AC Tool yourself](docs/BuildPackage.md).

# License

The AC Tool is licensed under the [Eclipse Public License - v 1.0](LICENSE.txt).
