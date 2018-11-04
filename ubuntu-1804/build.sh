#!/bin/bash
set -e
cd "$(dirname "$0")" || exit 1
jq '.builders[0].tags.Commit = "'"$(git rev-parse HEAD)"'"' ubuntu.json > packer-versioned.json
packer build packer-versioned.json
rm packer-versioned.json