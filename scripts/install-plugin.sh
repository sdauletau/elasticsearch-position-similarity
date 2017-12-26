#!/bin/sh

version=$(cat ./VERSION.txt)

/usr/local/opt/elasticsearch-${version}/bin/elasticsearch-plugin install file:///`pwd`/build/distributions/elasticsearch-position-similarity-${version}.zip
