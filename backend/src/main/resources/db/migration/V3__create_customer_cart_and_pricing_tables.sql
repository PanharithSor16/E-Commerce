-- =============================================================================
-- V3: Customer addresses, shipping methods, coupons, carts
-- Carts never store prices: totals are always recalculated server-side from
-- current catalog prices, coupon rules and shipping configuration.
-- =============================================================================

CREATE TABLE dbo.Addresses (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    UserId            BIGINT         NOT NULL,
    Label             NVARCHAR(50)   NULL,               -- "Home", "Office"
    RecipientName     NVARCHAR(200)  NOT NULL,
    PhoneNumber       VARCHAR(32)    NULL,
    Line1             NVARCHAR(200)  NOT NULL,
    Line2             NVARCHAR(200)  NULL,
    City              NVARCHAR(100)  NOT NULL,
    Region            NVARCHAR(100)  NULL,
    PostalCode        NVARCHAR(20)   NULL,
    CountryCode       CHAR(2)        NOT NULL,           -- ISO 3166-1 alpha-2
    IsDefaultShipping BIT            NOT NULL CONSTRAINT DF_Addresses_DefShip DEFAULT 0,
    IsDefaultBilling  BIT            NOT NULL CONSTRAINT DF_Addresses_DefBill DEFAULT 0,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Addresses_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_Addresses_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_Addresses PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_Addresses_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id) ON DELETE CASCADE,
    CONSTRAINT CK_Addresses_CountryCode CHECK (CountryCode COLLATE Latin1_General_BIN2 NOT LIKE '%[^A-Z]%' AND LEN(CountryCode) = 2)
);
CREATE INDEX IX_Addresses_UserId ON dbo.Addresses (UserId);
CREATE UNIQUE INDEX UX_Addresses_DefaultShipping ON dbo.Addresses (UserId) WHERE IsDefaultShipping = 1;
CREATE UNIQUE INDEX UX_Addresses_DefaultBilling ON dbo.Addresses (UserId) WHERE IsDefaultBilling = 1;

CREATE TABLE dbo.ShippingMethods (
    Id                     BIGINT IDENTITY(1,1) NOT NULL,
    Code                   VARCHAR(32)    NOT NULL,
    Name                   NVARCHAR(100)  NOT NULL,
    Description            NVARCHAR(255)  NULL,
    Fee                    DECIMAL(19,4)  NOT NULL,
    FreeShippingThreshold  DECIMAL(19,4)  NULL,          -- subtotal at/above which Fee = 0
    EstimatedDaysMin       INT            NULL,
    EstimatedDaysMax       INT            NULL,
    SortOrder              INT            NOT NULL CONSTRAINT DF_ShippingMethods_SortOrder DEFAULT 0,
    Active                 BIT            NOT NULL CONSTRAINT DF_ShippingMethods_Active DEFAULT 1,
    CreatedAt              DATETIME2(3)   NOT NULL CONSTRAINT DF_ShippingMethods_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt              DATETIME2(3)   NOT NULL CONSTRAINT DF_ShippingMethods_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_ShippingMethods PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_ShippingMethods_Code UNIQUE (Code),
    CONSTRAINT CK_ShippingMethods_Fee CHECK (Fee >= 0),
    CONSTRAINT CK_ShippingMethods_Threshold CHECK (FreeShippingThreshold IS NULL OR FreeShippingThreshold >= 0),
    CONSTRAINT CK_ShippingMethods_Days CHECK (EstimatedDaysMin IS NULL OR EstimatedDaysMax IS NULL OR EstimatedDaysMin <= EstimatedDaysMax)
);

CREATE TABLE dbo.Coupons (
    Id                 BIGINT IDENTITY(1,1) NOT NULL,
    Code               VARCHAR(40)    NOT NULL,          -- stored upper-case
    Description        NVARCHAR(255)  NULL,
    DiscountType       VARCHAR(16)    NOT NULL,
    DiscountValue      DECIMAL(19,4)  NOT NULL,          -- percent (0-100] or fixed amount
    MinOrderAmount     DECIMAL(19,4)  NULL,
    MaxDiscountAmount  DECIMAL(19,4)  NULL,              -- cap for percentage coupons
    StartsAt           DATETIME2(3)   NULL,
    EndsAt             DATETIME2(3)   NULL,
    UsageLimit         INT            NULL,              -- total redemptions allowed
    PerUserLimit       INT            NULL,
    TimesUsed          INT            NOT NULL CONSTRAINT DF_Coupons_TimesUsed DEFAULT 0,
    Active             BIT            NOT NULL CONSTRAINT DF_Coupons_Active DEFAULT 1,
    CreatedAt          DATETIME2(3)   NOT NULL CONSTRAINT DF_Coupons_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt          DATETIME2(3)   NOT NULL CONSTRAINT DF_Coupons_UpdatedAt DEFAULT SYSUTCDATETIME(),
    Version            BIGINT         NOT NULL CONSTRAINT DF_Coupons_Version DEFAULT 0,
    CONSTRAINT PK_Coupons PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Coupons_Code UNIQUE (Code),
    CONSTRAINT CK_Coupons_Code CHECK (Code COLLATE Latin1_General_BIN2 NOT LIKE '%[^A-Z0-9_-]%'),
    CONSTRAINT CK_Coupons_DiscountType CHECK (DiscountType IN ('PERCENTAGE', 'FIXED_AMOUNT', 'FREE_SHIPPING')),
    CONSTRAINT CK_Coupons_DiscountValue CHECK (
        (DiscountType = 'PERCENTAGE'    AND DiscountValue > 0 AND DiscountValue <= 100) OR
        (DiscountType = 'FIXED_AMOUNT'  AND DiscountValue > 0) OR
        (DiscountType = 'FREE_SHIPPING' AND DiscountValue = 0)),
    CONSTRAINT CK_Coupons_Window CHECK (StartsAt IS NULL OR EndsAt IS NULL OR StartsAt < EndsAt),
    CONSTRAINT CK_Coupons_Limits CHECK ((UsageLimit IS NULL OR UsageLimit > 0) AND (PerUserLimit IS NULL OR PerUserLimit > 0)),
    CONSTRAINT CK_Coupons_TimesUsed CHECK (TimesUsed >= 0 AND (UsageLimit IS NULL OR TimesUsed <= UsageLimit)),
    CONSTRAINT CK_Coupons_Amounts CHECK ((MinOrderAmount IS NULL OR MinOrderAmount >= 0) AND (MaxDiscountAmount IS NULL OR MaxDiscountAmount > 0))
);

