# Advanced features

## Loops

Configuration sections for groups and ACEs allow to use loops to specify multiple, similar entries. In order to do this, a FOR statement has to be used in place of a group name. The FOR statement names a loop variable and lists the values to iterate over. All the children of the FOR element are repeated once per iteration and all group names and property values of child elements that contain the name of the loop variable within '${' and '}' have that expression substituted with the current value of the loop variable.

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

FOR loops can be nested to any level:

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

For some use cases it is useful to dynamically derive the list of possible values from the content structure. FOR ... IN CHILDREN OF will loop over the children of the provided path (skipping 'jcr:content' nodes) and provide an object with the properties name, path, primaryType, jcr:content (a map of all properties of the respective node) and title (./jcr:content/jcr:title added to root map for convenience).

```
- FOR site IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site.title}
         isMemberOf: 
         path: /home/groups/${site.name}
```

### Loops that traverse the full jcr:content structure

By default only the direct properties of the jcr:content node will be mapped and made available for substitution. In some cases it may be desirable to extract properties from further down within the jcr:content node structure. As this can be memory intensive a special syntax has been introduced to enable this on a per-loop basis. FOR ... WITH CONTENT IN ... will perform the for loop and map the full jcr:content node structure.

```
- FOR site WITH CONTENT IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site["jcr:content"]["node1"]["prop1"]}
         isMemberOf:
         path: /home/groups/${site.name}
```

### Conditional entries

When looping over content structures, entries can be applied conditionally using the "IF" keyword:

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

Expressions are evaluated using javax.el expression language. The following utility functions are made available to any EL expression used in yaml:

- split(str,separator) 
- join(array,separator)
- subarray(array,startIndexInclusive,endIndexExclusive)
- upperCase(str) 
- lowerCase(str) 
- replace(text,searchString,replacement) 
- substringAfter(str,separator) 
- substringBefore(str,separator) 
- substringAfterLast(str,separator) 
- substringBeforeLast(str,separator) 
- contains(str,fragmentStr) 
- endsWith(str,fragmentStr) 
- startsWith(str,fragmentStr) 

### Variables

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

NOTE: The scope of a variable is always limited to the lines in the very same yaml file following the definition till it is either redefined or the end of the yaml file is reached (this limitation will supposably be lifted with [#257][i257]).

## Configure permissions for built-in users or groups (like anonymous)

To configure permissions for already existing users, it's best to create a custom group and add this user to the `members` attribute of that group. The ACEs added to the custom group will then be effective for that user as well.

Another alternative is to list the built-in user in the YAML file (with the correct path and system user flag) and leverage `unmanagedAcePathsRegex` as outlined below.
  
## Configure memberships of/towards externally managed groups

The AC Tool manages relationships between authorizables of the configuration (the normal case, not adjustable), but also relationships to authorizables that are not contained in the configuration (that means if you add `isMemberOf: contributor` this group id will be added to the `contributur`'s `members` list; if you remove `contributor` this group membership will be removed with the next run). This is the case even though only one side of the relationship is contained in the AC Tool configuration. To not manage (currently means only to not remove) certain relationships, the following configuration can be used:

```
- global_config:
      defaultUnmanagedExternalIsMemberOfRegex: <regular expression matching against the externally managed group's authorizable id. The members property of matching external groups are not modified at all (i.e. pointers towards ACTool managed groups are not removed).
      defaultUnmanagedExternalMembersRegex: <regular expression matching against the ACTool managed groups' members property values> 
      keepExistingMembershipsForGroupNamesRegEx: <regular expression> # DEPRECATED but still supported, sets the value for both configurations from above at the same time 
```

That way relationships that are created programmatically or manually can be left intact and the AC Tool does not remove them. Also this allows to have two configuration sets at different root paths.

### Examples ###

* `defaultUnmanagedExternalMembersRegex: .*` allow arbitrary groups to inherit from ACTool managed groups and keep those (unmanaged) relations even though relationship hasn't been established through the ACTool. Might be useful in a multi-tenant setup where each tenant maintains his own list of groups (e.g. via ACTool in dedicated packages) and wants to inherit from some fragments being set up by the global YAML file.
* `defaultUnmanagedExternalIsMemberOfRegex: contributor` allow the contributor group to list an ACTool managed group as a member (i.e. ACTool managed group inherits from `contributor` all ACLs) and keep that relation even though this relationship hasn't been established through the ACTool. This is **very dangerous as unmanaged ACLs may creep into AC managed groups**! So please use with care.

## Limiting where the AC Tool creates and removes ACEs

The property `unmanagedAcePathsRegex` for authorizable configurations (users or groups) can be used to ensure certain paths are not managed by the AC Tool. This property must contain a regular expression which is matched against all ACE paths bound to the authorizable found in the system. All ACEs with matching paths are not touched:

### Examples
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
    # the user does exist already, make sure the path is set correctly
    - path: /home/users/system/wcm
      isSystemUser: true
   # everything outside /conf should not be managed by the ac tool
      unmanagedAcePathsRegex: /(?!conf).*
```

## Automatically purge obsolete groups and users
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

## Intermediate save() calls during ACL installation

For large installations (> 1000 groups) that use MongoDB, the system possibly may get into an invalid state as older versions of OAK (AEM 6.1/6.2 ootb) do not always correctly fire the post commit hook for very large change sets (OAK-5557). To circumvent this issue it is possible since v1.9.2 to configure the OSGi property `intermediateSaves=true` of PID `biz.netcentric.cq.tools.actool.impl.AcInstallationServiceImpl`. 

NOTE: This is never necessary when using TarMK and also it should only be used for MongoMK for large installations that do not contain a fix for OAK-5557 yet as the rollback functionality is lost when enabling intermediate saves.


[i257]: https://github.com/Netcentric/accesscontroltool/issues/257
