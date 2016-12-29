# Advanced Scoring with Elasticsearch Similarity Plugins

## What are Plugins

> Plugins are a way to enhance the core Elasticsearch functionality in a custom manner.

https://www.elastic.co/guide/en/elasticsearch/plugins/2.4/index.html


## What is Similarity

>A **similarity** (scoring/ranking model) defines how matching documents are scored. Similarity is per field, meaning that via the mapping one can define a different similarity per field.
>
>Configuring a custom similarity is considered an expert feature and the builtin similarities are most likely sufficient.

https://www.elastic.co/guide/en/elasticsearch/reference/2.4/index-modules-similarity.html#default-similarity


## Scoring Formula

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

Let's index some documents, run a match query and look at explanation.

## Create Elasticsearch Index

```bash
curl -s -XDELETE "http://localhost:9200/test_index"

curl -s -XPUT "http://localhost:9200/test_index" -d '
{
  "settings": {
    "index": {
      "number_of_shards": 1,
      "number_of_replicas": 0
    }
  }
}
'
```

## Create Type Mapping

```bash
curl -XPUT 'localhost:9200/test_index/test_type/_mapping' -d '
{
  "test_type": {
    "properties": {
      "field1": {
        "type": "string"
      }
    }
  }
}
'
```

## Index Documents

```bash
curl -s -XPUT "localhost:9200/test_index/test_type/1" -d '
{"field1" : "foo bar"}
'

curl -s -XPUT "localhost:9200/test_index/test_type/2" -d '
{"field1" : "foo foo bar bar bar"}
'

curl -s -XPUT "localhost:9200/test_index/test_type/3" -d '
{"field1" : "bar bar foo foo"}
'

curl -s -XPOST "http://localhost:9200/test_index/_refresh"
```

doc id|foo freq|doc length
------|--------|----------
1|1|2
2|2|5
3|2|4


## Match Query

```bash
curl -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
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
    "max_score" : 0.5036848,
    "hits" : [ {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "3",
      "_score" : 0.5036848,
      "_source" : {
        "field1" : "bar bar foo foo"
      }
    }, {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "1",
      "_score" : 0.4451987,
      "_source" : {
        "field1" : "foo bar"
      }
    }, {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "2",
      "_score" : 0.44072422,
      "_source" : {
        "field1" : "foo foo bar bar bar"
      }
    } ]
  }
}
```

- Document 3 has higher frequency than Document 1 and lower length than Document 2.

- Document 1 has lower frequency than Document 2 but it also has lower length than Document 2.


## Match Query Explanation

Note, that explanation is part of Lucene API and doc mentioned in explanation is a Lucene document id and it has nothing to do with Elacticsearch _id field.

```json
{
  "value": 0.5036848,
  "description": "weight(field1:foo in 2) [PerFieldSimilarity], result of:",
  "details": [
    {
      "value": 0.5036848,
      "description": "fieldWeight in 2, product of:",
      "details": [
        {
          "value": 1.4142135,
          "description": "tf(freq=2.0), with freq of:",
          "details": [
            {
              "value": 2.0,
              "description": "termFreq=2.0",
              "details": []
            }
          ]
        },
        {
          "value": 0.71231794,
          "description": "idf(docFreq=3, maxDocs=3)",
          "details": []
        },
        {
          "value": 0.5,
          "description": "fieldNorm(doc=2)",
          "details": []
        }
      ]
    }
  ]
}
```


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
        PositionStats positionStats = (PositionStats) weight;
        return new PositionSimScorer(positionStats, context);
    }
}
```

## PositionStats extends SimWeight

The first class that we need to implement will extend SimWeight. This class has a very simple implementation. We will use it to pass any necessary parameters into PositionSimScorer.


```java
private static class PositionStats extends SimWeight {
    private final String field;
    private final TermStatistics[] termStats;
    private float totalBoost;

    public PositionStats(String field, TermStatistics... termStats) {
        this.field = field;
        this.termStats = termStats;
    }

    @Override
    public float getValueForNormalization() {
        // do not use any query normalization
        return 1.0f;
    }

    @Override
    public void normalize(float queryNorm, float boost) {
        this.totalBoost = queryNorm * boost;
    }
}
```


## PositionSimScorer extends SimScorer

The second class will extend SimScorer and will allow us to compute custom score by overriding `score` method.
The actual implementation is available at https://github.com/sdauletau/elasticsearch-position-similarity/blob/master/src/main/java/org/elasticsearch/index/similarity/PositionSimilarity.java.


```java
private final class PositionSimScorer extends SimScorer {
    private final PositionStats stats;
    private final LeafReaderContext context;
    private final List<Explanation> explanations = new ArrayList<>();

