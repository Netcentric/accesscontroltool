# JMX interface

The JMX interface offers the possibility to trigger the functions offered by the ACE service. These are:

* starting a new installation of the newest configuration files in CRX.
* purging of ACLs (of a single node or recursively for all subnodes)
* deletion of single ACEs
* purging users/groups from the instance (including all related ACEs).
* creation of dumps (ordered by path or by group)
* showing of history logs created during the installation

Also important status messages are shown here:

* configuration files that are taken into account
* display of the paths of last 5 history logs saved in CTX and a success status of each of those installation
* readiness for installation (if at least one configuration file is stored in CRX)
* execution status of service

<img src="images/ac-service.png">
    
## Operations

### apply()

This will apply the configuration files listed on top respecting the [run mode semantics](Configuration.md).    
Before installing a new configuration on an instance a validation of the data stored in the configuration file takes place. In case an issue gets detected the installation does not get performed.

The path of the config files is configured in OSGi - AC Installation Service.

<img src="images/installation-service.png">

### apply(configurationRootPath), applyRestrictedToPaths(paths), applyRestrictedToPaths(configurationRootPath, paths)

Special variants of apply: The parameter `configurationRootPath` allows to provide an alternative configuration location (other from the default as configured in OSGi - AC Installation Service). The parameter  `paths` (comma separated list of paths) allows to restrict the 
locations where the ACEs are applied (the installation of authorizables in /home are not affected by this). For instance it is possible 
to provide the `path` `/content/myproj` which will only change ACEs at that location (and not at other paths like e.g. `/etc` even if those are contained in the configuration).

###  groupBasedDump() and pathBasedDump()

* Group based dump: here all ACEs in the dump are grouped by their respective principal (group or user). This kind of dump gets triggered by the method: groupBasedDump(). The result is in AC Tool config file format and can be used as template to create a configuration file.
* Path based dump: here all ACEs in the dump are grouped by path thus representing a complete ACL. This kind of dump gets triggered by the method: pathBasedDump().

The created dump can be watched directly in JMX and also gets saved in CRX under /var/statistics/achistory/dump_[Timestamp]. The number of dumps to be saved in CRX can be configured in the OSGi configuration of the dump service in the field: "Number of dumps to save" (see screenshot).

<img src="images/dump-service.png">

There are also some additional options available in the OSGi configuration of the dump service:

* Include user in ACEs in dumps: if checked, all users which have ACEs directly set in the repository get added to the dump (ACEs in section "- ace_config:" and users in section "- user_config:")
* filtered dump: if checked, all ACEs belonging to the cq actions: modifiy, create or delete which have a repGlob set don't get added to the dump. Per default they are omitted since they're automatically set within CQ if one of the action gets set and are not necessary to expicitly being set in a new configuration file.
* include legacy ACEs in dump: if checked, legacy ACEs (ACEs of groups/users which can not be found under /home) also get added to the dump in an extra section called "legacy_aces" at the end of the dump. The order of ACEs listed there is the same as it is for the "valid" ACEs (path based or group based). If needed these ACEs can be manually deleted using the "purge_Authorizables" function by entering the respective principal name(s).
* AC query exclude paths: In order to exclude certain direct child nodes of the jcr:root node from the query (e.g. /home, since we do not want to have the rep:policy nodes of each user) to get all the rep:policy nodes in the system, these nodes can be configured. '/home', '/jcr:system' and '/tmp' are exclude by default.

Internal dumps which get created and used everytime an new AC installation takes place get also created by this service and contain always all ACEs (users and unfiltered ACEs).

### Purge ACLs/Authorizables

Beside the options to install access control configurations and to create dumps the AC Tool also offers different possibilities to purge ACLs/authorizables from the system.

Method | Action
--- | ---
purgeACL | This method purges the access control list of a (single) node in repository. The node path is entered as parameter before invocation.
purgeACLs | This method purges the access control list of a node and also the access control lists of all child nodes. The node path is entered as parameter before invocation.
purgeAuthorizables | This method purges authorizables from home and also deletes all corresponding ACEs from the repository. Several authorizables are entered as comma separated list before invocation.
purgeAllAuthorizablesFromConfiguration | This method purges all authorizables defined in all configurations files and all their corresponding ACEs from the repository.

For any of these purge actions a separate purge history (node) containing all logging statements gets persisted in CRX in order to be able to track every of those actions afterwards. Such a purge history node gets saved under the history node of the current ac installation in place. Any of these purge nodes has a timestamp as suffix in the node name.

