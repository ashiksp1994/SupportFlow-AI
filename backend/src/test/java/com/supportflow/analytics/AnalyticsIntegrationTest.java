package com.supportflow.analytics;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import com.supportflow.SupportFlowApplication;
import com.supportflow.identity.security.AccessControlService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = SupportFlowApplication.class)
@ActiveProfiles("test")
class AnalyticsIntegrationTest {

    @Autowired
    private AnalyticsService analyticsService;

    @Autowired
    private AccessControlService accessControlService;

    @Test
    void shouldComputeSupportAnalyticsDashboardMetrics() {
        accessControlService.runAsSystem(() -> {
            AnalyticsService.DashboardAnalytics metrics = analyticsService.getDashboardAnalytics();
            assertNotNull(metrics);
            assertTrue(metrics.slaComplianceRatePercent() >= 0.0 && metrics.slaComplianceRatePercent() <= 100.0);
            assertNotNull(metrics.ticketsByPriority());
        });
    }
}
