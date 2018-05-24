# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

docker pull wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest
docker tag wlsldi-v2.docker.oraclecorp.com/store-weblogic-12.2.1.3:latest store/oracle/weblogic:12.2.1.3
  
docker pull wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest
docker tag wlsldi-v2.docker.oraclecorp.com/store-serverjre-8:latest store/oracle/serverjre:8

#docker rmi -f $(docker images -q -f dangling=true)
docker images --quiet --filter=dangling=true | xargs --no-run-if-empty docker rmi  -f

if [ -z "$BRANCH_NAME" ]; then
  export BRANCH_NAME="`git branch | grep \* | cut -d ' ' -f2-`"
  if [ ! "$?" = "0" ] ; then
  	echo "Error: Could not determine branch.  Run script from within a git repo".
  	exit 1
  fi
fi

export IMAGE_TAG_OPERATOR=${IMAGE_TAG_OPERATOR:-`echo "test_${BRANCH_NAME}" | sed "s#/#_#g"`}
export IMAGE_NAME_OPERATOR=${IMAGE_NAME_OPERATOR:-wlsldi-v2.docker.oraclecorp.com/weblogic-operator}

export SCRIPTPATH="$( cd "$(dirname "$0")" > /dev/null 2>&1 ; pwd -P )"
export PROJECT_ROOT="$SCRIPTPATH/../../../.."
cd $PROJECT_ROOT
if [ $? -ne 0 ]; then
    echo "Couldn't change to $PROJECT_ROOT dir"
    exit 1
fi

echo IMAGE_NAME_OPERATOR $IMAGE_NAME_OPERATOR IMAGE_TAG_OPERATOR $IMAGE_TAG_OPERATOR
docker build -t "${IMAGE_NAME_OPERATOR}:${IMAGE_TAG_OPERATOR}" --no-cache=true .
