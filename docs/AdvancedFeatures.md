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

### Loops derived from content structure (since 1.8.x)

For some use cases it is useful to dynamically derive the list of possible values from the content structure. FOR ... IN CHILDREN OF will loop over the children of the provided path (skipping 'jcr:content' nodes) and provide an object with the properties name, path, primaryType, jcr:content (a map of all properties of the respective node) and title (./jcr:content/jcr:title added to root map for convenience).

```
- FOR site IN CHILDREN OF /content/myPrj:

    - content-reader-${site.name}:
       - name: Content Reader ${site.title}
         isMemberOf: 
         path: /home/groups/${site.name}
```


### Conditional entries (since 1.8.x)

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
- substringAfter(str,separator) 
- substringBefore(str,separator) 
- substringAfterLast(str,separator) 
- substringBeforeLast(str,separator) 
- contains(str,fragmentStr) 
- endsWith(str,fragmentStr) 
- startsWith(str,fragmentStr) 

## Configure permissions for anonymous (since 1.8.2)

Normally it is ensured by validation that a configuration's group system is self-contained - this means out-of-the-box groups like `contributor` cannot be used. For registered users in the system this approach works well since either the users are manually assigned to groups (by a user admin) or the membership relationship is maintained by LDAP or SSO extensions. For the `anonymous` user on publish that is not logged in by definition, there is no hook that allows to assign it to a group in the AC Tools configuration. Therefore as an exception, it is allowed to use the user `anonymous` in the `members` attribute of a group configuration.
  
## Configure memberships to Dynamic Groups

If a group configured via the AC Tool is a member of a group which is not part of the configuration, this membership is removed by the installation process. This behaviour makes sense to ensure the configuration to be self-contained and prevent unwanted "injection" of permissions into the configuration system as described by the yaml files. Therefore wherever possible groups and their dependent groups should be added to the AC Tool configuration.

An exception to this might be dynamic groups created and maintained by authors or end-users. As such groups and their memberships are not static they cannot be added easily to the configuration. For this use-case the global configuration "allowExternalGroupNamesRegEx" can be set to a regular expression that matches all group names, that may keep member AC Tool groups as member:

```
- global_config:
      allowExternalGroupNamesRegEx: external.* # the AC Tool groups can inherit from other external.* groups
```

## Automatically purge obsolete groups and users (since 1.9.1)
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

## Health Check (since 1.9.1)

The AC Tool comes with a Sling Health Check to returns WARN if the last run of the AC Tool was not successful. The health check can be triggered via `/system/console/healthcheck?tags=actool`. Additional tags can be configured using PID `biz.netcentric.cq.tools.actool.healthcheck.LastRunSuccessHealthCheck` and property `hc.tags`. Also see [Sling Health Check Tools Documentation](https://sling.apache.org/documentation/bundles/sling-health-check-tool.html).

## Use Manual ACL Ordering (since 1.9.1)

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
