package org.elasticsearch.index.query;

import org.apache.lucene.index.IndexReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Objects;

public class PositionMatchQuery extends Query {
    public static final String NAME = "position_match";

    private final Query query;

    PositionMatchQuery(Query query) {
        this.query = query;
    }

    @Override
    public Query rewrite(IndexReader reader) throws IOException {
        Query newQ = query.rewrite(reader);
        if ((newQ != query)) {
            return new PositionMatchQuery(newQ);
        }
        return super.rewrite(reader);
    }

    @Override
    public String toString(String field) {
        return NAME + "(" + query.toString(field) + ")";
    }

    @Override
    public PositionMatchWeight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost) throws IOException {
        Weight weight = query.createWeight(searcher, ScoreMode.COMPLETE, boost);
        return new PositionMatchWeight(query, weight);
    }

    @Override
    public boolean equals(Object obj) {
        if (!sameClassAs(obj)) {
            return false;
        }
        PositionMatchQuery other  = (PositionMatchQuery) obj;
        return Objects.equals(query, other.query);
    }

    @Override
    public int hashCode() {
        return Objects.hash(classHash(), query);
    }
}