    PositionSimScorer(PositionStats stats, LeafReaderContext context) throws IOException {
        this.stats = stats;
        this.context = context;
    }

    @Override
    public float score(int doc, float freq) {
        // calculate score
        // return score
    }

    @Override
    public float computeSlopFactor(int distance) {
        return 1.0f / (distance + 1);
    }

    @Override
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
    private final PositionSimilarity similarity;

    @Inject
    public PositionSimilarityProvider(@Assisted String name, @Assisted Settings settings) {
        super(name);
        this.similarity = new PositionSimilarity();
    }

    public PositionSimilarity get() {
        return similarity;
    }
}
```

## PositionSimilarityPlugin extends Plugin

```java
public class PositionSimilarityPlugin extends Plugin {
    @Override
    public String name() {
        return "position-similarity";
    }

    @Override
    public String description() {
        return "position-similarity plugin";
    }

    public void onModule(SimilarityModule module) {
        module.addSimilarity("position-similarity", PositionSimilarityProvider.class);
    }
}
```

## Build and Install Plugin

```bash
git clone -b 2.4.3 https://github.com/sdauletau/elasticsearch-position-similarity.git elasticsearch-position-similarity

cd elasticsearch-position-similarity

mvn clean package

/usr/local/opt/elasticsearch-2.4.3/bin/plugin install file:./target/releases/elasticsearch-position-similarity-2.4.3.zip
```

**IMPORTANT**: Restart Elasticsearch.


## Create Elasticsearch Index

```bash
curl -s -XDELETE "http://localhost:9200/test_index"

curl -s -XPUT "http://localhost:9200/test_index" -d '
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
'
```

## Create Type Mapping

```bash
curl -XPUT 'localhost:9200/test_index/test_type/_mapping' -d '
{
  "test_type": {
    "properties": {
      "field1": {
        "type": "string"
      },
      "field2": {
        "type": "string",
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
curl -s -XPUT "localhost:9200/test_index/test_type/1" -d '
{"field1" : "bar foo", "field2" : "bar|0 foo|1"}
'

curl -s -XPUT "localhost:9200/test_index/test_type/2" -d '
{"field1" : "foo foo bar bar bar", "field2" : "foo|0 foo|1 bar|3 bar|4 bar|5"}
'

curl -s -XPUT "localhost:9200/test_index/test_type/3" -d '
{"field1" : "bar bar foo foo", "field2" : "bar|0 bar|1 foo|2 foo|3"}
'

curl -s -XPOST "http://localhost:9200/test_index/_refresh"
```

doc id|foo freq|doc length|foo position
------|--------|----------|------------
1|1|2|1
2|2|5|0
3|2|4|2


## Match Query

```bash
curl -s "localhost:9200/test_index/test_type/_search?pretty=true" -d '
{
  "explain": false,
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
  "hits" : [ {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "2",
      "_score" : 1.0,
      "_source" : {
        "field1" : "foo foo bar bar bar",
        "field2" : "foo|0 foo|1 bar|3 bar|4 bar|5"
      }
    }, {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "1",
      "_score" : 0.8333333,
      "_source" : {
        "field1" : "bar foo",
        "field2" : "bar|0 foo|1"
      }
    }, {
      "_index" : "test_index",
      "_type" : "test_type",
      "_id" : "3",
      "_score" : 0.71428573,
      "_source" : {
        "field1" : "bar bar foo foo",
        "field2" : "bar|0 bar|1 foo|2 foo|3"
      }
    } ]
  }
}
```

- ~~Document 3 has higher frequency than Document 1 and lower length than Document 2.~~

- ~~Document 1 has lower frequency than Document 2 but it also has lower length than Document 2.~~

- Document 2 has lowest position.


## Match Query Explanation

Note, that explanation is part of Lucene API and doc mentioned in explanation is a Lucene document id and it has nothing to do with Elacticsearch _id field.

```json
{
  "value": 1.0,
  "description": "weight(field2:foo in 1) [PerFieldSimilarity], result of:",
  "details": [
    {
      "value": 1.0,
      "description": "position score(doc=1, freq=2.0), sum of:",
      "details": [
        {
          "value": 1.0,
          "description": "score(boost=1.0, pos=0, func=1.0*5.0/(5.0+0))",
          "details": []
        }
      ]
    }
  ]
}
```
