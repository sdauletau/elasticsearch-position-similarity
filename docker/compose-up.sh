#!/bin/sh

set -eux

docker-compose -p esps --env-file VERSION.txt -f docker/docker-compose.yml up -d
sleep 10
