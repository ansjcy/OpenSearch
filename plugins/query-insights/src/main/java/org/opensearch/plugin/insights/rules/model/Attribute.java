/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.rules.model;

import org.opensearch.action.search.SearchType;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.Map;

public enum Attribute {
    SEARCH_TYPE("searchType", SearchType.class),
    SOURCE("source", String.class),
    TOTAL_SHARDS("totalShards", Integer.class),
    INDICES("indices", String[].class),
    PHASE_LATENCY_MAP("phaseLatencyMap", Map.class);

    public final String name;
    public final Type type;

    public static Attribute readFromStream(StreamInput in) throws IOException {
        return Attribute.valueOf(in.readString());
    }

    public static void writeTo(StreamOutput out, Attribute attribute) throws IOException {
        out.writeString(attribute.getName());
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return this.getName();
    }

    Attribute(String name, Type type) {
        this.name = name;
        this.type = type;
    }
}
