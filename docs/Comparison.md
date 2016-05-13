# Comparison of AC Tool to other approaches

We considered existing solutions before starting our own AC Tool. These are basically content packages (including rep:policy nodes) and the ACL Setup Service provided by AEM.

Aspect | AC Tool | Content Package | ACL Setup Service
------ | ------- | --------------- | -----------------
Readability | :white_check_mark: config can be read by less technical persons | :x: hard to read even for developers | :large_orange_diamond: readable for small setups
Run mode support | :white_check_mark: | :x: | :x:
Setting ACLs for a content position | :white_check_mark: | :large_orange_diamond: if path does not exist, invalid pages are created | :white_check_mark:
Creation of groups possible | :white_check_mark: | :white_check_mark: | :x: 
Order of ACEs is ensured | order of ACEs is ensured | :x: works for initial creation, but not incrementally | :x: works for initial creation, but not incrementally
Old entries can be deleted | :white_check_mark: before applying ACEs to a content node, all entries are removed to ensure the ACL exactly as provided by AC Tool configuration file | :x: old entries are untouched and have to be deleted manually | :x: old entries are untouched and have to be deleted manually
Consistency Checks regarding AC setup | :white_check_mark: | :x: | :x:
Maintainability | :white_check_mark: Single configuration file per project keeps ACL setup in one place. Can be split up to multiple files (e.g. one per tenant). | :x: package with many filter rules and complex structure has to be created | :large_orange_diamond: Everything is kept in one file (OSGi configuration), good for small projects but gets too big for large instances.
Duplication in configuration | :white_check_mark: supports wildcards and loops | :x: all paths have to be contained in package | :x: all paths have to be explicitly listed in OSGi config
Automatic Group Location Migration | :white_check_mark: if the location of a group changes in the config file, the AC Tool automatically migrates the group location and all references to it in the content | :x: all paths in content package have to be changed manually | :x: cannot handle groups
Import/Export | :white_check_mark: import and export of Yaml files | :x: no standard tool in AEM for exporting ACEs but :white_check_mark: [ACL Packager](http://adobe-consulting-services.github.io/acs-aem-commons/features/acl-packager.html) can be used | :x: no export of the effective permissions of an instance
Reproducibility | :white_check_mark: It is possible to ensure that ACL settings in any system are exactly as defined. | :x: Old ACLs are not removed. Therefore, it can only be ensured that the defined ACLs are there but there may be additional ones active as well. | :x: Old ACLs are not removed. Therefore, it can only be ensured that the defined ACLs are there but there may be additional ones active as well.
Availability | :large_orange_diamond: requires installation of additional package | :white_check_mark: part of deployment packages | :white_check_mark: included out-of-the-box