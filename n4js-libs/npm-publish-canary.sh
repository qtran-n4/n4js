#!/bin/bash
#
# Copyright (c) 2018 NumberFour AG.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Public License v1.0
# which accompanies this distribution, and is available at
# http://www.eclipse.org/legal/epl-v10.html
#
# Contributors:
#   NumberFour AG - Initial API and implementation
#
set -e

cd `dirname $0`
cd `pwd -P`

DIR_ROOT=`pwd`

echo "We are currently in $PWD"
export N4_N4JSC_JAR="${DIR_ROOT}/tools/org.eclipse.n4js.hlc/target/n4jsc.jar"

# DIRS contains all direct folders of packages
DIRS=$(find ./packages/ -type d -mindepth 1 -maxdepth 1)

function echo_exec {
    echo "$@"
    $@
}

cleanup() {
	set +e
	# Remove node_modules before and after publishing
	for dir in $DIRS
	do
		rm -rf "$dir/node_modules"
	done
	rm -rf "${DIR_ROOT}/node_modules"
	set -e
}

cleanup

# Turn http://localhost:4873 -> localhost:4873
NPM_REGISTRY_WITHOUT_PROTOCOL=$(echo ${NPM_REGISTRY} | awk -F"//" '{print $2}')

export NPM_CONFIG_GLOBALCONFIG="DIR_ROOT"

echo "Publishing using .npmrc configuration to ${NPM_REGISTRY}";
lerna publish --loglevel silly --skip-git --registry="${NPM_REGISTRY}" --exact --canary --yes --sort --npm-tag="${NPM_TAG}"
cleanup