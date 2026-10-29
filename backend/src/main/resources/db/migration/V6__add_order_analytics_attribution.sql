-- =============================================================================
-- V6: Funnel attribution for server-side PURCHASE events.
-- PURCHASE is recorded when payment is confirmed (often from a provider webhook,
-- with no browser context). The visitor/session keys are captured at checkout,
-- only when the shopper has granted analytics consent, so the event can be
-- attributed to the right funnel session.
-- =============================================================================
ALTER TABLE dbo.Orders ADD
    AnalyticsVisitorKey UNIQUEIDENTIFIER NULL,
    AnalyticsSessionKey UNIQUEIDENTIFIER NULL;
