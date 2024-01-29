/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.core.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.lifecycle.AbstractLifecycleComponent;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.plugin.insights.core.exporter.QueryInsightsExporter;
import org.opensearch.plugin.insights.core.exporter.QueryInsightsExporterType;
import org.opensearch.plugin.insights.core.exporter.QueryInsightsLocalIndexExporter;
import org.opensearch.plugin.insights.rules.model.MetricType;
import org.opensearch.plugin.insights.rules.model.SearchQueryRecord;
import org.opensearch.plugin.insights.settings.QueryInsightsSettings;
import org.opensearch.threadpool.ThreadPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.PriorityBlockingQueue;

import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.DEFAULT_LOCAL_INDEX_MAPPING;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.MIN_EXPORT_INTERVAL;

/**
 * Service responsible for gathering, analyzing, storing and exporting
 * top N queries with high latency data for search queries
 *
 * @opensearch.internal
 */
public class TopQueriesByLatencyService extends AbstractLifecycleComponent {
    private static final Logger log = LogManager.getLogger(TopQueriesByLatencyService.class);

    private static final TimeValue delay = TimeValue.ZERO;

    private int topNSize = QueryInsightsSettings.DEFAULT_TOP_N_SIZE;

    private TimeValue windowSize = TimeValue.timeValueSeconds(QueryInsightsSettings.DEFAULT_WINDOW_SIZE);

    private final ClusterService clusterService;
    private final Client client;

    /**
     * The internal OpenSearch thread pool that execute async processing and exporting tasks
     */
    private final ThreadPool threadPool;

    /**
     * The internal thread-safe store that holds the top n queries by latency query insight data
     */
    private final PriorityBlockingQueue<SearchQueryRecord> topQueriesByLatencyStore;
    /**
     * The internal thread-safe store that holds the top n queries by cpu query insight data
     */
    private final PriorityBlockingQueue<SearchQueryRecord> topQueriesByCpuStore;
    /**
     * The internal thread-safe store that holds the top n queries by jvm query insight data
     */
    private final PriorityBlockingQueue<SearchQueryRecord> topQueriesByJvmStore;

    /**
     * enable insight data collection
     */
    private final Map<MetricType, Boolean> enableCollect = new HashMap<>();

    private final Map<MetricType, Map<QueryInsightsExporterType, QueryInsightsExporter>> exporters = new HashMap<>();

    /**
     * Create the TopQueriesByLatencyService Object
     *
     * @param threadPool     The OpenSearch thread pool to run async tasks
     * @param clusterService The clusterService of this node
     * @param client         The OpenSearch Client
     */
    @Inject
    public TopQueriesByLatencyService(ThreadPool threadPool, ClusterService clusterService, Client client) {
        this.topQueriesByLatencyStore = new PriorityBlockingQueue<>(topNSize, Comparator.comparingLong(a -> a.getMeasurement(MetricType.LATENCY).getValue().longValue()));
        this.topQueriesByCpuStore = new PriorityBlockingQueue<>(topNSize, Comparator.comparingDouble(a -> a.getMeasurement(MetricType.CPU).getValue().doubleValue()));
        this.topQueriesByJvmStore = new PriorityBlockingQueue<>(topNSize, Comparator.comparingDouble(a -> a.getMeasurement(MetricType.JVM).getValue().doubleValue()));
        this.clusterService = clusterService;
        this.client = client;
        this.threadPool = threadPool;
    }

//    /**
//     * Ingest the query data into to the top N queries with latency store
//     *
//     * @param timestamp The timestamp of the query.
//     * @param searchType The manner at which the search operation is executed. see {@link SearchType}
//     * @param source The search source that was executed by the query.
//     * @param totalShards Total number of shards as part of the search query across all indices
//     * @param indices The indices involved in the search query
//     * @param propertyMap Extra attributes and information about a search query
//     * @param phaseLatencyMap Contains phase level latency information in a search query
//     * @param tookInNanos Total search request took time in nanoseconds
//     */
//    public void ingestQueryData(
//        final Long timestamp,
//        final SearchType searchType,
//        final String source,
//        final int totalShards,
//        final String[] indices,
//        final Map<String, Object> propertyMap,
//        final Map<String, Long> phaseLatencyMap,
//        final Long tookInNanos
//    ) {
//        if (timestamp <= 0) {
//            log.error(
//                String.format(
//                    Locale.ROOT,
//                    "Invalid timestamp %s when ingesting query data to compute top n queries with latency",
//                    timestamp
//                )
//            );
//            return;
//        }
//        if (totalShards <= 0) {
//            log.error(
//                String.format(
//                    Locale.ROOT,
//                    "Invalid totalShards %s when ingesting query data to compute top n queries with latency",
//                    totalShards
//                )
//            );
//            return;
//        }
//        this.threadPool.schedule(() -> {
//            clearOutdatedData();
//            super.ingestQueryData(
//                new SearchQueryLatencyRecord(timestamp, searchType, source, totalShards, indices, propertyMap, phaseLatencyMap, tookInNanos)
//            );
//            // remove top elements for fix sizing priority queue
//            if (this.store.size() > this.getTopNSize()) {
//                this.store.poll();
//            }
//        }, delay, ThreadPool.Names.GENERIC);
//
//        log.debug(String.format(Locale.ROOT, "successfully ingested: %s", this.store));
//    }

