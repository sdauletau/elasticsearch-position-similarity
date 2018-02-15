<!--
  title: Elasticsearch term position similarity (aka boost by position) plugin
  description: Elasticsearch custom similarity plugin to calculate score based on term position and payload.
  author: sdauletau
  -->
  
# Elasticsearch term position similarity plugin

Elasticsearch custom similarity plugin to calculate score based on term position and payload so that terms closer to the beginning of a field have higher scores.

## Build

./gradlew clean assemble

Note, that versions 6.2.x require Java 9.

## Install

Run ./scripts/install-plugin.sh

Re-start elasticsearch

## Examples

Run ./examples/position-similarity.sh

# Advanced Scoring with Elasticsearch Similarity Plugins

## What are Plugins

> Plugins are a way to enhance the core Elasticsearch functionality in a custom manner.

https://www.elastic.co/guide/en/elasticsearch/plugins/current/intro.html


## What is Similarity

>A **similarity** (scoring/ranking model) defines how matching documents are scored. Similarity is per field, meaning that via the mapping one can define a different similarity per field.
>
>Configuring a custom similarity is considered an expert feature and the builtin similarities are most likely sufficient.

https://www.elastic.co/guide/en/elasticsearch/reference/current/index-modules-similarity.html


## Classic Similarity Scoring Formula

```
score(q,d) =
              queryNorm(q)
            · coord(q,d)
            · ∑ (
                  tf(t in d)
                · idf(t)²
                · t.getBoost()
                · norm(t,d)
                ) (t in q)
```

https://www.elastic.co/guide/en/elasticsearch/guide/2.x/practical-scoring-function.html

Note, that we can disable normalization by adding { "norms": false } to a field mappings.

Let's index some documents, run a match query and look at explanation.

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
          "type": "classic"
        }
      }
    }
  }
}
'
```

## Create Type Mapping

```bash
curl --header "Content-Type:application/json" -XPUT 'localhost:9200/test_index/test_type/_mapping' -d '
{
  "test_type": {
    "properties": {
      "field1": {
        "type": "text",
        "norms": false
      }
    }
  }
}
'
```

## Index Documents

```bash
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/1" -d '
{"field1" : "bar foo"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/2" -d '
{"field1" : "foo bar bar"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/3" -d '
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
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
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
  "total" : 3,
  "max_score" : 1.4142135,
  "hits" : [
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "3",
      "_score" : 1.4142135,
      "_source" : {
        "field1" : "bar bar foo foo"
      }
    },
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "1",
      "_score" : 1.0,
      "_source" : {
        "field1" : "bar foo"
      }
    },
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "2",
      "_score" : 1.0,
      "_source" : {
        "field1" : "foo bar bar"
      }
    }
  ]
}
```

- Document 3 has the highest score because it has higher foo frequency than Document 1 and Document 2 and because we ignore length normalization.

- Document 1 and 2 have the same score because they have the same foo frequency and because we ignore length normalization.


## Match Query Explanation

```bash
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
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
  "value" : 1.4142135,
  "description" : "weight(field1:foo in 2) [PerFieldSimilarity], result of:",
  "details" : [
    {
      "value" : 1.4142135,
      "description" : "fieldWeight in 2, product of:",
      "details" : [
        {
          "value" : 1.4142135,
          "description" : "tf(freq=2.0), with freq of:",
          "details" : [
            {
              "value" : 2.0,
              "description" : "termFreq=2.0",
              "details" : [ ]
            }
          ]
        },
        {
          "value" : 1.0,
          "description" : "idf, computed as log((docCount+1)/(docFreq+1)) + 1 from:",
          "details" : [
            {
              "value" : 4.0,
              "description" : "docFreq",
              "details" : [ ]
            },
            {
              "value" : 4.0,
              "description" : "docCount",
              "details" : [ ]
            }
          ]
        },
        {
          "value" : 1.0,
          "description" : "fieldNorm(doc=2)",
          "details" : [ ]
        }
      ]
    }
  ]
}
```

## We Need a Better Score

The default scoring model works good but the best scoring model will always be application specific.
Let's say that we want to score documents based on a position of a matching token.
For our example, we want to score Document 2 higher than Document 1 and 3.

## Similarity Plugins

Similarity plugins extend Elasticsearch by adding new similarities (scoring/ranking models) to Elasticsearch.

There are several steps necessary to implement a scoring plugin that will **use term positions and payloads** and **ignore term frequency, inverse document frequency and normalization**.


## Similarity Class

As you know, Elasticsearch is based on Lucene. We need to look at Lucene source code to understand Lucene scoring.

```java
public abstract class Similarity {
    public Similarity() {}

    public float coord(int overlap, int maxOverlap) { return 1.0F; }
    public float queryNorm(float valueForNormalization) { return 1.0F; }

    public abstract long computeNorm(FieldInvertState fieldInvertState);
    public abstract SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats);
    public abstract SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException;


    public abstract static class SimWeight {
        public SimWeight() {}

        public abstract float getValueForNormalization();
        public abstract void normalize(float queryNorm, float boost);
    }


    public abstract static class SimScorer {
        public SimScorer() {}

        public abstract float score(int doc, float freq);
        public abstract float computeSlopFactor(int distance);
        public abstract float computePayloadFactor(int doc, int start, int end, BytesRef payload);

        public Explanation explain(int doc, Explanation freq) {
            return Explanation.match(
                    this.score(doc, freq.getValue()),
                    "score(doc=" + doc + ",freq=" + freq.getValue() + "), with freq of:",
                    Collections.singleton(freq));
        }
    }
}
```

https://github.com/apache/lucene-solr/blob/master/lucene/core/src/java/org/apache/lucene/search/similarities/Similarity.java

## PositionSimilarity extends Similarity

Our custom plugin will extend abstract Similarity class and it will implement 3 abstract methods and 2 internal abstract classes.

```java
public class PositionSimilarity extends Similarity {
    public PositionSimilarity() {}

