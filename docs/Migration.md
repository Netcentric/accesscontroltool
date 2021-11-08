# Migration to AC Tool

Migration to AC Tool is quite easy as it allows to export the AC entries on your existing server. So you can simply install it on an instance that already has the rights that you want. This will work regardless if you managed the entries manually or used some other tool/content package before.

## 1. Install AC Tool

See [Installation](Installation.md) on how to introduce the tool to either on-prem or cloud service AEM instances.

## 2. Export groups and ACLs

Do an export using **groupBasedDump()** on [JMX interface](Jmx.md). The dump will provide you a Yaml export with all AC entries in your system. Save it to a file and remove all entries that should not be managed by AC Tool. For instance you do not want to manage the system groups such as `everyone` and `administrators`. For most non-minimal setups it then also makes sense to split up the one dump file in multiple files for better maintainability.

## 3. Create a permissions package in source control

Create a permissions package in source control/maven and add the config file(s) to it along with the [install hook](ApplyConfig.md) to install the rules during package installation.

## 4. Install the Permissions package

Install your package on a clean instance and verify your rules. Take a look at the output of the package installation if there occurred any problems. 

## Next steps

Now that your rules are working you can take a look at our [best practices](BestPractices.md) to restructure the rules using multiple files and folders.