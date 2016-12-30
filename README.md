<!--
  Title: Elasticsearch term position similarity (aka boost by position) plugin
  Description: Elasticsearch plugin to boost search relevance by a position of a term.
  Author: Sergei Dauletau
  -->
  
# Elasticsearch term position similarity plugin

This plugin allows to calculate score using matching term position so that terms closer to the beginning of a field have higher scores.

## Build

mvn clean package

## Install

Run ./scripts/install-plugin.sh

Re-start elasticsearch

## Examples

Run ./examples/position-similarity.sh

## Implementation Details

https://github.com/sdauletau/elasticsearch-position-similarity/blob/master/Advanced%20Scoring%20with%20Similarity%20Plugins.md
