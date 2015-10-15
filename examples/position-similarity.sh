#!/bin/sh

echo &&
curl -s -XDELETE "http://localhost:9200/test" && echo

echo &&
curl -s -XPUT "http://localhost:9200/test" -d '
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "similarity": {
      "positionSimilarity": {
        "type": "position-similarity"
      }
    },
    "analysis": {
      "analyzer": {
        "positionPayloadAnalyzer": {
          "type": "custom",
          "tokenizer": "whitespace",
          "filter": [
            "standard",
            "lowercase",
            "asciifolding",
            "positionPayloadFilter"
          ]
        }
      },
      "filter": {
        "positionPayloadFilter": {
          "delimiter": "|",
          "encoding": "int",
          "type": "delimited_payload_filter"
        }
      }
    }
  }
}
' && echo

echo &&
curl -XPUT 'localhost:9200/test/type1/_mapping' -d '
{
  "type1": {
    "properties": {
      "positionedName": {
        "type": "string",
        "term_vector": "with_positions_offsets_payloads",
        "analyzer": "positionPayloadAnalyzer",
        "similarity": "positionSimilarity"
      },
      "name": {
        "type": "string"
      }
    }
  }
}
' && echo

echo &&
curl -s -XPUT "localhost:9200/test/type1/1" -d '{"name" : "foo bar baz", "positionedName" : "foo|0 bar|1 baz|2"}' && echo
echo &&
curl -s -XPUT "localhost:9200/test/type1/2" -d '{"name" : "foo foo foo", "positionedName" : "foo|0 foo|1 foo|2"}' && echo
echo &&
curl -s -XPUT "localhost:9200/test/type1/3" -d '{"name" : "bar b. baz", "positionedName" : "bar|0 b.|0 baz|1"}' && echo

echo &&
curl -s -XPOST "http://localhost:9200/test/_refresh" && echo

echo &&
echo 'expecting doc 2 with highest score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '
{
  "query": {
    "match": {
      "name": "foo"
    }
  }
}
'

echo &&
echo 'expecting doc 3 with highest score' &&
curl -s "localhost:9200/test/type1/_search?pretty=true" -d '
{
  "explain": false,
  "query": {
    "multi_match": {
      "boost": 3,
      "query": "baz",
      "fields": [
        "positionedName"
      ]
    }
  }
}
'
