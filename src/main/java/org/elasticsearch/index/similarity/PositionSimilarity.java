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

import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.index.*;
import org.apache.lucene.search.CollectionStatistics;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.TermStatistics;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.Version;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.script.ScriptService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PositionSimilarity extends Similarity {
    public PositionSimilarity(Settings settings, Version version, ScriptService scriptService) {
    }

    public long computeNorm(FieldInvertState state) {
        // ignore field boost and length during indexing
        return 1;
    }

    public SimWeight computeWeight(float boost, CollectionStatistics collectionStats, TermStatistics... termStats) {
        return new PositionWeight(boost, collectionStats, termStats);
    }

    public final SimScorer simScorer(SimWeight weight, LeafReaderContext context) throws IOException {
        PositionWeight positionWeight = (PositionWeight) weight;
        return new PositionScorer(positionWeight, context);
    }

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

    private final class PositionScorer extends SimScorer {
        private final PositionWeight weight;
        private final LeafReaderContext context;
        private final List<Explanation> explanations = new ArrayList<>();

        PositionScorer(PositionWeight weight, LeafReaderContext context) throws IOException {
            this.weight = weight;
            this.context = context;
        }

        /**
         *
         * @param doc Document id.
         * @param freq A term frequency. This function ignores it.
         * @return A term score for a field in a document. Depends on a position of the term.
         *         A term with lower position will score higher.
         */
        public float score(int doc, float freq) {
            float totalScore = 0.0f;
            int i = 0;
            while (i < weight.termStats.length) {
                totalScore += scoreTerm(doc, weight.termStats[i].term());
                i++;
            }
            return totalScore;
        }

        private float scoreTerm(int doc, BytesRef term) {
            float halfScorePosition = 5.0f; // position where score should decrease by 50%
            int termPosition = position(doc, term);
            float termScore = weight.boost * halfScorePosition / (halfScorePosition + termPosition);

            String func = weight.boost + "*" + halfScorePosition + "/(" + halfScorePosition + "+" + termPosition + ")";
            explanations.add(
                Explanation.match(
                    termScore,
                    "score(boost=" + weight.boost +", pos=" + termPosition + ", func=" + func + ")"
                )
            );

            return termScore;
        }

        private int position(int doc, BytesRef term) {
            int maxPosition = 20;
            try {
                Terms terms = context.reader().getTermVector(doc, weight.field);
                if (terms == null) {
                    LogManager.getLogger(this.getClass()).error("getTermVector failed, returning default position = " +
                            maxPosition + " for field = " + weight.field);
                    return maxPosition;
                }
                TermsEnum termsEnum = terms.iterator();
                if (!termsEnum.seekExact(term)) {
                    LogManager.getLogger(this.getClass()).error("seekExact failed, returning default position = " +
                            maxPosition + " for field = " + weight.field);
                    return maxPosition;
                }
                PostingsEnum dpEnum = termsEnum.postings(null, PostingsEnum.ALL);
                dpEnum.nextDoc();
                dpEnum.nextPosition();
                BytesRef payload = dpEnum.getPayload();
                if (payload == null) {
                    LogManager.getLogger(this.getClass()).error("getPayload failed, returning default position = " +
                            maxPosition + " for field = " + weight.field);
                    return maxPosition;
                }
                return PayloadHelper.decodeInt(payload.bytes, payload.offset);
            } catch (UnsupportedOperationException ex) {
                LogManager.getLogger(this.getClass()).error("Unsupported operation, returning default position = " +
                        maxPosition + " for field = " + weight.field, ex);
                return maxPosition;
            } catch (Exception ex) {
                LogManager.getLogger(this.getClass()).error("Unexpected exception, returning default position = " +
                        maxPosition + " for field = " + weight.field, ex);
                return maxPosition;
            }
        }

        public float computeSlopFactor(int distance) {
            return 1.0f / (distance + 1);
        }

        public float computePayloadFactor(int doc, int start, int end, BytesRef payload) {
            return 1.0f;
        }

        public Explanation explain(int doc, Explanation freq) {
            return Explanation.match(
                    score(doc, freq.getValue()),
                    "position score(doc=" + doc + ", freq=" + freq.getValue() + "), sum of:",
                    explanations
            );
        }
    }
}
