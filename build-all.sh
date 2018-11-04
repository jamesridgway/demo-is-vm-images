#!/bin/bash
set -e
./ubuntu-1804/build.sh
./jenkins-master/build.sh
./jenkins-slave/build.sh
./rails-base/build.sh

echo -e "\nAll AMIs built successfully!"