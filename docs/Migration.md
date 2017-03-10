# Migration to AC Tool

Migration to AC Tool is quite easy as it allows to export the AC entries on your existing server. So you can simply install it on an instance that already has the rights that you want. This will work regardless if you managed the entries manually or used some other tool/content package before.

## 1. Install AC Tool

Install the AC Tool packages on your existing server that should be used as source for the AC Tool rules.
Please note that for CQ 5.6 you will need version 1.8.5 of the tool.

## 2. Export rules

Do an export using **groupBasedDump()** on [JMX interface](Jmx.md). The dump will provide you a Yaml export with all AC entries in your system. Save it to a file and remove all entries that should not be managed by AC Tool. E.g. you do not want to manage the system groups such as "everyone" and "administrators". 

## 3. Add rules to your content package

Add the config file to your [content package](Configuration.md) and use the [install hook](ApplyConfig.md) to install the rules during package installation.

## 4. Install the package

Install your package on a clean instance and verify your rules. Take a look at the output of the package installation if there occurred any problems.

## Next steps

Now that your rules are working you can take a look at our [best practices](BestPractices.md) to restructure the rules using multiple files and folders.