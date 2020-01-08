# Applying ACLs

The following steps are performed:

1. All AC entries are removed from the repository which refer to an authorizable (user/group) being mentioned in the YAML configuration file (no matter to which path those entries refer).
1. All authorizables being mentioned in the YAML configuration get created (if necessary, i.e. if they do no exist yet).
1. All AC entries generated from the YAML configuration get persisted in the repository. If there are already existing entries for one path (and referring to another authorizable) those are not touched. New AC entries are added at the end of the list. All new AC entries are sorted, so that the Deny entries are listed above the Allow entries. Since the AC entry nodes are evaluated bottom-to-top this sorting order leads to less restrictions (E.g. a user might be member of two groups where one group sets a Deny and the other one sets an Allow. This order ensures that the Allow has a higher priority.).

If at any point during the installation an exception occurs, no changes get persisted in the system. This prevents ending up having a undefined state in the repository.

During the installation a history containing the most important events gets created and persisted in CRX for later examination.
The following section explain how you can trigger the installation of ACLs.

    
## Installation Hook

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

Although it is not necessary that the YAML files are covered by the filter rules of the `filter.xml`, this is recommended practice. That way you can see afterwards in the repository which YAML files have been processed. However if you would not let the `filter.xml` cover your YAML files, those files would still be processed by the installation hook.

The installation takes place in phase "PREPARE" by default, i.e. before any other content from the package has been installed. Optionally you can make the hook kick in in phase "INSTALLED" (i.e. after the content has been installed) by additionally setting the package property `actool.atInstalledPhase` to `true`. This is helpful if the initial content on which you want to set ACEs is created via classical package content (instead of inline yaml `initialContent`). That is only properly supported since AEM 6.4.2 (for details look at [issue 287](https://github.com/Netcentric/accesscontroltool/issues/287)) and since ACTool 2.4.0.

An install hook is ignored in the cloud because the startup hook is to be used for that use case (the package property `actool.forceInstallHookInCloud` can be used force excution).

## Web Console

A Felix Web Console UI is available at "Main" -> "AC Tool". The web console provides fewer operations as JMX, but for manual testing it provides better usability for applying config changes continuously. 

## JMX

See [JMX apply() method](Jmx.md).

## Automatically during startup

When using the composite node store [1] with immutable containers (and immutable /apps and /libs paths), the AC tool can be automatically triggered upon startup. To enable that trigger, use the package `accesscontroltool-cloud-package` instead of `accesscontroltool-package`.

### Curl calls

Trigger the 'apply' method of the AC JMX service through HTTP Post

```
curl -sS --retry 1 -u ${CQ_ADMINUSER}:${CQ_ADMINPW} -X POST "http://${CQ_SERVER}:${CQ_PORT}/system/console/jmx/biz.netcentric.cq.tools:type=ACTool/op/apply/"
```
    
## Upload Listener Service

The Upload Listener Service allows to automatically apply the configuration upon changes in the yaml files in CRX. It registers a JCR listener per configured path in `AC Tool Installation Service` and applies the corresponding changes with a configured delay (to aggregate multiple change events into installation). By default it is disabled.

NOTE: Usually it is better to rely on the install hook and manual executions via the user interface when needed.

<img src="images/upload-listener.png">

<img src="images/installation-service.png">