    @Override
    public long computeNorm(FieldInvertState fieldInvertState) {
        // ignore field boost and length during indexing
        return 1;
    }

    @Override
    public SimWeight computeWeight(CollectionStatistics collectionStats, TermStatistics... termStats) {
        return new PositionStats(collectionStats.field(), termStats);
    }

    @Override
    public final SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        PositionStats positionScore = (PositionStats) weight;
        return new PositionSimScorer(positionScore, context);
    }
}
```

## PositionWeight extends SimWeight

The first class that we need to implement will extend SimWeight. This class has a very simple implementation. We will use it to pass any necessary parameters into PositionScorer.


```java
private static class PositionWeight extends SimWeight {
    private float boost;
    private final String field;
    private final TermStatistics[] termStats;

    PositionWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        this.boost = boost;
        this.field = collectionStats.field();
        this.termStats = termStats;
    }
}
```


## PositionScorer extends SimScorer

The second class will extend SimScorer and will allow us to compute custom score by overriding `score` method.
The actual implementation is available at https://github.com/sdauletau/elasticsearch-position-similarity/blob/master/src/main/java/org/elasticsearch/index/similarity/PositionSimilarity.java.


```java
private final class PositionScorer extends SimScorer {
    private final PositionWeight weight;
    private final LeafReaderContext context;
    private final List<Explanation> explanations = new ArrayList<>();

    PositionScorer(PositionWeight weight, LeafReaderContext context) throws IOException {
        this.weight = weight;
        this.context = context;
    }

    public float score(int doc, float freq) {
        // calculate score
        // return score
    }

    public float computeSlopFactor(int distance) {
        return 1.0f / (distance + 1);
    }

    public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
        return 1.0f;
    }
}
```


## AbstractSimilarityProvider and Plugin

At this point we need two more classes to implement AbstractSimilarityProvider and Plugin.

## PositionSimilarityProvider extends AbstractSimilarityProvider

```java
public class PositionSimilarityProvider extends AbstractSimilarityProvider {
    private final PositionSimilarity similarity = new PositionSimilarity();

    public PositionSimilarityProvider(String name, Settings settings, Settings indexSettings, ScriptService scriptService) {
        super(name);
    }

    public PositionSimilarity get() {
        return similarity;
    }
}
```

## PositionSimilarityPlugin extends Plugin

```java
public class PositionSimilarityPlugin extends Plugin {
    public String name() {
        return "elasticsearch-position-similarity";
    }

    public void onIndexModule(IndexModule indexModule) {
        indexModule.addSimilarity("position-similarity", PositionSimilarityProvider::new);
    }
}
```

## Build and Install Plugin

```bash
git clone -b 6.1.0 https://github.com/sdauletau/elasticsearch-position-similarity.git elasticsearch-position-similarity

cd elasticsearch-position-similarity

./gradlew clean assemble

/usr/local/opt/elasticsearch-6.1.0/bin/elasticsearch-plugin install file:///`pwd`/build/distributions/elasticsearch-position-similarity-6.1.0.zip
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
          "type": "classic"
        }
      }
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
'
```

## Create Type Mapping

```bash
curl --header "Content-Type:application/json" -XPUT 'localhost:9200/test_index/test_type/_mapping' -d '
{
  "test_type": {
    "properties": {
      "field1": {
        "type": "text",
        "norms": false
      },
      "field2": {
        "type": "text",
        "norms": false,
        "term_vector": "with_positions_offsets_payloads",
        "analyzer": "positionPayloadAnalyzer",
        "similarity": "positionSimilarity"
      }
    }
  }
}
'
```

## Index Documents

```bash
curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/1" -d '
{"field1" : "bar foo", "field2" : "bar|0 foo|1"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/2" -d '
{"field1" : "foo bar bar", "field2" : "foo|0 bar|1 bar|3"}
'

curl --header "Content-Type:application/json" -s -XPUT "localhost:9200/test_index/test_type/3" -d '
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
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
{
  "query": {
    "match": {
      "field2": "foo"
    }
  }
}
'
```


## Match Query Results

```json
{
  "hits" : {
  "total" : 3,
  "max_score" : 1.0,
  "hits" : [
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "2",
      "_score" : 1.0,
      "_source" : {
        "field1" : "foo bar bar",
        "field2" : "foo|0 bar|1 bar|3"
      }
    },
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "1",
      "_score" : 0.8333333,
      "_source" : {
        "field1" : "bar foo",
        "field2" : "bar|0 foo|1"
      }
    },
    {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "3",
      "_score" : 0.71428573,
      "_source" : {
        "field1" : "bar bar foo foo",
        "field2" : "bar|0 bar|1 foo|2 foo|3"
      }
    }
  ]
}
```

- Document 2 has the highest score because foo has the lowest position.


## Match Query Explanation

```bash
curl --header "Content-Type:application/json" -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
{
  "explain": true,
  "query": {
    "match": {
      "field2": "foo"
    }
  }
}
'
```

Note, that explanation is part of Lucene API and doc mentioned in explanation is a Lucene document id and it has nothing to do with Elacticsearch _id field.

```json
{
  "value" : 1.0,
  "description" : "weight(field2:foo in 1) [PerFieldSimilarity], result of:",
  "details" : [
    {
      "value" : 1.0,
      "description" : "position score(doc=1, freq=1.0), sum of:",
      "details" : [
        {
          "value" : 1.0,
          "description" : "score(boost=1.0, pos=0, func=1.0*5.0/(5.0+0))",
          "details" : [ ]
        }
      ]
    }
  ]
}
```
