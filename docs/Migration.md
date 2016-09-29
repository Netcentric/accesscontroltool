# Migration to AC Tool

## Install AC Tool

Install the AC Tool packages on your existing server that should be used as source for the AC Tool rules. 

## Export rules

Do an export using groupBasedDump() on [JMX interface](Jmx.md). The dump will provide you a Yaml export with all AC entries in your system. Save it to a file and remove all entries that should not be managed by AC Tool. E.g. you do not want to manage the system groups such as "everyone" and "administrators". 

## Add rules to your content package

Add the config file to your [content package](Configuration.md) and use the [install hook](ApplyConfig.md) to install the rules during package installation.

## Install the package

Install your package on a clean instance and verify your rules. Take a look at the output of the package installation if there occured any problems.