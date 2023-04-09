#!/bin/sh

set -eux

/usr/share/elasticsearch/bin/elasticsearch-plugin install file:///${PWD}/build/distributions/elasticsearch-position-similarity.zip
