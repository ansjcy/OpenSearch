/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.rules.action.top_queries;

import org.opensearch.action.support.nodes.BaseNodeResponse;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.common.Nullable;
import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.plugin.insights.rules.model.MetricType;
import org.opensearch.plugin.insights.rules.model.SearchQueryRecord;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Top Queries by resource usage / latency on a node
 * <p>
 * Mainly used in the top N queries node response workflow.
 *
 * @opensearch.internal
 */
public class TopQueries extends BaseNodeResponse implements ToXContentObject {
    /** The store to keep the top N queries records */
    private final List<SearchQueryRecord> topQueriesRecords;

    /**
     * Create the TopQueries Object from StreamInput
     * @param in A {@link StreamInput} object.
     * @throws IOException IOException
     */
    public TopQueries(StreamInput in) throws IOException {
        super(in);
        topQueriesRecords = in.readList(SearchQueryRecord::new);
    }

    /**
     * Create the TopQueries Object
     * @param node A node that is part of the cluster.
     */
    public TopQueries(
        DiscoveryNode node,
        List<SearchQueryRecord> searchQueryRecords
    ) {
        super(node);
        topQueriesRecords = searchQueryRecords;
//        if (searchQueryRecords != null) {
//            topQueriesRecords = searchQueryRecords.stream().map((v) -> new TopQuery<>(v, metricType)).collect(Collectors.toList());
//        } else {
//            topQueriesRecords = null;
//        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        if (topQueriesRecords != null) {
            for (SearchQueryRecord record : topQueriesRecords) {
                record.toXContent(builder, params);
            }
        }
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        out.writeList(topQueriesRecords);

    }

    /**
     * Get all top queries records
     *
     * @return the top queries records in this node response
     */
    public List<SearchQueryRecord> getTopQueriesRecord() {
        return topQueriesRecords;
    }
}
