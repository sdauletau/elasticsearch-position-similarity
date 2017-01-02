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