    private PriorityBlockingQueue<SearchQueryRecord> getStoreByMetricType(MetricType type) {
        if (type == MetricType.LATENCY) {
            return topQueriesByLatencyStore;
        } else if (type == MetricType.CPU) {
            return topQueriesByCpuStore;
        } else if (type == MetricType.JVM) {
            return topQueriesByJvmStore;
        } else {
            throw new IllegalArgumentException(
                "Invalid metricType ["
                    + type
                    + "]"
                    + " should be one of ["
                    + MetricType.allMetricTypes()
                    + "]"
            );
        }
    }
    public void addRecord(
        final MetricType metricType,
        SearchQueryRecord record
    ) {
        PriorityBlockingQueue<SearchQueryRecord> store = getStoreByMetricType(metricType);

        this.threadPool.schedule(() -> {
//            clearOutdatedData(metricType);
            store.add(record);
            // remove top elements for fix sizing priority queue
            if (store.size() > this.getTopNSize()) {
                store.poll();
            }
        }, delay, ThreadPool.Names.GENERIC);

        log.debug(String.format(Locale.ROOT, "successfully ingested record type: %s", metricType));
    }

//    public void clearOutdatedData(MetricType metricType) {
//        PriorityBlockingQueue<SearchQueryRecord> store = getStoreByMetricType(metricType);
//        store.removeIf(record -> record.getTimestamp() < System.currentTimeMillis() - windowSize.getMillis());
//    }

    public void clearAllData(MetricType metricType) {
        PriorityBlockingQueue<SearchQueryRecord> store = getStoreByMetricType(metricType);
        store.clear();
    }

    /**
     * Get all records that are in the query insight store, based on the input MetricType
     * By default, return the records in sorted order.
     *
     * @return List of the records that are in the query insight store
     * @throws IllegalArgumentException if query insight is disabled in the cluster
     */
    public List<SearchQueryRecord> getTopNRecords(MetricType metricType) throws IllegalArgumentException {
        if (!enableCollect.get(metricType)) {
            throw new IllegalArgumentException(String.format( Locale.ROOT, "Cannot get query data when query insight feature is not enabled for MetricType [%s].", metricType));
        }
//        clearOutdatedData(metricType);
        PriorityBlockingQueue<SearchQueryRecord> store = getStoreByMetricType(metricType);
        List<SearchQueryRecord> queries = new ArrayList<>(store);
        queries.sort((a, b) -> SearchQueryRecord.compare(a, b, metricType) * -1);
        return queries;
    }

    /**
     * Set flag to enable or disable Query Insights data collection
     *
     * @param enableCollect Flag to enable or disable Query Insights data collection
     */
    public void setEnableCollect(MetricType type, boolean enableCollect) {
        this.enableCollect.put(type, enableCollect);
    }

    /**
     * Get if the Query Insights data collection is enabled
     *
     * @return if the Query Insights data collection is enabled
     */
    public boolean getEnableCollect(MetricType type) {
        return this.enableCollect.get(type);
    }

    /**
     * Start the Query Insight Service.
     */
    @Override
    protected void doStart() {
        exporters.forEach(((metricType, exporterMap) -> {
            exporterMap.forEach(((exporterType, exporter) -> {
                if (exporter != null && exporter.getScheduledFuture() == null) {
//                    exporter.setScheduledFuture(threadPool.scheduleWithFixedDelay(() -> doExportAndClear(metricType, exporter), exporter.getExportInterval(), ThreadPool.Names.GENERIC));
//                    threadPool.schedule(
//                        () -> exporter.setScheduledFuture(threadPool.scheduleWithFixedDelay(() -> doExportAndClear(metricType, exporter), exporter.getExportInterval(), ThreadPool.Names.GENERIC)),
//                        exporter.getNextScheduleDelay(),
//                        ThreadPool.Names.GENERIC
//                    );
                    startRecurrentExport(exporter, metricType);
                }
            }));
        }));
//        for (Map.Entry<MetricType, Map<QueryInsightsExporterType, QueryInsightsExporter>> metricTypeMapEntry : exporters.entrySet()) {
//            MetricType metricType = metricTypeMapEntry.getKey();
//            Map<QueryInsightsExporterType, QueryInsightsExporter> exporterMap = metricTypeMapEntry.getValue();
//            for (Map.Entry<QueryInsightsExporterType, QueryInsightsExporter> exporterEntry : exporterMap.entrySet()) {
//                QueryInsightsExporterType exporterType = exporterEntry.getKey();
//                QueryInsightsExporter exporter = exporterEntry.getValue();
//                if (exporter != null && getEnableExport(metricType)) {
//                    if (scheduledFutures.get(metricType) == null) {
//                        scheduledFutures.put()
//                    }
//                    scheduledFuture = threadPool.scheduleWithFixedDelay(this::doExport, exportInterval, ThreadPool.Names.GENERIC);
//                }
//            }
//        }
    }

