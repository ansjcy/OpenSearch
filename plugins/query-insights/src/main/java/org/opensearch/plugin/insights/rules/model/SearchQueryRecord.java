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
import org.opensearch.core.common.io.stream.Writeable;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.ToXContentObject;
import org.opensearch.core.xcontent.XContentBuilder;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

/**
 * Simple abstract class that represent record stored in the Query Insight Framework
 *
 * @opensearch.internal
 */
public class SearchQueryRecord implements ToXContentObject, Writeable {
    private final Long timestamp;
    private final Map<MetricType, Measurement<Number>> measurements;
    private final Map<Attribute, Object> attributes;

    public SearchQueryRecord(final StreamInput in) throws IOException, ClassCastException {
        this.timestamp = in.readLong();
        this.measurements = in.readMap(MetricType::readFromStream, Measurement::new);
        this.attributes = in.readMap(Attribute::readFromStream, StreamInput::readGenericValue);
    }
    /**
     * Constructor of the SearchQueryRecord
     *
//     * @param timestamp The timestamp of the query.
//     * @param searchType The manner at which the search operation is executed. see {@link SearchType}
//     * @param source The search source that was executed by the query.
//     * @param totalShards Total number of shards as part of the search query across all indices
//     * @param indices The indices involved in the search query
//     * @param propertyMap Extra attributes and information about a search query
//     * @param value The value on this SearchQueryRecord
     */
    public SearchQueryRecord(
        final Long timestamp,
        Map<MetricType, Measurement<Number>> measurements,
        Map<Attribute, Object> attributes
        ) {
        if (measurements == null) {
            throw new IllegalArgumentException("Measurements cannot be null");
        }

        this.measurements = measurements;
        this.attributes = attributes;
        this.timestamp = timestamp;
    }

    /**
     * Returns the observation time of the metric.
     *
     * @return the observation time in milliseconds
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * Returns the measurement associated with the specified name.
     *
     * @param name the name of the measurement
     * @return the measurement object, or null if not found
     */
    public Measurement<Number> getMeasurement(MetricType name) {
        return measurements.get(name);
    }

    /**
     * Returns an unmodifiable map of all the measurements associated with the metric.
     *
     * @return an unmodifiable map of measurement names to measurement objects
     */
    public Map<MetricType, Measurement<Number>> getMeasurements() {
        return measurements;
    }

    /**
     * Returns an unmodifiable map of the attributes associated with the metric.
     *
     * @return an unmodifiable map of attribute keys to attribute values
     */
    public Map<Attribute, Object> getAttributes() {
        return attributes;
    }

    /**
     * Compare if two SearchQueryRecord are equal
     * @param other The Other SearchQueryRecord to compare to
     * @return boolean
     */
    public boolean equals(SearchQueryRecord other) {
        return this.timestamp.equals(other.getTimestamp())
            && this.attributes.equals(other.getAttributes())
            && this.measurements.equals(other.getMeasurements());
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        builder.startObject();
        for (Map.Entry<Attribute, Object> entry : attributes.entrySet()) {
            builder.field(entry.getKey().getName(), entry.getValue());
        }
        for (Map.Entry<MetricType, Measurement<Number>> entry : measurements.entrySet()) {
            builder.field(entry.getKey().getName(), entry.getValue().getValue());
        }
        return builder.endObject();
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeLong(timestamp);
        out.writeMap(
            measurements,
            (stream, metricType) -> MetricType.writeTo(out, metricType),
            (stream, measurement) -> Measurement.writeTo(out, measurement)
        );
        out.writeMap(
            attributes,
            (stream, attribute) -> Attribute.writeTo(out, attribute),
            StreamOutput::writeGenericValue
        );
    }

    public static int compare(SearchQueryRecord a, SearchQueryRecord b, MetricType metricType) {
        if (metricType == MetricType.LATENCY) {
            return Long.compare(a.getMeasurement(metricType).getValue().longValue(), b.getMeasurement(metricType).getValue().longValue());
        } else if (metricType == MetricType.CPU) {
            return Double.compare(a.getMeasurement(metricType).getValue().doubleValue(), b.getMeasurement(metricType).getValue().doubleValue());
        } else if (metricType == MetricType.JVM) {
            return Double.compare(a.getMeasurement(metricType).getValue().doubleValue(), b.getMeasurement(metricType).getValue().doubleValue());
        } else {
            throw new IllegalArgumentException(String.format(Locale.ROOT, "Invalid metric type %s", metricType));
        }
    }

}