-- Carts belong either to a user or to an anonymous browser (cart cookie).
-- The anonymous cart cookie holds a random token; only its hash is stored.
-- On login the anonymous cart is merged into the user's active cart.
CREATE TABLE dbo.Carts (
    Id              BIGINT IDENTITY(1,1) NOT NULL,
    PublicId        UNIQUEIDENTIFIER NOT NULL CONSTRAINT DF_Carts_PublicId DEFAULT NEWID(),
    UserId          BIGINT          NULL,
    CartTokenHash   CHAR(64)        NULL,
    Status          VARCHAR(16)     NOT NULL CONSTRAINT DF_Carts_Status DEFAULT 'ACTIVE',
    CouponId        BIGINT          NULL,
    CreatedAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_Carts_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt       DATETIME2(3)    NOT NULL CONSTRAINT DF_Carts_UpdatedAt DEFAULT SYSUTCDATETIME(),
    ExpiresAt       DATETIME2(3)    NULL,                -- anonymous carts expire
    Version         BIGINT          NOT NULL CONSTRAINT DF_Carts_Version DEFAULT 0,
    CONSTRAINT PK_Carts PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Carts_PublicId UNIQUE (PublicId),
    CONSTRAINT FK_Carts_Users FOREIGN KEY (UserId) REFERENCES dbo.Users (Id),
    CONSTRAINT FK_Carts_Coupons FOREIGN KEY (CouponId) REFERENCES dbo.Coupons (Id),
    CONSTRAINT CK_Carts_Status CHECK (Status IN ('ACTIVE', 'CONVERTED', 'MERGED', 'ABANDONED')),
    CONSTRAINT CK_Carts_Owner CHECK (UserId IS NOT NULL OR CartTokenHash IS NOT NULL)
);
-- At most one active cart per user and per anonymous token
CREATE UNIQUE INDEX UX_Carts_ActiveUser ON dbo.Carts (UserId) WHERE Status = 'ACTIVE' AND UserId IS NOT NULL;
CREATE UNIQUE INDEX UX_Carts_TokenHash ON dbo.Carts (CartTokenHash) WHERE CartTokenHash IS NOT NULL;
CREATE INDEX IX_Carts_Status_UpdatedAt ON dbo.Carts (Status, UpdatedAt);   -- abandonment reports, purge

CREATE TABLE dbo.CartItems (
    Id         BIGINT IDENTITY(1,1) NOT NULL,
    CartId     BIGINT       NOT NULL,
    VariantId  BIGINT       NOT NULL,
    Quantity   INT          NOT NULL,
    CreatedAt  DATETIME2(3) NOT NULL CONSTRAINT DF_CartItems_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt  DATETIME2(3) NOT NULL CONSTRAINT DF_CartItems_UpdatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_CartItems PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_CartItems_Cart_Variant UNIQUE (CartId, VariantId),
    CONSTRAINT FK_CartItems_Carts FOREIGN KEY (CartId) REFERENCES dbo.Carts (Id) ON DELETE CASCADE,
    CONSTRAINT FK_CartItems_Variants FOREIGN KEY (VariantId) REFERENCES dbo.ProductVariants (Id),
    CONSTRAINT CK_CartItems_Quantity CHECK (Quantity BETWEEN 1 AND 99)
);
CREATE INDEX IX_CartItems_VariantId ON dbo.CartItems (VariantId);

-- Reference data
INSERT INTO dbo.ShippingMethods (Code, Name, Description, Fee, FreeShippingThreshold, EstimatedDaysMin, EstimatedDaysMax, SortOrder) VALUES
    ('STANDARD', N'Standard Shipping', N'Delivered in 3-7 business days', 5.0000, 75.0000, 3, 7, 1),
    ('EXPRESS',  N'Express Shipping',  N'Delivered in 1-2 business days', 15.0000, NULL, 1, 2, 2);
