/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.core.listener;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchPhaseContext;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchRequestContext;
import org.opensearch.action.search.SearchRequestOperationsListener;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.plugin.insights.core.exporter.QueryInsightsExporterType;
import org.opensearch.plugin.insights.core.service.TopQueriesByLatencyService;
import org.opensearch.plugin.insights.rules.model.Attribute;
import org.opensearch.plugin.insights.rules.model.Measurement;
import org.opensearch.plugin.insights.rules.model.MetricType;
import org.opensearch.plugin.insights.rules.model.SearchQueryRecord;
import org.opensearch.plugin.insights.settings.QueryInsightsSettings;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_ENABLED;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_ENABLED;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_INTERVAL;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_SIZE;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.TOP_N_LATENCY_QUERIES_WINDOW_SIZE;

/**
 * The listener for top N queries by latency
 *
 * @opensearch.internal
 */
public final class SearchQueryLatencyListener extends SearchRequestOperationsListener {
    private static final ToXContent.Params FORMAT_PARAMS = new ToXContent.MapParams(Collections.singletonMap("pretty", "false"));

    private static final Logger log = LogManager.getLogger(SearchQueryLatencyListener.class);

    private final TopQueriesByLatencyService topQueriesByLatencyService;

    /**
     * Constructor for SearchQueryLatencyListener
     *
     * @param clusterService The Node's cluster service.
     * @param topQueriesByLatencyService The topQueriesByLatencyService associated with this listener
     */
    @Inject
    public SearchQueryLatencyListener(ClusterService clusterService, TopQueriesByLatencyService topQueriesByLatencyService) {
        this.topQueriesByLatencyService = topQueriesByLatencyService;
        clusterService.getClusterSettings().addSettingsUpdateConsumer(
            TOP_N_LATENCY_QUERIES_ENABLED,
            v -> this.setEnabled(MetricType.LATENCY, v));
        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(
                TOP_N_LATENCY_QUERIES_SIZE,
                this.topQueriesByLatencyService::setTopNSize,
                this.topQueriesByLatencyService::validateTopNSize
            );
        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(
                TOP_N_LATENCY_QUERIES_WINDOW_SIZE,
                this.topQueriesByLatencyService::setWindowSize,
                this.topQueriesByLatencyService::validateWindowSize
            );

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(
                TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_INTERVAL,
                v -> this.topQueriesByLatencyService.resetExporter(
                    MetricType.LATENCY,
                    QueryInsightsExporterType.LOCAL_INDEX,
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER),
                    v,
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_ENABLED)
                ),
                this.topQueriesByLatencyService::validateExportInterval
            );
        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(
                TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER,
                v -> this.topQueriesByLatencyService.resetExporter(
                    MetricType.LATENCY,
                    QueryInsightsExporterType.LOCAL_INDEX,
                    v,
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_INTERVAL),
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_ENABLED)
            ));

        clusterService.getClusterSettings()
            .addSettingsUpdateConsumer(
                TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_ENABLED,
                v -> this.topQueriesByLatencyService.resetExporter(
                    MetricType.LATENCY,
                    QueryInsightsExporterType.LOCAL_INDEX,
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_IDENTIFIER),
                    clusterService.getClusterSettings().get(QueryInsightsSettings.TOP_N_LATENCY_QUERIES_LOCAL_INDEX_EXPORTER_INTERVAL),
                    v
                ));

        this.setEnabled(clusterService.getClusterSettings().get(TOP_N_LATENCY_QUERIES_ENABLED));
        this.topQueriesByLatencyService.setTopNSize(clusterService.getClusterSettings().get(TOP_N_LATENCY_QUERIES_SIZE));
        this.topQueriesByLatencyService.setWindowSize(clusterService.getClusterSettings().get(TOP_N_LATENCY_QUERIES_WINDOW_SIZE));
    }

    public void setEnabled(MetricType metricType, boolean enabled) {
        this.topQueriesByLatencyService.setEnableCollect(metricType, enabled);
        if (!enabled) {
            for (MetricType t : MetricType.allMetricTypes()) {
                if (this.topQueriesByLatencyService.getEnableCollect(t)) {
                    return;
                }
            }
            super.setEnabled(false);
        } else {
            super.setEnabled(true);
        }
    }

    @Override
    public boolean isEnabled() {
        return super.isEnabled();
    }

    @Override
    public void onPhaseStart(SearchPhaseContext context) {}

    @Override
    public void onPhaseEnd(SearchPhaseContext context, SearchRequestContext searchRequestContext) {}

    @Override
    public void onPhaseFailure(SearchPhaseContext context) {}

    @Override
    public void onRequestStart(SearchRequestContext searchRequestContext) {}

    @Override
    public void onRequestEnd(SearchPhaseContext context, SearchRequestContext searchRequestContext) {
        SearchRequest request = context.getRequest();
        try {
            if (topQueriesByLatencyService.getEnableCollect(MetricType.LATENCY)) {
                Map<MetricType, Measurement<Number>> measurements = Map.of(
                    MetricType.LATENCY, new Measurement<>(MetricType.LATENCY.name(), System.nanoTime() - searchRequestContext.getAbsoluteStartNanos())
                );
                Map<Attribute, Object> attributes = new HashMap<>();
                attributes.put(Attribute.SEARCH_TYPE, request.searchType());
                attributes.put(Attribute.SOURCE, request.source().toString(FORMAT_PARAMS));
                attributes.put(Attribute.TOTAL_SHARDS, context.getNumShards());
                attributes.put(Attribute.INDICES, request.indices());
                attributes.put(Attribute.PHASE_LATENCY_MAP, searchRequestContext.phaseTookMap());
                SearchQueryRecord record = new SearchQueryRecord(
                    request.getOrCreateAbsoluteStartMillis(),
                    measurements,
                    attributes
                );
                topQueriesByLatencyService.addRecord(MetricType.LATENCY, record);
            }
//            topQueriesByLatencyService.ingestQueryData(
//                request.getOrCreateAbsoluteStartMillis(),
//                request.searchType(),
//                request.source().toString(FORMAT_PARAMS),
//                context.getNumShards(),
//                request.indices(),
//                new HashMap<>(),
//                searchRequestContext.phaseTookMap(),
//                System.nanoTime() - searchRequestContext.getAbsoluteStartNanos()
//            );
        } catch (Exception e) {
            log.error(String.format(Locale.ROOT, "fail to ingest query insight data, error: %s", e));
        }
    }
}
