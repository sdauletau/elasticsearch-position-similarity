<!--
  Title: Elasticsearch position similarity (aka boost by position) plugin
  Description: Elasticsearch plugin to boost search relevance by a position of a word.
  Author: sdauletau
  -->
  
# Elasticsearch "boost by position" plugin

This plugin allows to boost search relevance by a position of a word in a field.

## Build

mvn clean package

## Install

Run ./scripts/install-plugin.sh

Re-start elasticsearch

## Examples

Run ./examples/position-similarity.sh

## Implementation Details

https://github.com/sdauletau/elasticsearch-position-similarity/blob/2.4.3/Advanced%20Scoring%20with%20Similarity%20Plugins.md
