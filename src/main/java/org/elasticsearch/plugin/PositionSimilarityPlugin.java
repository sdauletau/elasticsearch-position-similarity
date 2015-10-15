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

package org.elasticsearch.plugin;

import org.elasticsearch.index.similarity.SimilarityModule;
import org.elasticsearch.index.similarity.PositionSimilarityProvider;
import org.elasticsearch.plugins.Plugin;

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
