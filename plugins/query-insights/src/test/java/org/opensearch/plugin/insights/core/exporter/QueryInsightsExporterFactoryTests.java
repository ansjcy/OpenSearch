/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

package org.opensearch.plugin.insights.core.exporter;

import org.opensearch.client.Client;
import org.opensearch.common.settings.Settings;
import org.opensearch.test.OpenSearchTestCase;
import org.junit.Before;

import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.DEFAULT_TOP_QUERIES_EXPORTER_TYPE;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.EXPORTER_TYPE;
import static org.opensearch.plugin.insights.settings.QueryInsightsSettings.EXPORT_INDEX;
import static org.mockito.Mockito.mock;

/**
 * Granular tests for the {@link QueryInsightsExporterFactoryTests} class.
 */
public class QueryInsightsExporterFactoryTests extends OpenSearchTestCase {
    private final String format = "YYYY.MM.dd";

    private final Client client = mock(Client.class);
    private QueryInsightsExporterFactory queryInsightsExporterFactory;

    @Before
    public void setup() {
        queryInsightsExporterFactory = new QueryInsightsExporterFactory(client);
    }

    public void testValidateConfigWhenResetExporter() {
        Settings.Builder settingsBuilder = Settings.builder();
        // empty settings
        Settings settings = settingsBuilder.build();
        try {
            queryInsightsExporterFactory.validateExporterConfig(settings);
        } catch (Exception e) {
            fail("No exception should be thrown when setting is null");
        }
    }

    public void testInvalidExporterTypeConfig() {
        Settings.Builder settingsBuilder = Settings.builder();
        Settings settings = settingsBuilder.put(EXPORTER_TYPE, "some_invalid_type").build();
        assertThrows(IllegalArgumentException.class, () -> { queryInsightsExporterFactory.validateExporterConfig(settings); });
    }

    public void testInvalidLocalIndexConfig() {
        Settings.Builder settingsBuilder = Settings.builder();
        assertThrows(IllegalArgumentException.class, () -> {
            queryInsightsExporterFactory.validateExporterConfig(
                settingsBuilder.put(EXPORTER_TYPE, DEFAULT_TOP_QUERIES_EXPORTER_TYPE).put(EXPORT_INDEX, "").build()
            );
        });
        assertThrows(IllegalArgumentException.class, () -> {
            queryInsightsExporterFactory.validateExporterConfig(
                settingsBuilder.put(EXPORTER_TYPE, DEFAULT_TOP_QUERIES_EXPORTER_TYPE).put(EXPORT_INDEX, "some_invalid_pattern").build()
            );
        });
    }

    public void testCreateAndCloseExporter() {
        AbstractExporter exporter1 = queryInsightsExporterFactory.createExporter(SinkType.LOCAL_INDEX, format);
        assertTrue(exporter1 instanceof LocalIndexExporter);
        AbstractExporter exporter2 = queryInsightsExporterFactory.createExporter(SinkType.DEBUG, format);
        assertTrue(exporter2 instanceof DebugExporter);
        AbstractExporter exporter3 = queryInsightsExporterFactory.createExporter(SinkType.DEBUG, format);
        assertTrue(exporter3 instanceof DebugExporter);
        try {
            queryInsightsExporterFactory.closeExporter(exporter1);
            queryInsightsExporterFactory.closeExporter(exporter2);
            queryInsightsExporterFactory.closeAllExporters();
        } catch (Exception e) {
            fail("No exception should be thrown when closing exporter");
        }
    }
}
