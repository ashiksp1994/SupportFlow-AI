package com.supportflow.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.supportflow.SupportFlowApplication;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Test
    void shouldComputeSupportAnalyticsDashboardMetrics() {
        AnalyticsService.DashboardAnalytics metrics = analyticsService.getDashboardAnalytics();
        assertNotNull(metrics);
        assertTrue(metrics.slaComplianceRatePercent() >= 0.0 && metrics.slaComplianceRatePercent() <= 100.0);
        assertNotNull(metrics.ticketsByPriority());
    }
}
