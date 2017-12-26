#!/bin/sh

version=$(cat ./VERSION.txt)

/usr/local/opt/elasticsearch-${version}/bin/elasticsearch-plugin remove elasticsearch-position-similarity
