/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package org.elasticsearch.index.query;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Weight;

import java.io.IOException;
import java.util.Set;

public class PositionMatchWeight extends Weight {
    final Weight weight;

    PositionMatchWeight(Query query, Weight weight) {
        super(query);
        this.weight = weight;
    }

    @Override
    public void extractTerms(Set<Term> terms) {
        weight.extractTerms(terms);
    }

    @Override
    public Explanation explain(LeafReaderContext context, int docID) throws IOException {
        return scorer(context).explain(docID);
    }

    @Override
    public PositionMatchScorer scorer(LeafReaderContext context) throws IOException {
        return new PositionMatchScorer(this, context);
    }

    @Override
    public boolean isCacheable(LeafReaderContext context) {
        return false; //weight.isCacheable(context);
    }
}
