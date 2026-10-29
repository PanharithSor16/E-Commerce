-- =============================================================================
-- V5: Visitors, visitor sessions, analytics events, consent
-- Privacy rules:
--   * VisitorKey is a random UUID from a first-party cookie; it contains no PII
--     and is only created after analytics consent is granted.
--   * Visitors are not linked to Users. Events carry UserId only when the user
--     is authenticated AND has granted analytics consent.
--   * Paths/referrers are stored without query strings; UTM values are
--     extracted separately and length-limited.
--   * No IP addresses are stored for analytics.
-- High-volume tables: see docs/DATABASE.md for retention and aggregation.
-- =============================================================================

CREATE TABLE dbo.Visitors (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    VisitorKey  UNIQUEIDENTIFIER NOT NULL,
    FirstSeenAt DATETIME2(3)     NOT NULL CONSTRAINT DF_Visitors_FirstSeenAt DEFAULT SYSUTCDATETIME(),
    LastSeenAt  DATETIME2(3)     NOT NULL CONSTRAINT DF_Visitors_LastSeenAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Visitors PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Visitors_VisitorKey UNIQUE (VisitorKey)
);
CREATE INDEX IX_Visitors_FirstSeenAt ON dbo.Visitors (FirstSeenAt);   -- new vs returning visitors
CREATE INDEX IX_Visitors_LastSeenAt ON dbo.Visitors (LastSeenAt);     -- retention purge

-- A visitor session ends after 30 minutes of inactivity (or at UTC midnight).
CREATE TABLE dbo.VisitorSessions (
    Id              BIGINT IDENTITY(1,1) NOT NULL,
    SessionKey      UNIQUEIDENTIFIER NOT NULL,
    VisitorId       BIGINT          NOT NULL,
    UserId          BIGINT          NULL,                -- set only when authenticated + consented
    StartedAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_VisitorSessions_StartedAt DEFAULT SYSUTCDATETIME(),
    LastActivityAt  DATETIME2(3)    NOT NULL CONSTRAINT DF_VisitorSessions_LastActivityAt DEFAULT SYSUTCDATETIME(),
    LandingPath     NVARCHAR(512)   NULL,
    ReferrerHost    NVARCHAR(255)   NULL,
    UtmSource       NVARCHAR(100)   NULL,
    UtmMedium       NVARCHAR(100)   NULL,
    UtmCampaign     NVARCHAR(100)   NULL,
    TrafficSource   VARCHAR(16)     NOT NULL CONSTRAINT DF_VisitorSessions_TrafficSource DEFAULT 'DIRECT',
    DeviceCategory  VARCHAR(16)     NOT NULL CONSTRAINT DF_VisitorSessions_Device DEFAULT 'UNKNOWN',
    Browser         NVARCHAR(64)    NULL,
    OperatingSystem NVARCHAR(64)    NULL,
    PageViewCount   INT             NOT NULL CONSTRAINT DF_VisitorSessions_PageViews DEFAULT 0,
    CONSTRAINT PK_VisitorSessions PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_VisitorSessions_SessionKey UNIQUE (SessionKey),
    CONSTRAINT FK_VisitorSessions_Visitors FOREIGN KEY (VisitorId) REFERENCES dbo.Visitors (Id),
    CONSTRAINT FK_VisitorSessions_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT CK_VisitorSessions_TrafficSource CHECK (TrafficSource IN
        ('DIRECT', 'ORGANIC', 'REFERRAL', 'SOCIAL', 'EMAIL', 'PAID', 'CAMPAIGN', 'OTHER')),
    CONSTRAINT CK_VisitorSessions_Device CHECK (DeviceCategory IN ('DESKTOP', 'TABLET', 'MOBILE', 'BOT', 'UNKNOWN')),
    CONSTRAINT CK_VisitorSessions_PageViews CHECK (PageViewCount >= 0)
);
CREATE INDEX IX_VisitorSessions_StartedAt ON dbo.VisitorSessions (StartedAt)
    INCLUDE (VisitorId, TrafficSource, DeviceCategory, UtmSource, UtmMedium, UtmCampaign);
CREATE INDEX IX_VisitorSessions_Visitor_StartedAt ON dbo.VisitorSessions (VisitorId, StartedAt DESC);
-- "Active visitors" = sessions with activity in the last 5 minutes
CREATE INDEX IX_VisitorSessions_LastActivityAt ON dbo.VisitorSessions (LastActivityAt);

