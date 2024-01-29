/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.core.exporter;

import org.opensearch.common.unit.TimeValue;
import org.opensearch.plugin.insights.rules.model.SearchQueryRecord;
import org.opensearch.plugin.insights.settings.QueryInsightsSettings;
import org.opensearch.threadpool.Scheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Simple abstract class to export data collected by search query analyzers
 * <p>
 * Mainly for use within the Query Insight framework
 *
 * @opensearch.internal
 */
public abstract class QueryInsightsExporter {
    private String identifier;

    /**
     * The export interval of this exporter, default to window size
     */
    private TimeValue exportInterval;
    private TimeValue nextScheduleDelay;

    /**
     * Holds references to delayed operations {@link Scheduler.Cancellable} so it can be cancelled when
     * the service closed concurrently.
     */
    private Scheduler.Cancellable scheduledFuture;

    QueryInsightsExporter(String identifier, TimeValue exportInterval) {
        this.identifier = identifier;
        this.exportInterval = exportInterval;
        calculateNextScheduledDelay();
    }

    /**
     * Export the data with the exporter.
     *
     * @param records the data to export
     */
    public abstract void export(List<SearchQueryRecord> records) throws Exception;

    /**
     * Get the identifier of this exporter
     * @return identifier of this exporter
     */
    public String getIdentifier() {
        return identifier;
    }
    public void setIdentifier(String identifier) {
        this.identifier = identifier;
    }
    public Scheduler.Cancellable getScheduledFuture () {
        return scheduledFuture;
    }
    public void setScheduledFuture(Scheduler.Cancellable scheduledFuture) {
        this.scheduledFuture = scheduledFuture;
    }

    public TimeValue getExportInterval() {
        return exportInterval;
    }

    public void setExportInterval(TimeValue exportInterval) {
        this.exportInterval = exportInterval;
    }

    public void calculateNextScheduledDelay() {
        if (exportInterval.getMinutes() == 0) {
            return;
        }
        LocalDateTime currentTime = LocalDateTime.now();
        LocalDateTime nextScheduleTime = currentTime.truncatedTo(ChronoUnit.HOURS);
        while (!nextScheduleTime.isAfter(currentTime)) {
            nextScheduleTime = nextScheduleTime.plusMinutes(exportInterval.getMinutes());
        }
        Duration delay = Duration.between(
            currentTime,
            nextScheduleTime
        );
        this.nextScheduleDelay = TimeValue.timeValueMillis(delay.toMillis());
    }

    public TimeValue getNextScheduleDelay() {
        return nextScheduleDelay;
    }
}
