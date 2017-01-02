<!--
  title: Elasticsearch term position similarity (aka boost by position) plugin
  description: Elasticsearch custom similarity plugin to calculate score based on term position and payload.
  author: sdauletau
  -->
  
# Elasticsearch term position similarity plugin

Elasticsearch custom similarity plugin to calculate score based on term position and payload so that terms closer to the beginning of a field have higher scores.

## Build

mvn clean package

## Install

Run ./scripts/install-plugin.sh

Re-start elasticsearch

## Examples

Run ./examples/position-similarity.sh

## Implementation Details

https://github.com/sdauletau/elasticsearch-position-similarity/blob/2.4.3/Advanced%20Scoring%20with%20Similarity%20Plugins.md
