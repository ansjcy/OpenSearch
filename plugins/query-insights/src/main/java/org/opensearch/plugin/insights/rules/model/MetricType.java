/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.rules.model;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.rest.RestStatus;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public enum MetricType {
    LATENCY("latency"),
    CPU("cpu"),
    JVM("jvm");

    /**
     * Latency metric type, used by top queries by latency
     */


    private final String name;

    public static MetricType readFromStream(StreamInput in) throws IOException {
        return MetricType.valueOf(in.readString());
    }

    public static void writeTo(StreamOutput out, MetricType metricType) throws IOException {
        out.writeString(metricType.getName());
    }

    MetricType(String name) {
        this.name = name;
    }

    /**
     * Get the metric name of the Metric
     * @return the metric name
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get all valid metrics
     * @return A set of String that contains all valid metrics
     */
    public static Set<MetricType> allMetricTypes() {
        return Arrays.stream(values()).collect(Collectors.toSet());
    }
}
