#!/bin/bash
mvn clean package && java -cp target/classes de.codesourcery.camcontrol.Main "$@"
