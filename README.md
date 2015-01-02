Access Control Tool for Adobe AEM
=================================

The Access Control Tool for Adobe AEM (ACTool) is a tool that simplifies the specification and deployment of complex Access Control Lists in Adobe AEM.

# Prerequisites

Building the ACTool reuires Java 7 and Maven 3.2.

Installing ACTool requires Adobe AEM 6.0.

# Installation

A full build of ACTool can be executed by running:

```
mvn clean install
```

This command, if successful, will create a CQ Package as a ZIP file inside accesscontroltool-package/target called accesscontroltool-package-<VERSION>.zip.

The package can be installed using the AEM Package Manager or directly from the command line, assuming AEM is running on host localhost, port 4502, using the command:

```
mvn -PautoInstallPackage install
```

# Configuration File Format

For better human readability and easy editing the ACL configuration files use the YAML format.

## Overall structure a of an AC configuration file

[<img src="docs/images/configuration-file-structure.png">]

Every configuration file comprises a group section where groups and their membership to other groups get defined and a ACE section where all ACEs in the repository regarding these groups get defined. These ACE definitions are again written under the respective group id. The group of an ACE definition in each configuration file has to match a group which is also defined in the same file. Groups which are contained in the memberOf property within a group definition have either to be defined in another configuration file or already be installed in the system on which the installation takes place.

## Configuration of groups

A principal record in configuration file starts with the principal id followed by some indented data records which comprise of:

    optional principalname
    comma separated list of other groups which the current principal should belong to
    optional description 


Overall format

```
[Groupd Id]
     - name: groupname (optional, if empty groupd id is taken)
     - isMemberOf: comma separated list of other groups
     - description: (optional, description)
     - path: ?
```

Example

```
isp-editor      
   - isMemberOf: fragment-restrict-for-everyone,fragment-allow-nst
```

If the memberOf property of a group contains a group which is not yet installed in the repository, this group gets created and its rep:members property gets filled accordingly. if another configuration gets installed having a actual definition for that group the data gets merged into the already existing one.

## Configuration of ACEs

The configurations are done per principal followed by indented informations which comprise of config data which represents the settings per ACE. This data includes

property | comment | optional
--- | --- | ---
path | a node path. Wildcards '*' are possible. e.g. assuming we have the language trees de and en then /content/*/test would match: /content/de/test and /content/en/test (mandatory) If an asterisk is contained then the path has to be written inside single quotes ('...') since this symbol is a functional character in YAML. | no
permission | the permission (allow/deny) | no
actions | the actions (read,modify,create,delete,acl_read,acl_edit,replicate) | no, either actions or privileges; also a mix of both is possible
privileges | the privileges (jcr:read, rep:write, jcr:all, crx:replicate,jcr:addChildNodes,jcr:lifecycleManagement,jcr:lockManagement,jcr:modifyAccessControl,jcr:modifyProperties,
jcr:namespaceManagement,jcr:nodeTypeDefinitionManagement,jcr:nodeTypeManagement,jcr:readAccessControl,jcr:removeChildNodes,
jcr:removeNode,jcr:retentionManagement,jcr:versionManagement,jcr:workspaceManagement,jcr:write,rep:privilegeManagement)
repGlob	a repGlob expression | yes

Every new data entry starts with a "-". 


Overall format

```
[principal]
   - path: a valid node path in CRX
     permission: [allow/deny]
     actions: actions string
     privileges: privileges string (optional)
     repGlob: regex (optional, path restriction as regular expression)
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

# Deploying ACLs

## Storage of configurations in CRX

Example showing 3 separate project-specific configuration sub-nodes each containing one or more configuration files:

[<img src="docs/images/crx-storage.png">]


The projectspecific configuration files get stored in CRX under a node which can be set in the OSGi configuration of the AcService (system/console/configMgr). Each child node contains the project specific configuration file(s). Everytime a new installation gets executed, the newest configuration file gets used. The folder structure gets created by deployment or manually in CRX. Each time a new configuration file gets uploaded in CRX (e.g. deployment) or the content of a file gets changed a node listener can trigger a new installation of the configurations. This behaviour can be enabled/disabled in UploadListenerService OSGi config.
