-- =============================================================================
-- V1: Identity, authorization, authentication sessions, login history
-- All timestamps are UTC (DATETIME2(3), default SYSUTCDATETIME()).
-- UpdatedAt columns are maintained by the application (JPA auditing).
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Roles and permissions
-- Roles group fine-grained permissions so STAFF can be restricted to specific
-- responsibilities while ADMIN holds every permission.
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.Roles (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    Name        VARCHAR(50)    NOT NULL,
    Description NVARCHAR(255)  NULL,
    CreatedAt   DATETIME2(3)   NOT NULL CONSTRAINT DF_Roles_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Roles PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Roles_Name UNIQUE (Name),
    CONSTRAINT CK_Roles_Name CHECK (Name LIKE 'ROLE[_]%')
);

CREATE TABLE dbo.Permissions (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    Name        VARCHAR(64)    NOT NULL,
    Description NVARCHAR(255)  NULL,
    CONSTRAINT PK_Permissions PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Permissions_Name UNIQUE (Name)
);

CREATE TABLE dbo.RolePermissions (
    RoleId       BIGINT NOT NULL,
    PermissionId BIGINT NOT NULL,
    CONSTRAINT PK_RolePermissions PRIMARY KEY CLUSTERED (RoleId, PermissionId),
    CONSTRAINT FK_RolePermissions_Roles FOREIGN KEY (RoleId) REFERENCES dbo.Roles (Id) ON DELETE CASCADE,
    CONSTRAINT FK_RolePermissions_Permissions FOREIGN KEY (PermissionId) REFERENCES dbo.Permissions (Id) ON DELETE CASCADE
);
CREATE INDEX IX_RolePermissions_PermissionId ON dbo.RolePermissions (PermissionId);

-- -----------------------------------------------------------------------------
-- Users
-- Users are never hard-deleted (orders, audit logs and payments reference them).
-- Deactivation sets Enabled = 0; GDPR erasure anonymizes PII columns instead.
-- Email is stored normalized (trimmed, lower-case) by the application.
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.Users (
    Id                 BIGINT IDENTITY(1,1) NOT NULL,
    PublicId           UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_Users_PublicId DEFAULT NEWID(),
    Email              NVARCHAR(320)  NOT NULL,
    PasswordHash       VARCHAR(100)   NOT NULL,          -- BCrypt hash, never plaintext
    FirstName          NVARCHAR(100)  NOT NULL,
    LastName           NVARCHAR(100)  NOT NULL,
    PhoneNumber        VARCHAR(32)    NULL,
    EmailVerified      BIT            NOT NULL CONSTRAINT DF_Users_EmailVerified DEFAULT 0,
    EmailVerifiedAt    DATETIME2(3)   NULL,
    Enabled            BIT            NOT NULL CONSTRAINT DF_Users_Enabled DEFAULT 1,
    FailedLoginCount   INT            NOT NULL CONSTRAINT DF_Users_FailedLoginCount DEFAULT 0,
    LockedUntil        DATETIME2(3)   NULL,
    LastLoginAt        DATETIME2(3)   NULL,
    PasswordChangedAt  DATETIME2(3)   NULL,
    DeactivatedAt      DATETIME2(3)   NULL,
    CreatedAt          DATETIME2(3)   NOT NULL CONSTRAINT DF_Users_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt          DATETIME2(3)   NOT NULL CONSTRAINT DF_Users_UpdatedAt DEFAULT SYSUTCDATETIME(),
    Version            BIGINT         NOT NULL CONSTRAINT DF_Users_Version DEFAULT 0,
    CONSTRAINT PK_Users PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Users_PublicId UNIQUE (PublicId),
    CONSTRAINT UQ_Users_Email UNIQUE (Email),
    CONSTRAINT CK_Users_FailedLoginCount CHECK (FailedLoginCount >= 0)
);
-- Admin customer list: newest first, filtered by enabled flag
CREATE INDEX IX_Users_CreatedAt ON dbo.Users (CreatedAt DESC) INCLUDE (Enabled, EmailVerified);
CREATE INDEX IX_Users_LastName_FirstName ON dbo.Users (LastName, FirstName);

