#!/bin/bash

set -eux

docker-compose -p esps --env-file VERSION.txt -f docker/docker-compose.yml up -d

while ! timeout 1 bash -c "echo > /dev/tcp/127.0.0.1/9200" &> /dev/null; do sleep 3; done;
