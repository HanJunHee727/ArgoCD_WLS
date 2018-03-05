#!/usr/bin/env bash
# Copyright 2018, Oracle Corporation and/or its affiliates.  All rights reserved.
# Licensed under the Universal Permissive License v 1.0 as shown at http://oss.oracle.com/licenses/upl.

if [ ! $# -eq 1 ]; then
  echo "Syntax: ${BASH_SOURCE[0]} <subject alternative names, e.g. DNS:localhost,DNS:mymachine,DNS:mymachine.us.oracle.com,IP:127.0.0.1>"
  exit
fi

script_dir="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

CERT_DIR="${script_dir}/weblogic-operator-cert"
OP_PREFIX="weblogic-operator"
OP_CERT_PEM="${CERT_DIR}/${OP_PREFIX}.cert.pem"
OP_KEY_PEM="${CERT_DIR}/${OP_PREFIX}.key.pem"
SANS=$1

rm -rf ${CERT_DIR}
mkdir ${CERT_DIR}

echo "unit test mock cert pem for sans:${SANS}" > ${OP_CERT_PEM}
echo "unit test mock key pem for sans:${SANS}" > ${OP_KEY_PEM}
