#!/bin/bash
set -e
cd "$(dirname "$0")" || exit 1
packer build -var "commit=$(git rev-parse HEAD)" ubuntu.json