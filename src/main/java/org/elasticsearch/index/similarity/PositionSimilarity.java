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

package org.elasticsearch.index.similarity;

import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.*;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PositionSimilarity extends Similarity {
    protected final Settings settings;

    public PositionSimilarity(Settings settings) {
        this.settings = settings;
    }

    @Override
    public String toString() {
        return "PositionSimilarity";
    }

    @Override
    public long computeNorm(FieldInvertState state) {
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

    private final class PositionSimScorer extends SimScorer {
        private final PositionStats stats;
        private final LeafReaderContext context;
        private final List<Explanation> explanations = new ArrayList<>();

        PositionSimScorer(PositionStats stats, LeafReaderContext context) throws IOException {
            this.stats = stats;
            this.context = context;
        }

        /**
         *
         * @param doc Document id.
         * @param freq A term frequency. This function ignores it.
         * @return A term score for a field in a document. Depends on a position of the term.
         *         A term with lower position will score higher.
         */
        @Override
        public float score(int doc, float freq) {
            float totalScore = 0.0f;
            int i = 0;
            while (i < stats.termStats.length) {
                totalScore += scoreTerm(doc, stats.termStats[i].term());
                i++;
            }
            return totalScore;
        }

        private float scoreTerm(int doc, BytesRef term) {
            float halfScorePosition = 5.0f; // position where score should decrease by 50%
            int termPosition = position(doc, term);
            float termScore = stats.totalBoost * halfScorePosition / (halfScorePosition + termPosition);

            String func = stats.totalBoost + "*" + halfScorePosition + "/(" + halfScorePosition + "+" + termPosition + ")";
            explanations.add(
                Explanation.match(
                    termScore,
                    "score(boost=" + stats.totalBoost +", pos=" + termPosition + ", func=" + func + ")"
                )
            );

            return termScore;
        }

        private int position(int doc, BytesRef term) {
            int maxPosition = 20;
            try {
                Terms terms = context.reader().getTermVector(doc, stats.field);
                TermsEnum termsEnum = terms.iterator();
                if (!termsEnum.seekExact(term)) {
                    Loggers.getLogger(this.getClass()).error("seekExact failed, returning default position = " + maxPosition + " in field = " + stats.field);
                    return maxPosition;
                }
                PostingsEnum dpEnum = termsEnum.postings(null, PostingsEnum.ALL);
                dpEnum.nextDoc();
                dpEnum.nextPosition();
                BytesRef payload = dpEnum.getPayload();
                if (payload == null) {
                    Loggers.getLogger(this.getClass()).error("getPayload failed, returning default position = " + maxPosition + " in field = " + stats.field);
                    return maxPosition;
                }
                return PayloadHelper.decodeInt(payload.bytes, payload.offset);
            } catch (Exception ex) {
                Loggers.getLogger(this.getClass()).error("Unexpected exception, returning default position = " + maxPosition + " in field = " + stats.field, ex);
                return maxPosition;
            }
        }

        @Override
        public float computeSlopFactor(int distance) {
            return 1.0f / (distance + 1);
        }

        @Override
        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return 1.0f;
        }

        @Override
        public Explanation explain(int doc, Explanation freq) {
            return Explanation.match(
                    score(doc, freq.getValue()),
                    "position score(doc=" + doc + ", freq=" + freq.getValue() + "), sum of:",
                    explanations
            );
        }
    }

    private static class PositionStats extends SimWeight {
        private final String field;
        private final TermStatistics[] termStats;
        private float totalBoost;

        public PositionStats(String field, TermStatistics... termStats) {
            this.field = field;
            this.termStats = termStats;
        }

        /** The value for normalization of contained query clauses (e.g. sum of squared weights).
         * <p>
         * NOTE: a Similarity implementation might not use any query normalization at all,
         * it's not required. However, if it wants to participate in query normalization,
         * it can return a value here.
         */
        @Override
        public float getValueForNormalization() {
            // do not use any query normalization
            return 1.0f;
        }

        /** Assigns the query normalization factor and boost from parent queries to this.
         * <p>
         * NOTE: a Similarity implementation might not use this normalized value at all,
         * it's not required. However, it's usually a good idea to at least incorporate
         * the boost into its score.
         */
        @Override
        public void normalize(float queryNorm, float boost) {
            this.totalBoost = queryNorm * boost;
        }
    }
}
