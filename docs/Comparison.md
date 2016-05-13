# Comparison of AC Tool to other approaches

We considered existing solutions before starting our own AC Tool. These are basically content packages (including rep:policy nodes) and the ACL Setup provided by AEM.

Aspect | AC Tool | Content Package | ACL Setup
------ | ------- | --------------- | ---------
Setting ACLs for a content position | :white_check_mark: | :large_orange_diamond: if path does not exist, invalid pages are created | :white_check_mark:
Creation of groups possible | :white_check_mark: | :white_check_mark: | :x: 
Order of ACEs is ensured | Order of ACEs is ensured | :x: works for initial creation, but not incrementally | :x: works for initial creation, but not incrementally
Old entries can be deleted | :white_check_mark: before applying ACEs to a content node, all entries are removed to ensure the ACL exactly as provided by AC Tool configuration file | :x: old entries are untouched and have to be deleted manually | :x: old entries are untouched and have to be deleted manually