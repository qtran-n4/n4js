#!/bin/bash

set -e

echo "Start publishing n4js-libs and n4js-cli"

function echo_exec {
    echo "$@"
    $@
}

# The first parameter is can be local, staging or public
if [ -z "$1" ]; then
	echo "The destination (local, staging or public) must be specified as the first parameter"
	exit 1
else
	export DESTINATION=$1
fi

# The second parameter is the port, if not exists default to npmjs
if [ -z "$2" ]; then
	export NPM_REGISTRY_PORT=80
else
	export NPM_REGISTRY_PORT=$2
fi

# Navigate to n4js-libs folder
cd `dirname $0`/n4js-libs

rm -rf node_modules

# Install dependencies and prepare npm task scripts
echo "=== Install dependencies and prepare npm task scripts"
npm install

# Build n4js-cli lib
echo "=== Run lerna run build to build n4js-cli"
lerna run build

# Run npm task script 'publish-canary' to publish n4js-libs and n4js-cli to NPM_REGISTRY
yarn run publish-canary $DESTINATION $NPM_REGISTRY_PORT

echo "End publishing n4js-libs and n4js-cli"
