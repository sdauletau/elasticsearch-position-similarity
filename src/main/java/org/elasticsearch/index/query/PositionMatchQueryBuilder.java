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

import org.apache.lucene.search.Query;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;

import java.io.IOException;
import java.util.Objects;

public class PositionMatchQueryBuilder extends AbstractQueryBuilder<PositionMatchQueryBuilder> {
    public static PositionMatchQueryBuilder fromXContent(XContentParser parser) throws IOException {
        QueryBuilder queryBuilder = null;

        XContentParser.Token token;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.START_OBJECT) {
                queryBuilder = parseInnerQueryBuilder(parser);
            }
        }

        return new PositionMatchQueryBuilder(queryBuilder);
    }

    private final QueryBuilder queryBuilder;

    private PositionMatchQueryBuilder(QueryBuilder queryBuilder) {
        if (queryBuilder == null) {
            throw new IllegalArgumentException("Expecting query object in [" + PositionMatchQuery.NAME + "]");
        }
        this.queryBuilder = queryBuilder;
    }

    public PositionMatchQueryBuilder(StreamInput in) throws IOException {
        super(in);
        queryBuilder = in.readNamedWriteable(QueryBuilder.class);
    }

    @Override
    protected void doWriteTo(StreamOutput out) throws IOException {
        out.writeNamedWriteable(queryBuilder);
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(PositionMatchQuery.NAME);
        queryBuilder.toXContent(builder, params);
        printBoostAndQueryName(builder);
        builder.endObject();
    }

    @Override
    protected Query doToQuery(QueryShardContext context) throws IOException {
        Query query = queryBuilder.toQuery(context);
        return new PositionMatchQuery(query);
    }

    @Override
    protected int doHashCode() {
        return Objects.hash(queryBuilder);
    }

    @Override
    protected boolean doEquals(PositionMatchQueryBuilder other) {
        return Objects.equals(queryBuilder, other.queryBuilder);
    }

    @Override
    public String getWriteableName() {
        return PositionMatchQuery.NAME;
    }
}
