# Best Practises

## Split files by project

In case you have multiple projects/teams you should split the config files for them in case groups and ACLs are independent. E.g. both project's content packages can ship with their own files containing only their groups. This makes it easier to update the rights later.

In case groups are shared between projects you should create a common package.

## Split files by topic

Create multiple folders for e.g. service users, generic role definitions and actual roles that are assigned to users. This will help you to find the place where to make changes.


## Create demo users with test content

Since you can also create users it makes perfect sense to add some demo users in your test content package. This way you always have a set of defined users with group permissions installed. Of course, test content must not be installed on production to have no security problem.
