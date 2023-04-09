#!/bin/sh

echo

echo 'Delete index'
curl --header "Content-Type:application/json" -s -XDELETE "http://localhost:9200/test_index"
echo
echo

echo 'Create index'
curl --header "Content-Type:application/json" -s -XPUT "http://localhost:9200/test_index" -d '
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0,
      "similarity": {
        "default": {
          "type": "BM25"
        }
      }
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

echo 'Create mappings'
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

echo 'Index documents and refresh index'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/1" -d '
{"field1" : "bar foo", "field2" : "bar|0 foo|1"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/2" -d '
{"field1" : "foo foo bar bar bar", "field2" : "foo|0 foo|1 bar|3 bar|4 bar|5"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/3" -d '
{"field1" : "bar bar foo too", "field2" : "bar|0 bar|1 foo|2 too|3"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/4" -d '
{"field1" : "bar bar too", "field2" : "bar|0 bar|1 too|2"}
'

curl --header "Content-Type:application/json" -s -XPOST "http://localhost:9200/test_index/_refresh"
echo

echo
echo 'Expecting document with _id 4 to have highest score'
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
echo