    /**
     * Stop the Query Insight Service
     */
    @Override
    protected void doStop() {
        exporters.forEach(((metricType, exporterMap) -> {
            exporterMap.forEach(((exporterType, exporter) -> {
                if (exporter != null && exporter.getScheduledFuture() != null) {
                    exporter.getScheduledFuture().cancel();
                }
            }));
        }));
//        if (scheduledFuture != null) {
//            scheduledFuture.cancel();
//            if (exporter != null && getEnableExport()) {
//                doExport();
//            }
//        }
    }


    @Override
    protected void doClose() {}

    private void doExportAndClear(MetricType metricType, QueryInsightsExporter exporter) {
        System.out.println(System.currentTimeMillis());
        List<SearchQueryRecord> topNRecords = getTopNRecords(metricType);
        try {
            if (exporter != null) {
                exporter.export(new ArrayList<>(topNRecords));
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format(Locale.ROOT, "failed to export query insight data to sink, error: %s", e));
        } finally {
            clearAllData(metricType);
        }
//        List<R> storedData = getQueryData();
//        try {
//            exporter.export(storedData);
//        } catch (Exception e) {
//            throw new RuntimeException(String.format(Locale.ROOT, "failed to export query insight data to sink, error: %s", e));
//        }
    }


    private void doDeleteOneExporter(MetricType metricType, QueryInsightsExporterType exporterType) {
        if (getExporter(metricType, exporterType) == null) {
            return;
        }
        QueryInsightsExporter exporter = exporters.get(metricType).get(exporterType);
        if (exporter.getScheduledFuture() != null) {
            exporter.getScheduledFuture().cancel();
        }
        exporters.get(metricType).remove(exporterType);
    }

    private void doCreateOneExporter(MetricType metricType, QueryInsightsExporterType exporterType, String identifier, TimeValue exportInterval) {
        doDeleteOneExporter(metricType, exporterType);
        if (!exporters.containsKey(metricType)) {
            exporters.put(metricType, new HashMap<>());
        }
        QueryInsightsExporter exporter = new QueryInsightsLocalIndexExporter(
            clusterService,
            client,
            identifier,
            exportInterval,
            TopQueriesByLatencyService.class.getClassLoader().getResourceAsStream(DEFAULT_LOCAL_INDEX_MAPPING)
        );
        if (exporterType.equals(QueryInsightsExporterType.LOCAL_INDEX)) {
            exporters.get(metricType).put(
                QueryInsightsExporterType.LOCAL_INDEX,
                exporter
            );
        }
        startRecurrentExport(exporter, metricType);
//        threadPool.schedule(
//            () -> exporter.setScheduledFuture(threadPool.scheduleWithFixedDelay(() -> doExportAndClear(metricType, exporter), exporter.getExportInterval(), ThreadPool.Names.GENERIC)),
//            exporter.getNextScheduleDelay(),
//            ThreadPool.Names.GENERIC
//        );
//        exporter.setScheduledFuture(threadPool.scheduleWithFixedDelay(() -> doExportAndClear(metricType, exporter), exportInterval, ThreadPool.Names.GENERIC));
    }

    private void startRecurrentExport(QueryInsightsExporter exporter, MetricType metricType) {
        threadPool.schedule(
            () -> {
                doExportAndClear(metricType, exporter);
                exporter.setScheduledFuture(threadPool.scheduleWithFixedDelay(() -> doExportAndClear(metricType, exporter), exporter.getExportInterval(), ThreadPool.Names.GENERIC));
            },
            exporter.getNextScheduleDelay(),
            ThreadPool.Names.GENERIC
        );
    }

    public QueryInsightsExporter getExporter(MetricType metricType, QueryInsightsExporterType exporterType) {
        if (!exporters.containsKey(metricType)) {
            return null;
        }
        return exporters.get(metricType).get(exporterType);
    }

    /**
     * Set the top N size for TopQueriesByLatencyService service.
     *
     * @param size the top N size to set
     */
    public void setTopNSize(int size) {
        this.topNSize = size;
    }

