# Applying ACLs

* [General Installation](#general-installation)
* [Installation in AEM as a Cloud Service](#installation-in-aem-as-a-cloud-service)
* [Installation Methods](#installation-methods)
  * [Installation Hook](#installation-hook)
  * [Web Console](#web-console)
  * [Touch UI](#touch-ui)
  * [JMX](#jmx)
  * [Startup Hook](#startup-hook)
  * [Upload Listener Service](#upload-listener-service)

<!--- This table of contents has been generated with https://github.com/ekalinin/github-markdown-toc#gh-md-toc -->

## General Installation

The following steps are performed during the installation of authorizables and ACEs:

1. All AC entries are removed from the repository which refer to an authorizable (user/group) being mentioned in the YAML configuration file (no matter to which path those entries refer).
1. All authorizables being mentioned in the YAML configuration get created (if necessary, i.e. if they do no exist yet).
1. All AC entries generated from the YAML configuration get persisted in the repository. If there are already existing entries for one path (and referring to another authorizable) those are not touched. New AC entries are added at the end of the list. All new AC entries are sorted, so that the Deny entries are listed above the Allow entries. Since the AC entry nodes are evaluated bottom-to-top this sorting order leads to less restrictions (E.g. a user might be member of two groups where one group sets a Deny and the other one sets an Allow. This order ensures that the Allow has a higher priority.).

If at any point during the installation an exception occurs, no changes get persisted in the system. This prevents ending up having a undefined state in the repository.

During the installation a history containing the most important events gets created and persisted in CRX for later examination.

## Installation in AEM as a Cloud Service

Due to usage of a [composite node store](http://jackrabbit.apache.org/oak/docs/nodestore/compositens.html) the installation is slightly more complex in AEMaaCS

1. During Maven build in Cloud Manager the ACLs are applied like outlined above via [Installation Hook](#installation-hook)
2. Afterwards all mutable content is discarded (authorizables in `/home` and ACEs in mutable content)
3. During deployment in Cloud Manager the authorizables and ACEs in mutable locations of the repository are installed on the target mutable node store via the [Startup Hook](#startup-hook)

Theoretically step 1 is only necessary if ACEs for immutable content are required, but it is recommended to always use the combination of Installation Hook and Startup Hook for AEMaaCS to be able to use the same package also for installation with a local AEM SDK instance.

## Installation Methods

The following section explain how you can trigger the installation of ACLs.
  
### Installation Hook

You can automatically install ACEs and authorizables defined in YAML files within a package using the Content Package Install Hook mechanism.
If you use the content-package-maven-plugin for building the package, enable the installation hook via: 

```
<plugin>
  <groupId>com.day.jcr.vault</groupId>
  <artifactId>content-package-maven-plugin</artifactId>
  <configuration>
    <properties>
      <installhook.actool.class>biz.netcentric.cq.tools.actool.installhook.AcToolInstallHook</installhook.actool.class>
    </properties>
  </configuration>
</plugin>
```

If you would rather use the filevault-package-maven-plugin for building the package, enable the installation hook via:

```
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <configuration>
        <properties>
            <installhook.actool.class>biz.netcentric.cq.tools.actool.installhook.AcToolInstallHook</installhook.actool.class>
        </properties>
    </configuration>
</plugin>
```

The `*.yaml` files are installed directly from the package content and respect the [run mode semantics](Configuration.md). Otherwise there is no limitation, so the YAML files will be picked up from anywhere in the package (as long as the parent node does not contain a `.` followed by one or multiple not matching run modes).

If you wish to limit which YAML files are picked up by the installation hook, you can add one or more regular expressions as package properties that start with the prefix `actool.installhook.configFilesPattern`. If the path of a file matches any of the provided patterns, it will be picked up by the installation hook:

```
<plugin>
    <groupId>org.apache.jackrabbit</groupId>
    <artifactId>filevault-package-maven-plugin</artifactId>
    <configuration>
        <properties>
            <installhook.actool.class>biz.netcentric.cq.tools.actool.installhook.AcToolInstallHook</installhook.actool.class>
            <actool.installhook.configFilesPattern.groups>/apps/myapp/acl/groups.*</actool.installhook.configFilesPattern.groups>
            <actool.installhook.configFilesPattern.users>/apps/myapp/acl/users.*</actool.installhook.configFilesPattern.users>
        </properties>
    </configuration>
</plugin>
```

Although it is not necessary that the YAML files are covered by the filter rules of the `filter.xml`, this is recommended practice. That way you can see afterwards in the repository which YAML files have been processed. However if you would not let the `filter.xml` cover your YAML files, those files would still be processed by the installation hook.

The installation takes place in phase "PREPARE" by default, i.e. before any other content from the package has been installed. Optionally you can make the hook kick in in phase "INSTALLED" (i.e. after the content has been installed) by additionally setting the package property `actool.atInstalledPhase` to `true`. This is helpful if the initial content on which you want to set ACEs is created via classical package content (instead of inline yaml `initialContent`). That is only properly supported since AEM 6.4.2 (for details look at [issue 287](https://github.com/Netcentric/accesscontroltool/issues/287)) and since ACTool 2.4.0.

An install hook is ignored in the cloud because the startup hook is to be used for that use case (the package property `actool.forceInstallHookInCloud` can be used force excution).

**Notice:** Packages with install hooks can only be installed by admin users (compare with [JCRVLT-427](https://issues.apache.org/jira/browse/JCRVLT-427))! This is either user with id `admin`, `system` or every member of group `administrators`.

### Web Console

A Felix Web Console UI is available at "Main" -> "AC Tool". The web console provides fewer operations as JMX, but for manual testing it provides better usability for applying config changes continuously. 

### Touch UI

The same interface as available via Web Console is also available via Touch UI at `Tools -> Security -> Netcentric AC Tool` if you are admin on the instance. When using [AEM as a Cloud Service](https://www.adobe.com/marketing/experience-manager/cloud-service.html), the console will be available if you are in the admin group for the AEM env as set up in [adminconsole](https://adminconsole.adobe.com/).

### JMX

See [JMX apply() method](Jmx.md).

**Curl call**

Trigger the 'apply' method of the AC JMX service through HTTP Post

```
curl -sS --retry 1 -u ${CQ_ADMINUSER}:${CQ_ADMINPW} -X POST "http://${CQ_SERVER}:${CQ_PORT}/system/console/jmx/biz.netcentric.cq.tools:type=ACTool/op/apply/"
```

### Startup Hook

When using the [Sling Feature Model](https://sling.apache.org/documentation/development/feature-model.html), the AC tool is automatically triggered upon startup. The startup hook is auto-activated for the case the [Sling OSGi installer](https://sling.apache.org/documentation/bundles/osgi-installer.html) is not present which is usually the case when using the Sling Feature Model. The behaviour can be adjusted via OSGi config `AC Tool Startup Hook` (PID `biz.netcentric.cq.tools.actool.startuphook.impl.AcToolStartupHookServiceImpl`) but the default is usually correct.

The startup hook requires the `AC Tool Installation Service` (PID `biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl`) to be configured correctly, i.e. its configuration path must point to the nodes containing the `YAML` files.
<img src="images/installation-service.png">

The AC Tool handles a [composite node store](https://jackrabbit.apache.org/oak/docs/nodestore/compositens.html) repository correctly (it will automatically only run with paths that are not ready-only). To avoid overhead for the case a configuration has already been applied, an MD5 checksum is created over all configuration files and the configuration is only applied for the case the checksum has changed.

The startup hook is the **only way** to automatically apply ACLs during startup and is therefore necessary for installing authorizables and applying ACEs on mutable content in [AEM as a Cloud Service](https://www.adobe.com/marketing/experience-manager/cloud-service.html).
This mechanism should be combined with the [Installation Hook](#installation-hook) to also have ACEs on immutable content applied (and to be able to use the same package with a local AEM SDK).

Use [Touch UI](ApplyConfig.md#touch-ui) to validate your results.

### Upload Listener Service

The Upload Listener Service allows to automatically apply the configuration upon changes in the yaml files in CRX. It registers a JCR listener per configured path in `AC Tool Installation Service` and applies the corresponding changes with a configured delay (to aggregate multiple change events into installation). By default it is disabled.

NOTE: Usually it is better to rely on the install hook and manual executions via the user interface when needed.

<img src="images/upload-listener.png">

The upload listener service requires the `AC Tool Installation Service` (PID `biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl`) to be configured correctly, i.e. its configuration path must point to the nodes containing the `YAML` files.
<img src="images/installation-service.png">



