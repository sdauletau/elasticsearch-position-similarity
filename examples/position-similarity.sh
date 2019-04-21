#!/bin/sh

echo

echo 'delete index'
curl --header "Content-Type:application/json" -s -XDELETE "http://localhost:9200/test_index"
echo

echo 'create index'
curl --header "Content-Type:application/json" -s -XPUT "http://localhost:9200/test_index" -d '
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    },
    "analysis": {
      "analyzer": {
        "positionPayloadAnalyzer": {
          "type": "custom",
          "tokenizer": "whitespace",
          "filter": [
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
          "type": "delimited_payload"
        }
      }
    }
  }
}
'
echo

echo 'add mappings'
curl --header "Content-Type:application/json" -XPUT 'localhost:9200/test_index/_mapping' -d '
{
  "properties": {
    "field1": {
      "type": "text"
    },
    "field2": {
      "type": "text",
      "term_vector": "with_positions_offsets_payloads",
      "analyzer": "positionPayloadAnalyzer"
    }
  }
}
'
echo

echo 'index doc 1'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/1" -d '
{"field1" : "bar foo", "field2" : "bar|0 foo|1"}
'
echo

echo 'index doc 2'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/2" -d '
{"field1" : "foo foo bar bar bar", "field2" : "foo|0 foo|1 bar|3 bar|4 bar|5"}
'
echo

echo 'index doc 3'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/3" -d '
{"field1" : "bar bar foo too", "field2" : "bar|0 bar|1 foo|2 too|3"}
'
echo

echo 'index doc 4'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/4" -d '
{"field1" : "bar bar too", "field2" : "bar|0 bar|1 too|2"}
'
echo

echo 'refresh index'
curl --header "Content-Type:application/json" -s -XPOST "http://localhost:9200/test_index/_refresh"
echo

echo
echo 'expecting doc 2 to have highest score'
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
  "explain": false,
  "query": {
    "position_match": {
      "query": {
        "match": {
          "field2": "foo"
        }
      }
    }
  }
}
'
echo

echo
echo 'expecting doc 4 to have highest score'
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
  "explain": true,
  "from": 0,
  "size": 1,
  "query": {
    "position_match": {
      "query": {
        "multi_match": {
          "query": "bar too",
          "fields": ["field1","field2"]
        }
      }
    }
  }
}
'
