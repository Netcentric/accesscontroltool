# Deploying ACLs

## Installation process

During the installation all groups defined in the groups section of the configuration file get created in the system. In a next step all ACEs configured in the ACE section get installed in CRX. Any old ACEs regarding these groups not contained anymore in the config but in the repository gets automatically purged thus no old or obsolete ACEs stay in the system and any manual change of ACEs regarding these groups get reverted thus ensuring a defined state again. ACEs not belongig to those groups in the configuration files remain untouched. So after the installation took place all groups and the corresponding ACEs exactly as defined in the configuration(s) are installed on the system.

If at any point during the installation an exception occurs, no changes get persisted in the system. This prevents ending up having a undefined state in the repository.

During the installation a history containing the most important events gets created and persisted in CRX for later examination.

The following steps are performed:

1. All AC entries are removed from the repository which refer to an authorizable being mentioned in the YAML configuration file (no matter to which path those entries refer).
1. All authorizables being mentioned in the YAML configuration get created (if necessary, i.e. if they do no exist yet).
1. All AC entries generated from the YAML configuration get persisted in the repository. If there are already existing entries for one path (and referring to another authorizable) those are not touched. New AC entries are added at the end of the list. All new AC entries are sorted, so that the Deny entries are listed above the Allow entries. Since the AC entry nodes are evaluated bottom-to-top this sorting order leads to less restrictions (e.g. for a user which is member of two groups where one group sets a Deny and the other one sets an Allow, this order ensures that the allow has a higher priority).

    
### Installation Hook

To automatically install ACEs and Authorizable being defined in YAML files with the package containing the YAML files one can leverage the Content Package Install Hook mechanism.
To enable that on a package being created with Maven through the content-package-maven-plugin one can enable the installation hook via 

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

The ```*.yaml``` files are installed directly from the package content and respect the same runmode semantics as described above. 

Although it is not necessary that the YAML files are covered by the filter rules of the ```filter.xml```, this is recommended practice. That way you can see afterwards in the repository which YAML files have been processed. However if you would not let the ```filter.xml``` cover your YAML files, those files would still be processed by the installation hook.
    
## UploadListenerService

The Upload Listener Service allows to configure the NodeEvent Listener. In the OSGi console it can be enabled/disabled furthermore the listener path can also be configured here.

If enabled each new upload of a projectspecific configuration file triggers the AceService. Before a new installation starts, a dump (groups & ACLs) gets created which get stored under the backups-node.

## Curl calls

Trigger the 'execute' method of the AC service

```
curl -sS --retry 1 -u ${CQ_ADMINUSER}:${CQ_ADMINPW} -X POST "http://${CQ_SERVER}:${CQ_PORT}/system/console/jmx/biz.netcentric.cq.tools.actool:id='ac+installation'/op/execute/"
```

