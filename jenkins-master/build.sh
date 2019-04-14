#!/bin/bash
set -e
cd "$(dirname "$0")"
packer build -var "commit=$(git rev-parse HEAD)" jenkins-master.json