CREATE TABLE dbo.AnalyticsEvents (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    EventId          UNIQUEIDENTIFIER NOT NULL,          -- client-generated: duplicate submissions are ignored
    EventType        VARCHAR(24)     NOT NULL,
    Source           VARCHAR(8)      NOT NULL CONSTRAINT DF_AnalyticsEvents_Source DEFAULT 'CLIENT',
    OccurredAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_AnalyticsEvents_OccurredAt DEFAULT SYSUTCDATETIME(),  -- server receive time
    VisitorId        BIGINT          NULL,
    VisitorSessionId BIGINT          NULL,
    UserId           BIGINT          NULL,
    PagePath         NVARCHAR(512)   NULL,               -- path only, no query string
    ProductId        BIGINT          NULL,
    CategoryId       BIGINT          NULL,
    OrderId          BIGINT          NULL,               -- PURCHASE only (server-side events)
    Referrer         NVARCHAR(512)   NULL,               -- scheme + host + path, no query string
    UtmSource        NVARCHAR(100)   NULL,
    UtmMedium        NVARCHAR(100)   NULL,
    UtmCampaign      NVARCHAR(100)   NULL,
    DeviceCategory   VARCHAR(16)     NULL,
    Browser          NVARCHAR(64)    NULL,
    OperatingSystem  NVARCHAR(64)    NULL,
    Properties       NVARCHAR(2000)  NULL,               -- allowlisted keys only, validated JSON
    CONSTRAINT PK_AnalyticsEvents PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_AnalyticsEvents_EventId UNIQUE (EventId),
    CONSTRAINT FK_AnalyticsEvents_Visitors FOREIGN KEY (VisitorId) REFERENCES dbo.Visitors (Id),
    CONSTRAINT FK_AnalyticsEvents_VisitorSessions FOREIGN KEY (VisitorSessionId) REFERENCES dbo.VisitorSessions (Id),
    CONSTRAINT FK_AnalyticsEvents_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT FK_AnalyticsEvents_Products FOREIGN KEY (ProductId) REFERENCES dbo.Products (Id),
    CONSTRAINT FK_AnalyticsEvents_Categories FOREIGN KEY (CategoryId) REFERENCES dbo.Categories (Id),
    CONSTRAINT FK_AnalyticsEvents_Orders FOREIGN KEY (OrderId) REFERENCES dbo.Orders (Id),
    CONSTRAINT CK_AnalyticsEvents_EventType CHECK (EventType IN ('PAGE_VIEW', 'PRODUCT_VIEW', 'CATEGORY_VIEW',
        'SEARCH', 'ADD_TO_CART', 'REMOVE_FROM_CART', 'BEGIN_CHECKOUT', 'PURCHASE', 'LOGIN', 'LOGOUT', 'REGISTER')),
    CONSTRAINT CK_AnalyticsEvents_Source CHECK (Source IN ('CLIENT', 'SERVER')),
    -- PURCHASE, LOGIN, LOGOUT, REGISTER are only ever emitted by trusted backend workflows
    CONSTRAINT CK_AnalyticsEvents_ServerOnly CHECK (
        EventType NOT IN ('PURCHASE', 'LOGIN', 'LOGOUT', 'REGISTER') OR Source = 'SERVER'),
    CONSTRAINT CK_AnalyticsEvents_Purchase CHECK (
        (EventType = 'PURCHASE' AND OrderId IS NOT NULL) OR (EventType <> 'PURCHASE' AND OrderId IS NULL)),
    CONSTRAINT CK_AnalyticsEvents_ProductView CHECK (EventType <> 'PRODUCT_VIEW' OR ProductId IS NOT NULL),
    CONSTRAINT CK_AnalyticsEvents_CategoryView CHECK (EventType <> 'CATEGORY_VIEW' OR CategoryId IS NOT NULL),
    CONSTRAINT CK_AnalyticsEvents_Device CHECK (DeviceCategory IS NULL OR DeviceCategory IN
        ('DESKTOP', 'TABLET', 'MOBILE', 'BOT', 'UNKNOWN')),
    CONSTRAINT CK_AnalyticsEvents_Properties CHECK (Properties IS NULL OR ISJSON(Properties) = 1)
);
-- One PURCHASE event per order, no matter how many times confirmation runs
CREATE UNIQUE INDEX UX_AnalyticsEvents_PurchaseOrder ON dbo.AnalyticsEvents (OrderId) WHERE EventType = 'PURCHASE';
-- Time-window reports by type (funnel, totals) with covering columns for distinct counts
CREATE INDEX IX_AnalyticsEvents_Type_OccurredAt ON dbo.AnalyticsEvents (EventType, OccurredAt)
    INCLUDE (VisitorId, VisitorSessionId, ProductId);