    /**
     * Validate the top N size based on the internal constrains
     *
     * @param size the wanted top N size
     */
    public void validateTopNSize(int size) {
        if (size > QueryInsightsSettings.MAX_N_SIZE) {
            throw new IllegalArgumentException(
                "Top N size setting ["
                    + QueryInsightsSettings.TOP_N_LATENCY_QUERIES_SIZE.getKey()
                    + "]"
                    + " should be smaller than max top N size ["
                    + QueryInsightsSettings.MAX_N_SIZE
                    + "was ("
                    + size
                    + " > "
                    + QueryInsightsSettings.MAX_N_SIZE
                    + ")"
            );
        }
    }

    /**
     * Get the top N size set for TopQueriesByLatencyService
     *
     * @return the top N size
     */
    public int getTopNSize() {
        return this.topNSize;
    }

    /**
     * Set the window size for TopQueriesByLatencyService
     *
     * @param windowSize window size to set
     */
    public void setWindowSize(TimeValue windowSize) {
        this.windowSize = windowSize;
    }

    /**
     * Validate if the window size is valid, based on internal constrains.
     *
     * @param windowSize the window size to validate
     */
    public void validateWindowSize(TimeValue windowSize) {
        if (windowSize.compareTo(QueryInsightsSettings.MAX_WINDOW_SIZE) > 0) {
            throw new IllegalArgumentException(
                "Window size setting ["
                    + QueryInsightsSettings.TOP_N_LATENCY_QUERIES_WINDOW_SIZE.getKey()
                    + "]"
                    + " should be smaller than max window size ["
                    + QueryInsightsSettings.MAX_WINDOW_SIZE
                    + "was ("
                    + windowSize
                    + " > "
                    + QueryInsightsSettings.MAX_WINDOW_SIZE
                    + ")"
            );
        }
    }

    /**
     * Get the window size set for TopQueriesByLatencyService
     *
     * @return the window size
     */
    public TimeValue getWindowSize() {
        return this.windowSize;
    }

//    /**
//     * Set the exporter type to export data generated in TopQueriesByLatencyService
//     *
//     * @param type The type of exporter, defined in {@link QueryInsightsExporterType}
//     */
//    public void addExporterForMetricType(MetricType metricType, QueryInsightsExporterType type) {
//        resetExporter(
//            getEnableExport(),
//            type,
//            clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER)
//        );
//    }
//
//    /**
//     * Set if the exporter is enabled
//     *
//     * @param enabled if the exporter is enabled
//     */
//    public void setExporterEnabled(MetricType metricType, QueryInsightsExporterType exporterType, String identifier, boolean enabled) {
//        resetExporter(
//            metricType,
//            exporterType,
//            identifier,
//            enabled
//        );
//    }
//
//    /**
//     * Set the identifier of this exporter, which will be used when exporting the data
//     * <p>
//     * For example, for local index exporter, this identifier would be used to define the index name
//     *
//     * @param identifier the identifier for the exporter
//     */
//    public void setExporterIdentifier(MetricType metricType, QueryInsightsExporterType exporterType, String identifier) {
//        resetExporter(
//            metricType,
//            exporterType,
//            identifier
//        );
//    }
//
//    /**
//     * Set the export interval for the exporter
//     *
//     * @param interval export interval
//     */
//    public void setExportInterval(TimeValue interval) {
//        super.setExportInterval(interval);
//        resetExporter(
//            getEnableExport(),
//            clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_TYPE),
//            clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER)
//        );
//    }

    /**
     * Validate if the export interval is valid, based on internal constrains.
     *
     * @param exportInterval the export interval to validate
     */
    public void validateExportInterval(TimeValue exportInterval) {
        if (exportInterval.getSeconds() < MIN_EXPORT_INTERVAL.getSeconds()) {
            throw new IllegalArgumentException(
                "Export Interval setting ["
                    + QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_INTERVAL.getKey()
                    + "]"
                    + " should not be smaller than minimal export interval size ["
                    + MIN_EXPORT_INTERVAL
                    + "]"
                    + "was ("
                    + exportInterval
                    + " < "
                    + MIN_EXPORT_INTERVAL
                    + ")"
            );
        }
    }

    /**
     * Reset the exporter with new config
     * <p>
     * This function can be used to enable/disable an exporter, change the type of the exporter,
     * or change the identifier of the exporter.
     *
     * @param enabled    the enable flag to set on the exporter
     * @param exporterType       The QueryInsightsExporterType to set on the exporter
     * @param identifier the Identifier to set on the exporter
     */
    public void resetExporter(MetricType metricType, QueryInsightsExporterType exporterType, String identifier, TimeValue exportInterval, boolean enabled) {
        doDeleteOneExporter(metricType, exporterType);
        if (!enabled) {
            return;
        }
        doCreateOneExporter(metricType, exporterType, identifier, exportInterval);
    }

}
