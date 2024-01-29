/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

///**
// * Represents a measurement, as part of a SearchQueryRecord, with a name and a corresponding value.
// *
//// * @param <T> the type of the value
// */

package org.opensearch.plugin.insights.rules.model;

import org.opensearch.core.common.io.stream.StreamInput;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.tasks.resourcetracker.TaskResourceUsage;

import java.io.IOException;

public class Measurement<T> {
    String name;
    T value;

    @SuppressWarnings("unchecked")
    public Measurement(StreamInput in) throws IOException {
        this.name = in.readString();
        this.value = (T) in.readGenericValue();
    }

    public static void writeTo(StreamOutput out, Measurement<?> measurement) throws IOException {
        out.writeString(measurement.getName());
        out.writeGenericValue(measurement.getValue());
    }
    /**
     * Constructs a new Measurement with input name and value.
     *
     * @param name  the name of the measurement
     * @param value the value of the measurement
     */
    public Measurement(String name, T value) {
        this.name = name;
        this.value = value;
    }

    /**
     * Returns the name of the measurement.
     *
     * @return the name of the measurement
     */
    public String getName() {
        return name;
    }

    /**
     * Returns the value of the measurement.
     *
     * @return the value of the measurement
     */
    public T getValue() {
        return value;
    }

}
