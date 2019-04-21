<!--
  title: Advanced Scoring with Elasticsearch Similarity Plugins
  description: Advanced Scoring with Elasticsearch Custom Similarity Plugins
  author: sdauletau
  -->
  
# Advanced Scoring with Elasticsearch Similarity Plugins

## What are Plugins

> Plugins are a way to enhance the core Elasticsearch functionality in a custom manner.

https://www.elastic.co/guide/en/elasticsearch/plugins/current/intro.html


## What is Similarity

>A **similarity** (scoring/ranking model) defines how matching documents are scored. Similarity is per field, meaning that via the mapping one can define a different similarity per field.
>
>Configuring a custom similarity is considered an expert feature and the builtin similarities are most likely sufficient.

https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules-similarity.html

## BM25 Similarity Scoring Formula

**BM25** is a default similarity in Elasticsearch 7.x.

```
score(q,d) =
  ∑ (
      (k1 + 1)
    · idf(t)
    · tf(t in d) / [ tf(t in d) + k1 · (1 - b + b · document_length / avg(document_length)) ]
    ) (t in q)
```

>Let's index some documents, run a match query and look at explanation.

## Create Elasticsearch Index

```bash
curl --header "Content-Type:application/json" -s -XDELETE "http://localhost:9200/test_index"

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
```

## Create Mapping

```bash
curl --header "Content-Type:application/json" -XPUT 'localhost:9200/test_index/_mapping' -d '
{
  "properties": {
    "field1": {
      "type": "text"
    }
  }
}
'
```

## Index Documents

```bash
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
```

doc id|foo freq|doc length
------|--------|----------
1|1|2
2|1|3
3|2|4


## Match Query

```bash
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
  "query": {
    "match": {
      "field1": "foo"
    }
  }
}
'
```


## Match Query Results

```json
{
  "hits" : {
    "total" : {
      "value" : 3,
      "relation" : "eq"
    },
    "max_score" : 0.16786805,
    "hits" : [
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "3",
        "_score" : 0.16786805,
        "_source" : {
          "field1" : "bar bar foo foo"
        }
      },
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "1",
        "_score" : 0.1546153,
        "_source" : {
          "field1" : "bar foo"
        }
      },
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "2",
        "_score" : 0.13353139,
        "_source" : {
          "field1" : "foo bar bar"
        }
      }
    ]
  }
}
```

- Document 3 has the highest score because it has higher foo frequency than Document 1 and Document 2.

- Document 1 and 2 have the same foo frequency but Document 1 has less terms.


## Match Query Explanation

```bash
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
```

Note, that explanation is part of Lucene API and doc mentioned in explanation is a Lucene document id and it has nothing to do with Elacticsearch _id field.

```json
{
  "_explanation": {
    "value": 0.16786805,
    "description": "weight(field1:foo in 2) [PerFieldSimilarity], result of:",
    "details": [
      {
        "value": 0.16786805,
        "description": "score(freq=2.0), product of:",
        "details": [
          {
            "value": 2.2,
            "description": "boost",
            "details": []
          },
          {
            "value": 0.13353139,
            "description": "idf, computed as log(1 + (N - n + 0.5) / (n + 0.5)) from:",
            "details": [
              {
                "value": 3,
                "description": "n, number of documents containing term",
                "details": []
              },
              {
                "value": 3,
                "description": "N, total number of documents with field",
                "details": []
              }
            ]
          },
          {
            "value": 0.5714286,
            "description": "tf, computed as freq / (freq + k1 * (1 - b + b * dl / avgdl)) from:",
            "details": [
              {
                "value": 2,
                "description": "freq, occurrences of term within document",
                "details": []
              },
              {
                "value": 1.2,
                "description": "k1, term saturation parameter",
                "details": []
              },
              {
                "value": 0.75,
                "description": "b, length normalization parameter",
                "details": []
              },
              {
                "value": 4,
                "description": "dl, length of field",
                "details": []
              },
              {
                "value": 3,
                "description": "avgdl, average length of field",
                "details": []
              }
            ]
          }
        ]
      }
    ]
  }
}
```

## We Need a Better Score

The default scoring model works good but the best scoring model will always be application specific.
Let's say that we want to score documents based on a position of a matching term.
For our example, we want to score Document 2 higher than Document 1 and 3.

## Similarity Plugins

Similarity plugins extend Elasticsearch by adding new similarities (scoring/ranking models) to Elasticsearch.

There are several steps necessary to implement a scoring plugin that will **use term positions and payloads** and **ignore term frequency, inverse document frequency and normalization**.

>TODO: Needs explanation

## Build and Install Plugin

```bash
git clone -b 7.0.0 https://github.com/sdauletau/elasticsearch-position-similarity.git elasticsearch-position-similarity

cd elasticsearch-position-similarity

./gradlew clean assemble

/usr/local/opt/elasticsearch-7.0.0/bin/elasticsearch-plugin install file:///`pwd`/build/distributions/elasticsearch-position-similarity-7.0.0.zip
```

**IMPORTANT**: Restart Elasticsearch.


## Create Elasticsearch Index

```bash
curl --header "Content-Type:application/json" -s -XDELETE "http://localhost:9200/test_index"

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
```

## Create Mapping

```bash
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
```

## Index Documents

```bash
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
```

doc id|foo freq|doc length|foo position
------|--------|----------|------------
1|1|2|1
2|1|3|0
3|2|4|2


## Match Query

```bash
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/_search?pretty=true" -d '
{
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
```


## Match Query Results

```json
{
  "hits" : {
    "total" : {
      "value" : 3,
      "relation" : "eq"
    },
    "max_score" : 1.0,
    "hits" : [
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "2",
        "_score" : 1.0,
        "_source" : {
          "field1" : "foo bar bar",
          "field2" : "foo|0 bar|1 bar|3"
        }
      },
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "1",
        "_score" : 0.8333333,
        "_source" : {
          "field1" : "bar foo",
          "field2" : "bar|0 foo|1"
        }
      },
      {
        "_index" : "test_index",
        "_type" : "_doc",
        "_id" : "3",
        "_score" : 0.71428573,
        "_source" : {
          "field1" : "bar bar foo foo",
          "field2" : "bar|0 bar|1 foo|2 foo|3"
        }
      }
    ]
  }
}
```

- Document 2 has the highest score because term foo has the lowest position.


## Match Query Explanation

```bash
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
```

Note, that explanation is part of Lucene API and doc mentioned in explanation is a Lucene document id and it has nothing to do with Elacticsearch _id field.

```json
{
  "_shard": "[test_index][0]",
  "_node": "Raak6LCoRluN_7MJpzKDJA",
  "_index": "test_index",
  "_type": "_doc",
  "_id": "2",
  "_score": 1,
  "_source": {
    "field1": "foo bar bar",
    "field2": "foo|0 bar|1 bar|3"
  },
  "_explanation": {
    "value": 1,
    "description": "score(doc=1), sum of:",
    "details": [
      {
        "value": 1,
        "description": "score(field=field2, term=foo, pos=0, func=5/(5+0))",
        "details": []
      }
    ]
  }
}
```
