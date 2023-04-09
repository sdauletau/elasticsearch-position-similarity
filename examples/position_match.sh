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
    }
  }
}
'
echo

echo 'Index documents and refresh index'
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/1" -d '
{"field1" : "bar foo"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/2" -d '
{"field1" : "foo bar bar"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/3" -d '
{"field1" : "bar bar foo foo"}
'

curl --header "Content-Type:application/json" -s -XPOST "http://localhost:9200/test_index/_refresh"
echo

echo
echo 'Expecting document with _id 3 to have highest score'
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
  "explain": true,
  "query": {
    "match": {
      "field1": "foo"
    }
  }
}
'
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
{"field1" : "foo bar bar", "field2" : "foo|0 bar|1 bar|3"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/_doc/3" -d '
{"field1" : "bar bar foo foo", "field2" : "bar|0 bar|1 foo|2 foo|3"}
'

curl --header "Content-Type:application/json" -s -XPOST "http://localhost:9200/test_index/_refresh"
echo

echo
echo 'Expecting document with _id 2 to have highest score'
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
  "explain": true,
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
