# JMX interface

The JMX interface offers the possibility to trigger the functions offered by the ACE service. These are:

* starting a new installation of the newest configuration files in CRX.
* purging of ACLs (of a single node or recursively for all subnodes)
* deletion of single ACEs
* purging users/groups from the instance (including all related ACEs).
* creation of dumps (ordered by path or by group)
* showing of history logs created during the installation

Also important status messages are shown here:

* readiness for installation (if at least one configuration file is stored in CRX)
* success status of the last installation
* display of the newest installation files (incl. date information)
* display of the paths of last 5 history logs saved in CTX and a success status of each of those installation

## AC Service
    
The main operation purpose of the AC service is the installation of ACE / group definitions from one or several configuration files to a CQ instance on the one hand or the creation of such files (dump) out of an existing configuration on the other hand. It offers possibilities like purging existing ACEs / principals from the instance before installing new ones, merging / adding new ACEs or performing a rollback to a previously saved state if needed. 
 
The configuration for ACEs principals and groups can be maintained in one file or in dedicated files.
 
The configurations are contained in textual files.These files get transferred to CRX usually via deployment. The service can be triggered by a JCR listener which reacts on node changes underneath a configurable path to detect a new configuration in the system, or by triggering the executing it via JMX.
 
Before installing a new configuration on an instance a validation of the data stored in the configuration file takes place. In case an issue gets detected the installation doesn't get performed.

<img src="images/ac-service.png">
    
## Dump service

The dump Service is responsible for creating dumps which are accessible via JMX whiteboard. There are 2 kinds of dumps supported: path ordered- and principal ordered dumps.

* path ordered dumps: here all ACEs in the dump are grouped by path thus representing a complete ACL. This kind of dump gets triggered by the method: pathBasedDump().
* group based dumps: here all ACEs in the dump are grouped by their respective principal (group or user). This kind of dump gets triggered by the method: groupBasedDump().

<img src="images/dump-service.png">

Every created dump can be watched directly in JMX and also gets saved in CRX under /var/statistics/achistory/dump_[Timestamp]. The number of dumps to be saved in CRX can be configured in the OSGi configuration of the dump service in the field: "Number of dumps to save" (see Screenshot)

There are also 3 additional options available in the OSGi configuration of the dump service:

* Include user in ACEs in dumps: if checked, all users which have ACEs directly set in the repository get added to the dump (ACEs in section "- ace_config:" and users in section "- user_config:")
* filtered dump: if checked, all ACEs belonging to the cq actions: modifiy, create or delete which have a repGlob set don't get added to the dump. Per default they are omitted since they're automatically set within CQ if one of the action gets set and are not necessary to expicitly being set in a new configuration file.
* include legacy ACEs in dump: if checked, legacy ACEs (ACEs of groups/users which can not be found under /home) also get added to the dump in an extra section called "legacy_aces" at the end of the dump. The order of ACEs listed there is the same as it is for the "valid" ACEs (path based or group based). If needed these ACEs can be manually deleted using the "purge_Authorizables" function by entering the respective principal name(s).

Internal dumps which get created and used everytime an new AC installation takes place get also created by this service and contain always all ACEs (users and unfilteredACEs).

