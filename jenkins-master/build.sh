#!/bin/bash
set -e
cd "$(dirname "$0")"
jq '.builders[0].tags.Commit = "'"$(git rev-parse HEAD)"'"' jenkins-master.json > packer-versioned.json
packer build packer-versioned.json
rm packer-versioned.json