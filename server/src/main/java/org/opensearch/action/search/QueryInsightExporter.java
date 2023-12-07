/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.action.search;

import org.opensearch.common.unit.TimeValue;

import java.util.List;

/**
 * Simple abstract class to export data collected by search query analyzers
 * <p>
 * Mainly for use within the Query Insight framework
 *
 * @opensearch.internal
 */
abstract class QueryInsightExporter<T extends SearchQueryRecord<?>> {

    private boolean enabled = false;

    /** The export interval of this exporter, default to 60 seconds */
    private TimeValue exportInterval = TimeValue.timeValueSeconds(60);

    /**
     * Initial set up the exporter
     */
    abstract void setup() throws Exception;

    /**
     * Export the data with the exporter.
     *
     * @param records the data to export
     */
    abstract void export(List<T> records) throws Exception;

    public boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public TimeValue getExportInterval() {
        return exportInterval;
    }

    /**
     * Set the export interval for the exporter.
     *
     * @param interval export interval
     */
    public void setExportInterval(TimeValue interval) {
        this.exportInterval = interval;
    }
}
