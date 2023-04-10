#!/bin/bash

set -eux

docker-compose -p esps --env-file VERSION.txt -f docker/docker-compose.yml up -d

# Wait for Elasticsearch to start
while ! timeout 1 bash -c "curl -fs http://127.0.0.1:9200" &> /dev/null; do sleep 5; done;
