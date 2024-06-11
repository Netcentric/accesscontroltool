# Advanced features

## Expressions

Expressions are evaluated using the [Jakarta Expression Language 4.0](https://jakarta.ee/specifications/expression-language/4.0/jakarta-expression-language-spec-4.0.html) (since v3.0.3 of the AC tool, before [javax.el EL 2.2](https://docs.oracle.com/javaee/6/tutorial/doc/gjddd.html) was used). They can be used anywhere in the YAML and are processed after YAML parsing and interpolation. Note though that only the [*Immediate Evaluation* syntax `${...}`](https://docs.oracle.com/javaee/6/tutorial/doc/bnahr.html#bnahs) is supported but not the *Deferred Evaluation* syntax (`#{...}`).

The following utility functions are made available to any EL expression used in YAML.
They can either be used standalone or combined with the [default EL operators](https://docs.oracle.com/javaee/6/tutorial/doc/bnaik.html).

Function Signature | Description
---|---
`split(String str, String separator)`|[`StringUtils.split(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#split(java.lang.String,%20java.lang.String))
`join(Object[] array, String separator)`|[`StringUtils.join(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#join(java.lang.Object[],%20java.lang.String))
`subarray(String array, startIndexInclusive,endIndexExclusive)`|  [`ArrayUtils.subarray(...)`](https://commons.apache.org/proper/commons-lang/apidocs/org/apache/commons/lang3/ArrayUtils.html#subarray-T:A-int-int-)
`upperCase(String str)`|[`StringUtils.upperCase(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#upperCase(java.lang.String))
`lowerCase(String str)`|[`StringUtils.lowerCase(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#lowerCase(java.lang.String))
`capitalize(String str)`|[`StringUtils.capitalize(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#capitalize(java.lang.String))
`replace(String text, String searchString, String replacement)`|[`StringUtils.replace(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#replace(java.lang.String,%20java.lang.String,%20java.lang.String))
`substringAfter(String str, String separator)`|[`StringUtils.substringAfter(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#substringAfter(java.lang.String,%20java.lang.String))
`substringBefore(String str, String separator)`|[`StringUtils.substringBefore(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#substringBefore(java.lang.String,%20java.lang.String))
`substringAfterLast(String str, String separator)`|[`StringUtils.substringAfterLast(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#substringAfterLast(java.lang.String,%20java.lang.String))
`substringBeforeLast(String str, String separator)`|[`StringUtils.substringBeforeLast(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#substringBeforeLast(java.lang.String,%20java.lang.String))
`contains(String str, String fragmentStr)`|[`StringUtils.contains(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#contains(java.lang.CharSequence,%20java.lang.CharSequence))
`endsWith(String str, String suffix)`|[`StringUtils.endsWith(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#endsWith(java.lang.CharSequence,%20java.lang.CharSequence))
`startsWith(String str, String prefix)`| [`StringUtils.startsWith(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#startsWith(java.lang.CharSequence,%20java.lang.CharSequence))
`length(String string)`| [`StringUtils.length(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#length(java.lang.CharSequence))
`defaultIfEmpty(String str, String default)` | [`StringUtils.defaultIfEmpty(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringUtils.html#defaultIfEmpty(T,%20T))
`escapeXml(String str)` | [`StringEscapeUtils.escapeXml10(...)`](https://commons.apache.org/proper/commons-lang/javadocs/api-3.3/org/apache/commons/lang3/StringEscapeUtils.html#escapeXml10(java.lang.String)), useful for escaping values within `initialContent` which uses [enhanced JCR DocView syntax (an XML 1.0 language)](https://jackrabbit.apache.org/filevault/docview.html).
`containsItem(List<String> list, String item)`| Returns `true` if the item is contained in the given list.
`containsAnyItem(List<String> list, List<String> items)`| Returns `true` if any of the items is contained in the given list.
`containsAllItems(List<String> list, List<String> items)`| Returns `true` if all of the items are contained in the given list (independent of their order).
`keys(Map<Object, Object> map)`| Returns the list of keys for the given map. The order is non-predictable.
`values(Map<Object, Object> map)`| Returns all values for the given map. The order is non-predictable.


## Variables

### Simple variables

Sometimes it can be useful to declare variables to reuse values or to give certain strings an expressive name:

```
- DEF groupPrefix="xyz"

- FOR site IN CHILDREN OF /content/myPrj:

    - DEF groupToken="${groupPrefix}-${site.name}"

    - cr-${groupToken}:
           
       - name: Content Reader ${site.title}
         isMemberOf: 
         path: /home/groups/${groupToken}
```

DEF entries can be used inside and outside of loops and conditional entries.

Variables can also be declared to be an array and used in a loop:

```
    - DEF testArr=[val1,val2]
    
    - FOR arrVal IN ${testArr}:
```

### Complex variables with value from yaml structure

There is also a multi-line variant of the DEF statement that allows to define complex structures directly in yaml using a `DEF varName=:`syntax:

```
# list
- DEF simpleArray=:
     - val1
     - val2
     - val3
- FOR loopVar IN ${simpleArray}:    
       - group-${loopVar}:   
          - name: "Group ${loopVar}"

# list of objects
- DEF listOfMaps=:
     - key1: obj1val1
       key2: obj1val2
       key3: obj1val3
     - key1: obj2val1
       key2: obj2val2
       key3: obj2val3
- FOR loopVar IN ${listOfMaps}:  
    - group-${loopVar.key1}: 
       - name: "Group ${loopVar.key2} ${loopVar.key3}"

# map (functions keys() or values() can be used to loop over map)
- DEF simpleMap=:
     key1: mapval1
     key2: mapval2
     key3: mapval3
- FOR loopVar IN ${keys(simpleMap)}:
   - group-${loopVar}:
      - name: "Value ${simpleMap[loopVar]}"
```

### Global variables 

By default, variables are local, this means the scope of a variable is limited to:
 * the lines in the very same yaml file following the definition till it is either redefined or the end of the yaml file is reached 
 * `FOR` loop in which variable is defined or re-defined.

It is possible to define global variables (that are available across multiple yaml files) as follows:

```
- global_config:
     vars:
         - DEF groupPrefix="xyz"
         - DEF testArr=:
                  - val1
                  - val2
```

Global variables still only become visible in the order of how the yaml files are processed, therefore it usually makes sense to put them in their own file prefixed with `_` to ensure they are processed at the beginning, e.g. `_globalvars.yaml`.

Local variables override global variables, but only in the same file (and only from the definition of the local variable onwards).

### Predefined variables 

Some variables are provided by default.

variable name | description
--- | --- 
`RUNMODES` | List of active run modes. Uses `SlingSettingsService#getRunModes` underneath. Consider using [run mode specific yaml files](Configuration.md#run-modes) as alternative.
`env.<envVariableName>` | Environment variable with name `<envVariableName>` as provided by the operating system. Uses [System.getenv()](https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#getenv--). To provide a default use `defaultIfEmpty()` as linked in from StringUtils class: `${defaultIfEmpty(env.my_env_variable, 'default-val')}`. **NOTE: Use this feature sparingly, for most cases the configuration should be self-contained. One use case for this is production passwords (as needed for non-system users like replication receiver)**.


## Loops

Configuration sections for groups and ACEs allow to use loops to specify multiple, similar entries. In order to do this, a `FOR` statement has to be used in place of a group name. The `FOR` statement names a loop variable and lists the values to iterate over. All the children of the `FOR` element are repeated once per iteration and all group names and property values of child elements that contain the name of the loop variable within '${' and '}' have that expression substituted with the current value of the loop variable.

For example, the following configuration element:

```
- FOR brand IN [ BRAND1, BRAND2, BRAND3 ]:

    - content-${brand}-reader:

       - name: 
         isMemberOf: 
         path: /home/groups/${brand}
```

Gets replaced with

```
    - content-BRAND1-reader:

       - name: 
         isMemberOf: 
         path: /home/groups/BRAND1

    - content-BRAND2-reader:

       - name: 
         isMemberOf: 
         path: /home/groups/BRAND2

    - content-BRAND3-reader:

       - name: 
         isMemberOf: 
         path: /home/groups/BRAND3
```

### Nested Loops

`FOR` loops can be nested to any level:

```
- for brand IN [ BRAND1, BRAND2 ]:

    - content-${brand}-reader:

       - name: 
         isMemberOf: 
         path: /home/groups/${brand}

    - content-${brand}-writer:

       - name: 
         isMemberOf: 
         path: /home/groups/${brand}
         
    - for mkt in [ MKT1, MKT2 ]:
                       
        - content-${brand}-${mkt}-reader:

           - name: 
             isMemberOf: 
             path: /home/groups/${brand}/${mkt}

        - content-${brand}-${mkt}-writer:

           - name: 
             isMemberOf: 
             path: /home/groups/${brand}/${mkt}
```

This will create 12 groups:

* content-BRAND1-reader
* content-BRAND1-writer
* content-BRAND1-MKT1-reader
* content-BRAND1-MKT1-writer
* content-BRAND1-MKT2-reader
* content-BRAND1-MKT2-writer
* content-BRAND2-reader
* content-BRAND2-writer
* content-BRAND2-MKT1-reader
* content-BRAND2-MKT1-writer
* content-BRAND2-MKT2-reader
* content-BRAND2-MKT2-writer

### Loops derived from content structure

For some use cases it is useful to dynamically derive the list of possible values from the content structure. `FOR ... IN CHILDREN OF` will loop over the children of the provided path (skipping 'jcr:content' nodes) and provide an object with the properties name, path, primaryType, jcr:content (a map of all properties of the respective node) and title (./jcr:content/jcr:title added to root map for convenience).

```
- FOR site IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site.title}
         isMemberOf: 
         path: /home/groups/${site.name}
```

### Loops that traverse the full jcr:content structure

By default only the direct properties of the jcr:content node will be mapped and made available for substitution. In some cases it may be desirable to extract properties from further down within the jcr:content node structure. As this can be memory intensive a special syntax has been introduced to enable this on a per-loop basis. `FOR ... WITH CONTENT IN ...` will perform the for loop and map the full jcr:content node structure.

```
- FOR site WITH CONTENT IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site["jcr:content"]["node1"]["prop1"]}
         isMemberOf:
         path: /home/groups/${site.name}
```

## Conditional entries

Entries can be applied conditionally using the `IF` keyword (e.g. within a Loop)

```
- FOR site IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site.title}
         isMemberOf: 
         path: /home/groups/${site.name}

    - IF ${endsWith(site.name,'-master')}:
       - content-reader-master-${site.name}:
          - name: Master Content Reader ${site.title}
            isMemberOf: 
            path: /home/groups/global
```

## Interpolate values

Sometimes configuration values should be obtained from somewhere else and should not appear as literals in the YAML (e.g. to hide sensitive information like passwords or to reuse the same yaml on multiple environments with slight environment adaptations). For that the [Felix Configadmin Interpolation Plugin][felix-interpolation-plugin] is hooked up with the AC Tool to allow to reference Environment Variables, Secrets and Properties (both Framework and Java System Properties). The syntax is as follows

1. `$[env:my_env_variable]` to reference the environment variable named `my_env_variable`
1. `$[secret:my_secret_variable]` to reference the secret variable named `my_secret_variable`
1. `$[prop:my.property]` to reference the framework property named `my.property`

Further details and the full syntax (incl. default values and type support) is described in the plugin's [README][felix-interpolation-plugin].

Both secret and environment variables can be easily set for AEMaaCS environments via [Cloud Manager API](https://www.adobe.io/apis/experiencecloud/cloud-manager/api-reference.html#/Variables/patchEnvironmentVariables) or with some client like [Adobe IO CLI together with the Cloud Manager Plugin](https://github.com/adobe/aio-cli-plugin-cloudmanager#aio-cloudmanagerenvironmentset-variables-environmentid).

Although AC Tool comes with native support for environment variables already (via the global variable `env.`) the syntax supported by the Felix Configadmin Interpolation Plugin is more powerful and usage of Secrets and Properties is not possible without this feature at all.

*This feature is only available in AEMaaCS and since ACTool 2.7.0*

## Auto-create test users for groups

It is possible to automatically create test users (since v2.1.0) for groups given in the configuration by providing a yaml hash `autoCreateTestUsers` in `global_config`. The following properties can be configured:

property | comment | required
--- | --- | ---
`createForGroupNamesRegEx` | A regex (matched against authorizableId of groups) to select the groups, test users should be created for | required
`prefix` | The prefix for the authorizable id, for instance if prefix "tu-" is given, a user "tu-myproject-editors" will be created for group "myproject-editors" | required
`name` | The name as configured in user's profile, allows for interpolation with EL *) | optional, defaults to "Test User %{group.name}"
`description` | The description as configured in user's profile, allows for interpolation with EL *) | optional, not set by default
`email` | The email as configured in user's profile, allows for interpolation with EL *). | optional, not set by default
`path` | The location where the test users shall be created | required
`password` | The password for all test users to be created. Can be encrypted using CryptoSupport. Defaults simply to the authorizable id of the test user. Allows for interpolation with EL *) | optional
`skipForRunmodes` | The configuration is placed in a regular config file, hence it is possible to add one to an author configuration (located in e.g. in a folder "config.author" and one to a publish configuration (e.g. folder "config.publish"). To avoid creating special runmodes folders just for this configuration that list all runmodes except production, skipForRunmodes can be a comma-separated list of runmodes, where the users are not created.  Defaults to prod,production | optional

*) Interpolation of group properties can be used with EL, however as `$` is evaluated at an earlier stage, `%{}` is used here. Available is `%{group.id}`, `%{group.name}`, `%{group.path}` or expressions like `%{split(group.path,'/')[2]}`.

Example:

```
- global_config:
   autoCreateTestUsers: 
     createForGroupNamesRegEx: "(myproj)-.*" 
     prefix: "testuser-"
     name: "TU %{group.name}"
     path: /home/users/myproj-test-users
     skipForRunmodes: prod, preprod
```

*As [dynamic groups](https://jackrabbit.apache.org/oak/docs/security/authentication/external/defaultusersync.html#enforcing-dynamic-groups) come with an Oak validator which enforces that no members are manually added to dynamic groups (like IMS managed groups) test user's cannot be created for dynamic groups directly. Otherwise an error like `OakConstraint0077: Attempt to add members to dynamic group '<dynamic group id>'` is emitted. As workaround use a non-dynamic ACTool managed group which is having both the according dynamic group as well as the test user as member*

## Configure unmanaged aspects

The following table gives an overview of what is managed (in terms of authorizables themselves, group memberships and ACLs) by AC Tool and what is not:

| Aspect | Default behaviour if existing in config but not in repo | Default behaviour if existing in repo but not in config | Configuration options to change the default behaviour |
| --- | --- | --- | --- |
| Users and Groups | are created | are **not** removed | `obsolete_authorizables` can be used, see [documentation](https://github.com/Netcentric/accesscontroltool/blob/develop/docs/AdvancedFeatures.md#automatically-purge-obsolete-groups-and-users), to also remove groups/users existing in repo but not in config | 
| Group memberships of users and groups within config **not "leaving the config space"**, i.e. only referring to groups managed by AC Tool (`isMemberOf` or `members`, internally `members` is always translated to `isMemberOf` on the other side of the relationship) | are created | are removed | Default behaviour can not be changed by design for consistent AC setups |
| Group memberships of users and groups **"leaving the config space"** , i.e. referring to groups not managed by AC Tool and hence inheriting permissions from elsewhere (`isMemberOf`) | are created | are removed (this affects even membership not initially created by AC Tool, e.g. created programmatically or manually) | Use `unmanagedExternalIsMemberOfRegex` on an authorizable config or `defaultUnmanagedExternalIsMemberOfRegex` in `global_config` to not touch group memberships. *You cannot use groups in `isMemberOf` that you explicitly unmanage in the given regex.*  |
| Group/user members of groups **"leaving the config space"**, i.e. referring to groups/users not managed by AC Tool  and pushing permissions to other, existing authorizables (`members`) | are created | **relationships to groups and system users are removed** - relationships to regular users are untouched (those are often assigned by user administrators, LDAP or SSO) | Similar to `isMemberOf`, in row above, use `unmanagedExternalMembersRegex` on group configs or `defaultUnmanagedExternalMembersRegex` to not touch group memberships. *You cannot use users/groups in `members` that you explicitly unmanage in the given regex.* |
| ACEs for authorizables in the config | are created | are removed | Property `unmanagedAcePathsRegex` on authorizable config allow to not touch certain paths for a authorizable. Also, `defaultUnmanagedAcePathsRegex` can be used to define unmanaged areas globally. |

For common use cases which may require tweaking the default behaviour refer to the following section

### Configure permissions for built-in users or groups (like anonymous)

To configure permissions for already existing users, it's best to create a custom group and add this user to the `members` attribute of that group. The ACEs added to the custom group will then be effective for that user as well.

This is not an option for the [`everyone` group](https://jackrabbit.apache.org/oak/docs/security/user/default.html#Everyone_Group) as it is neither allowed to put groups/users as members to this group (because implicitly every principal is member of this group) nor to put this group as member to another group (to prevent cycles, compare with [OAK-7323](https://issues.apache.org/jira/browse/OAK-7323)).
Also in case of using [Sling Service Authentication bound to principals](https://sling.apache.org/documentation/the-sling-engine/service-authentication.html#service-user-mappings)(available since AEM 6.4) you cannot use group memberships, as the principal mapping does not consider (transitive) group memberships. Those mappings can be identified by looking up the according `org.apache.sling.serviceusermapping.impl.ServiceUserMapperImpl.amended` configuration instance with its `user.mapping` property. It that has the format `<service-name>[:<subservice-name>]="["<principal name>{","<principal name>}"]"` (look for square brackets) it is a principal mapping.

In those cases just list the built-in authorizable (user/group) in the YAML file (with the correct authorizable id) and leverage `unmanagedAcePathsRegex` as outlined below. No other properties need to be set on the authorizable as this is in fact not touched by the ACTool.

### Configure memberships of/towards externally managed groups

The AC Tool manages relationships between authorizables of the configuration, but also relationships to authorizables that are not contained in the configuration (that means if you add `isMemberOf: contributor` this group id will be added to the `contributur`'s `members` list; if you remove `contributor` from `isMemberOf` this group membership will be removed with the next run). This is the case even though only one side of the relationship is contained in the AC Tool configuration. To not manage certain relationships, the following configuration can be used:

```
- global_config:
      defaultUnmanagedExternalIsMemberOfRegex: <regular expression matching against the externally managed group's authorizable id. The members property of matching external groups are not modified at all (i.e. pointers towards ACTool managed groups are not removed).
      defaultUnmanagedExternalMembersRegex: <regular expression matching against the ACTool managed groups' members property values> 
```

That way relationships that are created programmatically or manually can be left intact and the AC Tool does not remove them. Also this allows to have two configuration sets at different root paths.

Additionally, it is also possible to set `unmanagedExternalIsMemberOfRegex` and `unmanagedExternalMembersRegex` directly on the authorizable definition (then only effective locally to the authorizable).

#### Examples

* `defaultUnmanagedExternalMembersRegex: .*` allow arbitrary groups to inherit from ACTool managed groups and keep those (unmanaged) relations even though relationship hasn't been established through the ACTool. Might be useful in a multi-tenant setup where each tenant maintains his own list of groups (e.g. via ACTool in dedicated packages) and wants to inherit from some fragments being set up by the global YAML file.
* `defaultUnmanagedExternalIsMemberOfRegex: contributor` allow the contributor group to list an ACTool managed group as a member (i.e. ACTool managed group inherits from `contributor` all ACLs) and keep that relation even though this relationship hasn't been established through the ACTool. This is **very dangerous as unmanaged ACLs may creep into AC managed groups**! So please use with care.

### Limiting where the AC Tool creates and removes ACEs

The property `unmanagedAcePathsRegex` for authorizable configurations (users or groups) can be used to ensure certain paths are not managed by the AC Tool. This property must contain a regular expression which is matched against all ACE paths bound to the authorizable found in the system. All ACEs with matching paths are not touched. By setting the global config `defaultUnmanagedAcePathsRegex` it is possible to exclude certain areas of the JCR totally from removing (and creating once #244 is fixed) at all.

#### Examples

```
    - testgroup:

        - name: "Test Group"
          unmanagedAcePathsRegex: /content/dam/.*
```
That way for `testgroup`, ACEs in `/content/dam/` will be left untouched for this particular group. 

You can use negative lookaheads to whitelist management of certain paths:

```
- user_config: 
  - version-manager-service: 
    - path: /home/users/system/wcm
      # the user does exist already, make sure the path is set correctly
      isSystemUser: true
      # everything outside /conf is not be managed by the ac tool
      unmanagedAcePathsRegex: /(?!conf).*
```

Example for setting it globally:

```
- global_config:
      defaultUnmanagedAcePathsRegex: /content/project2.* # will never change any ACLs underneath this root path 
```

### Automatically purge obsolete groups and users
The root element `obsolete_authorizables` can be used to automatically purge authorizables that are not in use anymore:

```
- obsolete_authorizables:
      - group-to-delete-1
      - group-to-delete-2
      - group-to-delete-3
      - user-to-delete-1 
      - user-to-delete-2 
```

The `FOR` and `IF` syntax can be used within `obsolete_authorizables`.

## Providing Initial Content

The property `initialContent` on ACE entries allows to specify enhanced docview xml to create the path if it does not exist. The namespaces for jcr, sling and cq are added automatically if not provided to keep xml short. Initial content must only be specified exactly once per path (this is validated). 

The feature is useful when staging new content paths through all environments up to production. Then often the challenge is to ensure that the paths are created with the correct permissions on all environments. **Using a mix of content packages (that contain the path) and AC tool (that sets the ACL for the path) is not recommended since it is hard to control the correct installation order** (the path needs to exist when the AC Tool runs). It is better to provide initial content for nodes that also carry permissions with the `intialContent` property on ACE entries as follows: 

```
        - path: /content/cq:tags/newstags
          permission: allow
          actions: read
          initialContent: |
                            <jcr:root jcr:primaryType="cq:Tag"
                                jcr:title="News Tags"
                                sling:resourceType="cq/tagging/components/tag">
                            </jcr:root>
```

Sometimes it is also useful to create intermediate paths that do not even contain an ACE entry, this is also supported by the following syntax:

```
        - path: /content/sites # only initialContent without permissions just ensures the node exists
          initialContent: |
                            <jcr:root jcr:primaryType="cq:Page">
                                <jcr:content jcr:primaryType="cq:PageContent"
                                    jcr:title="Intermediate Page"
                                	   sling:resourceType="foundation/components/redirect"
                            </jcr:root>

        - path: /content/sites/site1
          permission: allow
          actions: read
          initialContent: |
                            <jcr:root jcr:primaryType="cq:Page">
                                <jcr:content jcr:primaryType="cq:PageContent"
                                    jcr:title="Site 1"
                                	   sling:resourceType="site1/home"
                            </jcr:root>                    
```

## Update Groups in External User Management Systems

Since AC Tool 3.1.0 there is support for creating/updating groups in [Adobe IMS](https://www.adobe.com/content/dam/cc/en/trust-center/ungated/whitepapers/corporate/adobe-identity-management-services-security-overview.pdf). Those are the groups which are exposed in the Adobe Admin Console and automatically used for [AEMaaCS](https://experienceleague.adobe.com/en/docs/experience-manager-cloud-service/content/security/ims-support) and also [AEM 6.5 hosted by AMS](https://experienceleague.adobe.com/en/docs/experience-manager-learn/foundation/authentication/adobe-ims-authentication-technical-video-understand).

To enable that feature, just set the property `externalSync` on the group to be synced in the YAML file to `true`.
In addition an OSGi configuration for the leveraged [UMAPI](https://adobe-apiplatform.github.io/umapi-documentation/en/) needs to be provided in the configuration PID `biz.netcentric.cq.tools.actool.ims.IMSUserManagement`. This configuration should only be provided for run mode `author` to prevent the same groups from being created/updated multiple times. Also make sure you don't trigger the update too often due to [throttling of that API](https://adobe-apiplatform.github.io/umapi-documentation/en/API_introduction.html#throttling-and-error-handling).

Only the group id (called name in IMS context) and the description are set for synchronized groups in IMS. Memberships are not modified and external groups are never deleted.

## Health Check

The AC Tool comes with a Sling Health Check to returns WARN if the last run of the AC Tool was not successful. The health check can be triggered via `/system/console/healthcheck?tags=actool`. Additional tags can be configured using PID `biz.netcentric.cq.tools.actool.healthcheck.LastRunSuccessHealthCheck` and property `hc.tags`. Also see [Sling Health Check Tools Documentation](https://sling.apache.org/documentation/bundles/sling-health-check-tool.html).

## Use Manual ACL Ordering

By default ACEs with denies are sorted up to the top of the list, this follows the best practice to order denies always before allows - this makes by default allows always take precedence over denies. This is because denies should be used sparsely: Normally there is exactly one group that includes all deny-ACEs for to-be-secured content and many groups with allow-ACEs, that selectively allow what has been denied by the "global deny" group.

For some special cases (e.g. when working with restrictions that limit a preceding allow as introduced with [Sling Oak Restrictions](https://sling.apache.org/documentation/bundles/sling-oak-restrictions.html)) it is possible to specify `keepOrder: true` for an ACE entry. For those cases the order from the config file is kept when is used.

The following examples shows a legitimate example of using `keepOrder: true`:

```
- myproj-editor:

       - path: /content/myproj
         permission: allow
         actions: read,acl_read,create,modify
         privileges:

       - path: /content/myproj
         permission: deny
         privileges: rep:write
         keepOrder: true # this ensures that the rule is NOT ordered to top 
         restrictions:
                  sling:resourceTypes:  myproj/iframe
```
This example gives the group `myproj-editor` edit rights for all content in folder `myproj`, except for the iframe component.


[felix-interpolation-plugin]: https://github.com/apache/felix-dev/blob/master/configadmin-plugins/interpolation/README.md
