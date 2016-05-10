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

You can get the ZIP file via Maven. Install it e.g. via CRX package manager.

```
    <groupId>biz.netcentric.cq.tools.accesscontroltool</groupId>
    <artifactId>accesscontroltool-oakindex-package</artifactId>
```


# Configuration File Format

For better human readability and easy editing the ACL configuration files use the YAML format.


## Overall structure a of an AC configuration file

<img src="docs/images/configuration-file-structure.png">

Every configuration file comprises a group section where groups and their membership to other groups get defined and a ACE section where all ACEs in the repository regarding these groups get defined. These ACE definitions are again written under the respective group id. The group of an ACE definition in each configuration file has to match a group which is also defined in the same file. Groups which are contained in the isMemberOf property within a group definition have either to be defined in another configuration file or already be installed in the system on which the installation takes place.

## Configuration of groups

A authorizable record in the configuration file starts with the principal id followed by some indented data records containing properties like name/description and group membership:

```
[Groupd Id]
     - name: groupname (optional, if empty groupd id is taken)
     - isMemberOf: comma separated list of other groups
     - members: comma separated list of groups that are member of this group
     - description: (optional, description)
     - path: (optional, path of the group in JCR)
     - migrateFrom: (optional, a group name assigned member users are taken over from, since v1.7)     
```

Example

```
isp-editor      
   - isMemberOf: fragment-restrict-for-everyone,fragment-allow-nst
   - members: editor
```

If the isMemberOf property of a group contains a group which is not yet installed in the repository, this group gets created and its rep:members property gets filled accordingly. if another configuration gets installed having a actual definition for that group the data gets merged into the already existing one.

The members property contains a list of groups where this group is added as isMemberOf.

The property 'migrateFrom' allows to migrate a group name without loosing their members (members of the group given in migrateFrom are taken over and the source=old group deleted afterwards). This property is only to be used temporarily (usually only included in one released version that travels all environments, once all groups are migrated the config should be removed). If not set (the default) nothing happens. If the property points to a group that does not exist (anymore), the property is ignored.

## Configuration of users

In general it is best practice to not generate regular users by the AC Tool but use other mechanism (e.g. LDAP) to create users. However, it can be useful to create system users (e.g. for replication agents or OSGi service authentiation) or test users on staging environments.

Users can be configured in the same way as groups in the **user_config** section. The following properties are different to groups (all optional):

* the attribute "members" cannot be used (for obvious reasons)
* the attribute "password" can be used for preset passwords (not allowed for system users)
* the boolean attribute isSystemUser is used to create system users in AEM 6.1
* the attribute profileContent allows to provide docview xml that will reset the profile to the given structure after each run (since v1.8.2)
* the attribute preferencesContent allows to provide docview xml that will reset the preferences node to the given structure after each run (since v1.8.2)

## Configuration of ACEs

The configurations are done per principal followed by indented informations which comprise of config data which represents the settings per ACE. This data includes