CREATE INDEX IX_AnalyticsEvents_OccurredAt ON dbo.AnalyticsEvents (OccurredAt);   -- retention purge, daily rollup
CREATE INDEX IX_AnalyticsEvents_Product_OccurredAt ON dbo.AnalyticsEvents (ProductId, OccurredAt)
    INCLUDE (EventType, VisitorId) WHERE ProductId IS NOT NULL;
CREATE INDEX IX_AnalyticsEvents_User_OccurredAt ON dbo.AnalyticsEvents (UserId, OccurredAt) WHERE UserId IS NOT NULL;
CREATE INDEX IX_AnalyticsEvents_Session ON dbo.AnalyticsEvents (VisitorSessionId) WHERE VisitorSessionId IS NOT NULL;

-- Daily rollups keep long-term reports cheap after raw events are purged.
CREATE TABLE dbo.AnalyticsDailySummary (
    StatDate          DATE         NOT NULL,             -- UTC day
    EventType         VARCHAR(24)  NOT NULL,
    DeviceCategory    VARCHAR(16)  NOT NULL,
    TrafficSource     VARCHAR(16)  NOT NULL,
    EventCount        BIGINT       NOT NULL,
    UniqueVisitors    BIGINT       NOT NULL,
    UniqueSessions    BIGINT       NOT NULL,
    ComputedAt        DATETIME2(3) NOT NULL CONSTRAINT DF_AnalyticsDailySummary_ComputedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AnalyticsDailySummary PRIMARY KEY CLUSTERED (StatDate, EventType, DeviceCategory, TrafficSource)
);

CREATE TABLE dbo.AnalyticsDailyProductStats (
    StatDate          DATE         NOT NULL,
    ProductId         BIGINT       NOT NULL,
    Views             BIGINT       NOT NULL,
    UniqueViewers     BIGINT       NOT NULL,
    AddToCarts        BIGINT       NOT NULL,
    Purchases         BIGINT       NOT NULL,             -- from Orders/OrderItems, not client events
    UnitsSold         BIGINT       NOT NULL,
    Revenue           DECIMAL(19,4) NOT NULL,
    ComputedAt        DATETIME2(3) NOT NULL CONSTRAINT DF_AnalyticsDailyProductStats_ComputedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AnalyticsDailyProductStats PRIMARY KEY CLUSTERED (StatDate, ProductId),
    CONSTRAINT FK_AnalyticsDailyProductStats_Products FOREIGN KEY (ProductId) REFERENCES dbo.Products (Id)
);
CREATE INDEX IX_AnalyticsDailyProductStats_Product ON dbo.AnalyticsDailyProductStats (ProductId, StatDate);

-- Append-only consent log; the latest row per (subject, ConsentType) is current.
CREATE TABLE dbo.UserConsent (
    Id            BIGINT IDENTITY(1,1) NOT NULL,
    UserId        BIGINT        NULL,
    VisitorId     BIGINT        NULL,
    ConsentType   VARCHAR(24)   NOT NULL,
    Granted       BIT           NOT NULL,
    PolicyVersion VARCHAR(16)   NOT NULL,                -- privacy policy version shown to the user
    Source        VARCHAR(16)   NOT NULL,                -- BANNER, ACCOUNT_SETTINGS, REGISTRATION
    RecordedAt    DATETIME2(3)  NOT NULL CONSTRAINT DF_UserConsent_RecordedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserConsent PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_UserConsent_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT FK_UserConsent_Visitors FOREIGN KEY (VisitorId) REFERENCES dbo.Visitors (Id),
    CONSTRAINT CK_UserConsent_Subject CHECK (UserId IS NOT NULL OR VisitorId IS NOT NULL),
    CONSTRAINT CK_UserConsent_Type CHECK (ConsentType IN ('ANALYTICS', 'MARKETING')),
    CONSTRAINT CK_UserConsent_Source CHECK (Source IN ('BANNER', 'ACCOUNT_SETTINGS', 'REGISTRATION'))
);
CREATE INDEX IX_UserConsent_User ON dbo.UserConsent (UserId, ConsentType, RecordedAt DESC) WHERE UserId IS NOT NULL;
CREATE INDEX IX_UserConsent_Visitor ON dbo.UserConsent (VisitorId, ConsentType, RecordedAt DESC) WHERE VisitorId IS NOT NULL;