CREATE TABLE dbo.UserRoles (
    UserId     BIGINT       NOT NULL,
    RoleId     BIGINT       NOT NULL,
    AssignedAt DATETIME2(3) NOT NULL CONSTRAINT DF_UserRoles_AssignedAt DEFAULT SYSUTCDATETIME(),
    AssignedBy BIGINT       NULL,
    CONSTRAINT PK_UserRoles PRIMARY KEY CLUSTERED (UserId, RoleId),
    CONSTRAINT FK_UserRoles_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id) ON DELETE CASCADE,
    CONSTRAINT FK_UserRoles_Roles FOREIGN KEY (RoleId) REFERENCES dbo.Roles (Id),
    CONSTRAINT FK_UserRoles_AssignedBy FOREIGN KEY (AssignedBy) REFERENCES dbo.Users (Id)
);
CREATE INDEX IX_UserRoles_RoleId ON dbo.UserRoles (RoleId);

-- -----------------------------------------------------------------------------
-- Single-use tokens (email verification, password reset).
-- Only a SHA-256 hash of the token is stored; the raw token is emailed once.
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.UserTokens (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    UserId      BIGINT        NOT NULL,
    TokenType   VARCHAR(32)   NOT NULL,
    TokenHash   CHAR(64)      NOT NULL,                  -- hex SHA-256
    ExpiresAt   DATETIME2(3)  NOT NULL,
    ConsumedAt  DATETIME2(3)  NULL,
    CreatedAt   DATETIME2(3)  NOT NULL CONSTRAINT DF_UserTokens_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_UserTokens PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_UserTokens_TokenHash UNIQUE (TokenHash),
    CONSTRAINT FK_UserTokens_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id) ON DELETE CASCADE,
    CONSTRAINT CK_UserTokens_TokenType CHECK (TokenType IN ('EMAIL_VERIFICATION', 'PASSWORD_RESET')),
    CONSTRAINT CK_UserTokens_Expiry CHECK (ExpiresAt > CreatedAt)
);
CREATE INDEX IX_UserTokens_UserId_TokenType ON dbo.UserTokens (UserId, TokenType) INCLUDE (ConsumedAt, ExpiresAt);
CREATE INDEX IX_UserTokens_ExpiresAt ON dbo.UserTokens (ExpiresAt);   -- purge job

-- -----------------------------------------------------------------------------
-- Authentication sessions (stateful, opaque session token in an HttpOnly cookie)
-- The cookie carries a random 256-bit token; only its SHA-256 hash is stored.
-- PublicId is a non-secret reference used in UIs, login history and revocation.
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.AuthenticationSessions (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    PublicId         UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_AuthSessions_PublicId DEFAULT NEWID(),
    UserId           BIGINT          NOT NULL,
    TokenHash        CHAR(64)        NOT NULL,
    CreatedAt        DATETIME2(3)    NOT NULL CONSTRAINT DF_AuthSessions_CreatedAt DEFAULT SYSUTCDATETIME(),
    LastSeenAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_AuthSessions_LastSeenAt DEFAULT SYSUTCDATETIME(),
    IdleExpiresAt    DATETIME2(3)    NOT NULL,           -- sliding expiry, extended on activity
    AbsoluteExpiresAt DATETIME2(3)   NOT NULL,           -- hard cap regardless of activity
    RevokedAt        DATETIME2(3)    NULL,
    RevokedReason    VARCHAR(32)     NULL,
    RevokedByUserId  BIGINT          NULL,
    IpAddress        VARCHAR(45)     NULL,               -- IPv4/IPv6 text form
    UserAgent        NVARCHAR(512)   NULL,
    Browser          NVARCHAR(64)    NULL,
    OperatingSystem  NVARCHAR(64)    NULL,
    DeviceCategory   VARCHAR(16)     NULL,
    CONSTRAINT PK_AuthenticationSessions PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_AuthSessions_PublicId UNIQUE (PublicId),
    CONSTRAINT UQ_AuthSessions_TokenHash UNIQUE (TokenHash),
    CONSTRAINT FK_AuthSessions_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id) ON DELETE CASCADE,
    CONSTRAINT FK_AuthSessions_RevokedBy FOREIGN KEY (RevokedByUserId) REFERENCES dbo.Users (Id),
    CONSTRAINT CK_AuthSessions_RevokedReason CHECK (RevokedReason IS NULL OR RevokedReason IN
        ('LOGOUT', 'USER_REVOKED', 'ADMIN_REVOKED', 'PASSWORD_CHANGED', 'ACCOUNT_DEACTIVATED', 'EXPIRED')),
    CONSTRAINT CK_AuthSessions_DeviceCategory CHECK (DeviceCategory IS NULL OR DeviceCategory IN
        ('DESKTOP', 'TABLET', 'MOBILE', 'BOT', 'UNKNOWN')),
    CONSTRAINT CK_AuthSessions_Expiry CHECK (AbsoluteExpiresAt > CreatedAt AND IdleExpiresAt <= AbsoluteExpiresAt)
);
-- "My sessions" and admin "user sessions": active sessions per user
CREATE INDEX IX_AuthSessions_UserId_Active ON dbo.AuthenticationSessions (UserId, AbsoluteExpiresAt)
    INCLUDE (IdleExpiresAt, LastSeenAt) WHERE RevokedAt IS NULL;