property | comment | required
--- | --- | ---
path | a node path. Wildcards `*` are possible. e.g. assuming we have the language trees de and en then `/content/*./test` would match: `/content/de/test` and `/content/en/test` (mandatory). If an asterisk is contained then the path has to be written inside single quotes (`'...'`) since this symbol is a functional character in YAML. | yes
permission | the permission (either `allow` or `deny`) | yes
actions | the actions (`read,modify,create,delete,acl_read,acl_edit,replicate`). Reference: <http://docs.adobe.com/docs/en/cq/current/administering/security.html#Actions> | either actions or privileges need to be present; also a mix of both is possible
privileges | the privileges (`jcr:read, rep:write, jcr:all, crx:replicate, jcr:addChildNodes, jcr:lifecycleManagement, jcr:lockManagement, jcr:modifyAccessControl, jcr:modifyProperties, jcr:namespaceManagement, jcr:nodeTypeDefinitionManagement, jcr:nodeTypeManagement, jcr:readAccessControl, jcr:removeChildNodes, jcr:removeNode, jcr:retentionManagement, jcr:versionManagement, jcr:workspaceManagement, jcr:write, rep:privilegeManagement`). References: <http://jackrabbit.apache.org/oak/docs/security/privilege.html> <http://www.day.com/specs/jcr/2.0/16_Access_Control_Management.html#16.2.3%20Standard%20Privileges> | either actions or privileges need to be present; also a mix of both is possible
repGlob |A repGlob expression like "/jcr:*". Please note that repGlobs do not play well together with actions. Use privileges instead (e.g. "jcr:read" instead of read action). See [issue #48](https://github.com/Netcentric/accesscontroltool/issues/48) | no
initialContent | Allows to specify docview xml to create the path if it does not exist. The namespaces for jcr, sling and cq are added automatically if not provided to keep xml short. Initial content must only be specified exactly once per path (this is validated). If paths without permissions should be created, it is possible to provide only a path/initialContent tuple. Available form version 1.7.0. | no

Every new data entry starts with a "-". 


Overall format

```
[principal]
   - path: a valid node path in CRX
     permission: [allow/deny]
     actions: actions string
     privileges: privileges string  
     repGlob: regex    (optional, path restriction as regular expression)
     initialContent: <jcr:root jcr:primaryType="sling:Folder"/>   (optional)
```

Only ACEs for groups which are defined in the same configuration file can be installed! This ensures a consistency between the groups and their ACE definitions per configuration file.

Cq actions and jcr: privileges can be mixed. If jcr: privileges are already covered by cq actions within an ACE definition they get ignored. Also aggregated privileges like jcr:all or rep:write can be used.

Example:

```
fragment-allow:
 
     - path: /content/isp
       permission: allow
       actions: read,modify,create,delete,acl_read,acl_edit,replicate
       repGlob: */jcr:content*
 
     - path: '/content/isp/*/articles'
       permission: allow
       actions: read,write
       privileges:
 
     - path: /content/intranet
       permission: allow
       actions: read,write
       privileges: crx:replicate
 
 fragment-deny:
 
     - path: /
       permission: deny
       actions: modify,create,delete,acl_read,acl_edit,replicate
```

In case the configuration file contains ACEs for groups which are not present in the current configuration no installation takes place and an appropriate error message gets displayed in the history log.

All important steps performed by the service as well as all error/warning messages get written to error log and history.

## Advanced configuration options

The ACTool also supports [loops, conditional statements and permissions for anonymous](docs/AdvancedFeatures.md).


## Validation

First the validation of the different configuration lines is performed based on regular expressions and gets applied while reading the file. Further validation consists of checking paths for existence as well as for double entries, checks for conflicting ACEs (e.g. allow and deny for same actions on same node), checks whether principals are existing under home. If an invalid parameter or aforementioned issue gets detected, the reading gets aborted and an appropriate error message gets append in the installation history and log.

If issues occur during the application of the configurations in CRX the installation has to be aborted and the previous state has to stay untouched! Therefore the session used for the installation only gets saved if no exceptions occured thus persisting the changes.

# Deploying ACLs

## Storage of configurations in CRX

Example showing 3 separate project-specific configuration sub-nodes each containing one or more configuration files:

<img src="docs/images/crx-storage.png">

The project specific configuration files are stored in CRX under a node which can be set in the OSGi configuration of the AcService (system/console/configMgr). Each folder underneath this location may contain `*.yaml` files that contain AC configuration. The folder structure gets created by deployment or manually in CRX. 

In general the parent node may specify required Sling run modes being separated by a dot (```.```). Folder names can contain runmodes in the same way as OSGi configurations ([installation of OSGi bundles through JCR packages in Sling](http://sling.apache.org/documentation/bundles/jcr-installer-provider.html)) using a `.` (e.g. `myproject.author` will only become active on author). Additionally, multiple runmodes combinations can be given separated by comma to avoid duplication of configuration (e.g. `myproject.author.test,author.dev` will be active on authors of dev and test environment only). Each time a new configuration file gets uploaded in CRX (e.g. deployment) or the content of a file gets changed a node listener can trigger a new installation of the configurations. This behaviour can be enabled/disabled in UploadListenerService OSGi config.

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
    
## AC (search) Query

In order to exclude certain direct child nodes of the jcr:root node from the query (e.g. /home, since we don't want to have the rep: policy nodes of each user) to get all the rep: policy nodes in the system, these nodes can be configured. This configuration takes place in the OSGi configuration of the AcService ('/home', '/jcr:system' and '/tmp' are exclude by default). When a query is performed (e.g. during a new installation) , the results of the single queries performed on the nodes which are not excluded get assembled and returned as collective result for further processing.

## UploadListenerService

The Upload Listener Service allows to configure the NodeEvent Listener. In the OSGi console it can be enabled/disabled furthermore the listener path can also be configured here.

If enabled each new upload of a projectspecific configuration file triggers the AceService. Before a new installation starts, a dump (groups & ACLs) gets created which get stored under the backups-node.

## JMX interface

The [Jmx interface](docs/Jmx.md) provides utility functions such as installing and dumping ACLs or showing the history. 

## History Service

A history object collects messages, warnings, and also an exception in case something goes wrong. This history gets saved in CRX nder /var/statistics/achistory. The number of histories to be saved can be configured in the history service.

## Purge ACLs/Authorizables

Beside the options to install access control configurations and to create dumps the ac tool also offers different possibilities to purge ACLs/authorizables from the system. These methods are also available via JMX whiteboard (see screenshot: JMX Whiteboard of the AC tool).

Method | Action
--- | ---
purgeACL | This method purges the access control list of a (single) node in repository. The node path is entered as parameter before invocation.
purgeACLs | This method purges the access control list a node and also the access control lists of all child nodes. The node path is entered as parameter before invocation.
purgeAuthorizables | This method purges authorizables from home and also deletes all corresponding ACEs from the repository. Several authorizables are entered as comma separated list before invocation.
purgeAllAuthorizablesFromConfigurations | This method purges all authorizables defined in all configurations files and all their corresponding ACEs from the repository.

For any of these purge actions a separate purge history (node) containing all logging statements gets persisted in CRX in order to be able to track every of those actions afterwards. Such a purge history node gets saved under the history node of the current ac installation in place. Any of these purge nodes has a timestamp as suffix in the node name.

## Curl calls

### Trigger the 'execute' method of the AC service

```
curl -sS --retry 1 -u ${CQ_ADMINUSER}:${CQ_ADMINPW} -X POST "http://${CQ_SERVER}:${CQ_PORT}/system/console/jmx/biz.netcentric.cq.tools.actool:id='ac+installation'/op/execute/"
```

## Building the packages from source

If needed you can [build ACTool yourself](docs/BuildPackage.md).

## License

The ACTool is licensed under the [Eclipse Public License - v 1.0](LICENSE.txt).