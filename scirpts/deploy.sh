#!/bin/bash

directory=$(pwd)

mvn deploy -DaltDeploymentRepository=marmot-support::default::file:${directory}/repository/