-- Admin "all active sessions" page and purge job
CREATE INDEX IX_AuthSessions_AbsoluteExpiresAt ON dbo.AuthenticationSessions (AbsoluteExpiresAt);

-- -----------------------------------------------------------------------------
-- Login history (every login attempt, success or failure)
-- Never stores passwords, cookies, headers, tokens.
-- Email privacy: EmailHash = HMAC-SHA256(normalized email, server key) allows
-- exact-match search and repeated-failure grouping; EmailMasked is for display.
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.LoginHistory (
    Id                  BIGINT IDENTITY(1,1) NOT NULL,
    UserId              BIGINT           NULL,           -- NULL when the email is unknown
    EmailHash           CHAR(64)         NOT NULL,
    EmailMasked         NVARCHAR(320)    NOT NULL,       -- e.g. j***@example.com
    Status              VARCHAR(16)      NOT NULL,
    FailureReason       VARCHAR(32)      NULL,
    OccurredAt          DATETIME2(3)     NOT NULL CONSTRAINT DF_LoginHistory_OccurredAt DEFAULT SYSUTCDATETIME(),
    IpAddress           VARCHAR(45)      NULL,           -- only from trusted proxy chain
    UserAgent           NVARCHAR(512)    NULL,
    Browser             NVARCHAR(64)     NULL,
    BrowserVersion      NVARCHAR(32)     NULL,
    OperatingSystem     NVARCHAR(64)     NULL,
    DeviceCategory      VARCHAR(16)      NULL,
    AuthSessionPublicId UNIQUEIDENTIFIER NULL,           -- intentionally no FK: survives session purge
    CONSTRAINT PK_LoginHistory PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_LoginHistory_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT CK_LoginHistory_Status CHECK (Status IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT CK_LoginHistory_FailureReason CHECK (
        (Status = 'SUCCESS' AND FailureReason IS NULL) OR
        (Status = 'FAILURE' AND FailureReason IS NOT NULL AND FailureReason IN ('BAD_CREDENTIALS', 'UNKNOWN_ACCOUNT', 'ACCOUNT_LOCKED',
            'ACCOUNT_DISABLED', 'EMAIL_NOT_VERIFIED', 'RATE_LIMITED', 'OTHER'))),
    CONSTRAINT CK_LoginHistory_DeviceCategory CHECK (DeviceCategory IS NULL OR DeviceCategory IN
        ('DESKTOP', 'TABLET', 'MOBILE', 'BOT', 'UNKNOWN'))
);
CREATE INDEX IX_LoginHistory_OccurredAt ON dbo.LoginHistory (OccurredAt DESC) INCLUDE (Status, UserId);
CREATE INDEX IX_LoginHistory_UserId_OccurredAt ON dbo.LoginHistory (UserId, OccurredAt DESC) WHERE UserId IS NOT NULL;
CREATE INDEX IX_LoginHistory_Status_OccurredAt ON dbo.LoginHistory (Status, OccurredAt DESC);
-- Repeated failed attempts grouped by submitted email or by source IP
CREATE INDEX IX_LoginHistory_EmailHash_OccurredAt ON dbo.LoginHistory (EmailHash, OccurredAt DESC) INCLUDE (Status);
CREATE INDEX IX_LoginHistory_Ip_Failures ON dbo.LoginHistory (IpAddress, OccurredAt DESC)
    WHERE Status = 'FAILURE' AND IpAddress IS NOT NULL;
CREATE INDEX IX_LoginHistory_AuthSession ON dbo.LoginHistory (AuthSessionPublicId) WHERE AuthSessionPublicId IS NOT NULL;

-- -----------------------------------------------------------------------------
-- Audit log for security-relevant and administrative actions
-- Details is an allowlisted JSON document (never secrets or full request bodies).
-- -----------------------------------------------------------------------------
CREATE TABLE dbo.AuditLogs (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    ActorUserId BIGINT          NULL,                    -- NULL for system jobs / anonymous
    Action      VARCHAR(64)     NOT NULL,                -- e.g. USER_DEACTIVATED, ORDER_REFUNDED
    EntityType  VARCHAR(64)     NULL,
    EntityId    VARCHAR(64)     NULL,
    Outcome     VARCHAR(16)     NOT NULL CONSTRAINT DF_AuditLogs_Outcome DEFAULT 'SUCCESS',
    Details     NVARCHAR(4000)  NULL,
    IpAddress   VARCHAR(45)     NULL,
    OccurredAt  DATETIME2(3)    NOT NULL CONSTRAINT DF_AuditLogs_OccurredAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_AuditLogs PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_AuditLogs_ActorUser FOREIGN KEY (ActorUserId) REFERENCES dbo.Users (Id),
    CONSTRAINT CK_AuditLogs_Outcome CHECK (Outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
    CONSTRAINT CK_AuditLogs_Details CHECK (Details IS NULL OR ISJSON(Details) = 1)
);
CREATE INDEX IX_AuditLogs_OccurredAt ON dbo.AuditLogs (OccurredAt DESC);
CREATE INDEX IX_AuditLogs_Actor_OccurredAt ON dbo.AuditLogs (ActorUserId, OccurredAt DESC) WHERE ActorUserId IS NOT NULL;
CREATE INDEX IX_AuditLogs_Entity ON dbo.AuditLogs (EntityType, EntityId, OccurredAt DESC);

-- -----------------------------------------------------------------------------
-- Reference data: roles and permissions
-- -----------------------------------------------------------------------------
INSERT INTO dbo.Roles (Name, Description) VALUES
    ('ROLE_CUSTOMER', N'Storefront customer'),
    ('ROLE_STAFF',    N'Store staff with delegated operational permissions'),
    ('ROLE_ADMIN',    N'Administrator with full management access');

INSERT INTO dbo.Permissions (Name, Description) VALUES
    ('CATALOG_READ',        N'View products, categories and inventory in admin'),
    ('CATALOG_WRITE',       N'Create, update and archive products, categories, images, variants'),
    ('INVENTORY_WRITE',     N'Adjust stock levels'),
    ('ORDER_READ',          N'View all orders'),
    ('ORDER_FULFILL',       N'Update order fulfillment status'),
    ('ORDER_REFUND',        N'Cancel paid orders and issue refunds'),
    ('CUSTOMER_READ',       N'View customer profiles'),
    ('CUSTOMER_WRITE',      N'Edit customer profiles, activate or deactivate accounts'),
    ('ROLE_ASSIGN',         N'Assign and remove roles'),
    ('SECURITY_LOG_READ',   N'View login history and active sessions'),
    ('SESSION_REVOKE',      N'Revoke other users'' sessions'),
    ('ANALYTICS_READ',      N'View analytics dashboards and reports'),
    ('ANALYTICS_EXPORT',    N'Export analytics reports to CSV'),
    ('COUPON_WRITE',        N'Manage coupons'),
    ('SETTINGS_WRITE',      N'Change store settings'),
    ('AUDIT_LOG_READ',      N'View audit logs');

-- ADMIN: every permission
INSERT INTO dbo.RolePermissions (RoleId, PermissionId)
SELECT r.Id, p.Id FROM dbo.Roles r CROSS JOIN dbo.Permissions p WHERE r.Name = 'ROLE_ADMIN';

-- STAFF: day-to-day operations only (no refunds, roles, security logs, settings, exports)
INSERT INTO dbo.RolePermissions (RoleId, PermissionId)
SELECT r.Id, p.Id FROM dbo.Roles r JOIN dbo.Permissions p
    ON p.Name IN ('CATALOG_READ', 'CATALOG_WRITE', 'INVENTORY_WRITE', 'ORDER_READ', 'ORDER_FULFILL',
                  'CUSTOMER_READ', 'ANALYTICS_READ')
WHERE r.Name = 'ROLE_STAFF';
