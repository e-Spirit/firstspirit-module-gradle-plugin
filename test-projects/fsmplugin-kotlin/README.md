This is a gradle fsm module project that can be used as a testbed for the gradlefsmplugin.

It is a completely standalone project, but it uses Gradle composite builds to include the fsmgradleplugin
directly and builds it altogether with this project.

That means you get changes implemented in the fsmgradleplugin project directly propagated into this
project and you can test your new features with it :) 