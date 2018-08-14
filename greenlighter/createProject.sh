#!/usr/bin/bash
appName="$1"
if [ "$#" -eq 0 ]; then
    echo "You must pass at least one argument indicating the name of the project to create (ex: sh createProject.sh MyNewProject)"
else
    mvn archetype:generate -DarchetypeGroupId=com.ociweb -DarchetypeArtifactId=GreenLighter -DarchetypeVersion=0.0.1 -DgroupId=com.javanut.web.project -DartifactId=$1 -Dversion=1.0-SNAPSHOT -Dpackage=com.javanut.web.project.$1 -DinteractiveMode=false
fi
