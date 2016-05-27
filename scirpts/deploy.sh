#!/bin/bash

directory=$(pwd)

mvn deploy -DaltDeploymentRepository=java-marmot::default::file:${directory}/repository/