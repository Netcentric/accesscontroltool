# Configuration File Format

For better human readability and easy editing the ACL configuration files use the YAML format.

You can split your configuration to multiple files and directories. See also [best practices](BestPractices.md). Each folder can include one or more Yaml files ("*.yaml").
The file format is the same for all files.

You can find some examples in [example config package](../accesscontroltool-exampleconfig-package/src/main/jcr_root/apps/netcentric/actool-exampleconfig).


## Storage of configurations in CRX

This example shows three separate project specific configuration subnodes (multipleFiles, runmodes, simple) each containing one or more configuration files:

<img src="images/crx-storage.png">

The project specific configuration files are stored in CRX under a node which can be set in the OSGi configuration of the AcService (system/console/configMgr). Each folder underneath this location may contain `*.yaml` files that contain AC configuration. You can use a normal content package to deploy the files.

## Run modes 

In general the parent node may specify required Sling run modes being separated by a dot (```.```). Folder names can contain runmodes in the same way as OSGi configurations ([installation of OSGi bundles through JCR packages in Sling](http://sling.apache.org/documentation/bundles/jcr-installer-provider.html)) using a `.` (e.g. `myproject.author` will only become active on author). Additionally, multiple runmodes combinations can be given separated by comma to avoid duplication of configuration (e.g. `myproject.author.test,author.dev` will be active on authors of dev and test environment only).

Examples:

* project.author: runs on "author" run mode only
* project.author.dev: runs only when run modes "author" and "dev" are present
* project.author.test,author.dev: requires run mode "author" and either "test" or "dev" to be present

## Overall structure a of an AC configuration file

<img src="images/configuration-file-structure.png">

Every configuration file comprises a group section where groups and their membership to other groups get defined and a ACE section where all ACEs in the repository regarding these groups get defined. These ACE definitions are again written under the respective group id. The group of an ACE definition in each configuration file has to match a group which is also defined in the same file. Groups which are contained in the isMemberOf property within a group definition have either to be defined in another configuration file or already be installed in the system on which the installation takes place.

## Configuration of groups

Groups are specified in the **group_config** A group record in the configuration file starts with the principal id followed by some indented data records containing properties like name/description and group membership:

```
[Groupd Id]
     - name: group name (optional, if empty group id is taken)
     - isMemberOf: comma separated list of other groups (optional)
     - members: comma separated list of groups that are member of this group
     - description: (optional, description)
     - path: (optional, path of the group in JCR)
     - migrateFrom: (optional, a group name assigned member users are taken over from, since v1.7)     
```

Example:

```
- group_config:

  - myeditors:
    - isMemberOf: mystaff
      members: editor

  - system-read:
    - isMemberOf: 
      description: system users with read access
      members: system-reader
```

If the isMemberOf property of a group contains a group which is not yet installed in the repository, this group gets created and its rep:members property gets filled accordingly. If another configuration gets installed having a actual definition for that group the data gets merged into the already existing one.

The members property contains a list of groups where this group is added as isMemberOf.

### Group migration

The property 'migrateFrom' allows to migrate a group name without loosing their members. Members of the group given in migrateFrom are taken over and the source/old group is deleted afterwards. This property is only to be used temporarily. Usually, it is only included in one released version that travels all environments. Once all groups are migrated the migrateFrom property should be removed. If the property points to a group that does not exist (anymore) the property is ignored.

## Configuration of users

In general it is best practice to not generate regular users by the AC Tool but use other mechanism (e.g. LDAP) to create users. However, it can be useful to create system users (e.g. for replication agents or OSGi service authentiation) or test users on staging environments.

Users can be configured in the same way as groups in the **user_config** section.

```
[User Id]
     - name: user name (optional, if empty user id is taken)
     - isMemberOf: comma separated list of groups (optional)
     - description: (optional, description)
     - path: (optional, path of the group in JCR)
     - isSystemUser: the created user is a system user (AEM 6.1 and later) (default: false, optional)
     - password: can be used to preset passwords (not allowed for system users) (optional)
     - profileContent: allows to provide docview xml that will reset the profile to the given structure after each run (optional, since v1.8.2)
     - preferencesContent: allows to provide docview xml that will reset the preferences node to the given structure after each run (optional, since v1.8.2)
```

Example:

```
- user_config:

  - editor:
    - isMemberOf: myeditors
      password: secret

  - system-reader:
    - name: system-reader
      isMemberOf: system-read
      path: s
      isSystemUser: true
```

Group memberships can be set on user entry or group entry or both.

## Configuration of ACEs

The configurations are done per principal followed by indented settings for each ACE. This data includes

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
- ace_config:

  - fragment-allow:

    - path: /content/myproject
      permission: allow
      actions: read,modify,create,delete,acl_read,acl_edit,replicate
      repGlob: */jcr:content*

    - path: '/content/myproject/*/articles'
      permission: allow
      actions: read,write
      privileges:
 
    - path: /content/mydemoproject
      permission: allow
      actions: read,write
      privileges: crx:replicate

  - fragment-deny:

    - path: /
      permission: deny
      actions: modify,create,delete,acl_read,acl_edit,replicate
```

In case the configuration file contains ACEs for groups which are not present in the current configuration no installation takes place and an appropriate error message gets displayed in the history log.

All important steps performed by the service as well as all error/warning messages get written to error log and history.

## Validation

First the validation of the different configuration lines is performed and gets applied while reading the file. Further validation consists of checking paths for existence as well as for double entries, checks for conflicting ACEs (e.g. allow and deny for same actions on same node), checks whether principals are existing under /home. If an issue is detected the reading is aborted and an appropriate error message is appended to the installation history and log.

If issues occur during the application of the configurations in CRX the installation has to be aborted and the previous state has to stay untouched. Therefore the session used for the installation only gets saved if no issues occurred thus persisting the changes.

