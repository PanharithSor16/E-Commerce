-- =============================================================================
-- V2: Product catalog (categories, products, variants, images)
-- Money: DECIMAL(19,4). Products are archived, never hard-deleted, because
-- order items, inventory transactions and analytics events reference them.
-- Every product has at least one variant (a "default" variant when the product
-- has no size/color options); carts, orders and stock always target a variant.
-- =============================================================================

CREATE TABLE dbo.Categories (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    ParentId    BIGINT         NULL,
    Name        NVARCHAR(120)  NOT NULL,
    Slug        VARCHAR(140)   NOT NULL,
    Description NVARCHAR(1000) NULL,
    ImageUrl    NVARCHAR(1024) NULL,
    SortOrder   INT            NOT NULL CONSTRAINT DF_Categories_SortOrder DEFAULT 0,
    Active      BIT            NOT NULL CONSTRAINT DF_Categories_Active DEFAULT 1,
    CreatedAt   DATETIME2(3)   NOT NULL CONSTRAINT DF_Categories_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt   DATETIME2(3)   NOT NULL CONSTRAINT DF_Categories_UpdatedAt DEFAULT SYSUTCDATETIME(),
    Version     BIGINT         NOT NULL CONSTRAINT DF_Categories_Version DEFAULT 0,
    CONSTRAINT PK_Categories PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Categories_Slug UNIQUE (Slug),
    CONSTRAINT FK_Categories_Parent FOREIGN KEY (ParentId) REFERENCES dbo.Categories (Id),
    CONSTRAINT CK_Categories_NotSelfParent CHECK (ParentId IS NULL OR ParentId <> Id),
    CONSTRAINT CK_Categories_Slug CHECK (Slug COLLATE Latin1_General_BIN2 NOT LIKE '%[^a-z0-9-]%')
);
CREATE INDEX IX_Categories_ParentId ON dbo.Categories (ParentId, SortOrder) WHERE Active = 1;

CREATE TABLE dbo.Products (
    Id               BIGINT IDENTITY(1,1) NOT NULL,
    CategoryId       BIGINT         NOT NULL,
    Name             NVARCHAR(200)  NOT NULL,
    Slug             VARCHAR(220)   NOT NULL,
    Sku              VARCHAR(64)    NOT NULL,            -- parent/style SKU
    Brand            NVARCHAR(100)  NULL,
    ShortDescription NVARCHAR(500)  NULL,
    Description      NVARCHAR(MAX)  NULL,                -- sanitized on write, escaped on render
    BasePrice        DECIMAL(19,4)  NOT NULL,
    DiscountPrice    DECIMAL(19,4)  NULL,
    -- Persisted so price filtering / sorting can use an index
    EffectivePrice   AS CAST(COALESCE(DiscountPrice, BasePrice) AS DECIMAL(19,4)) PERSISTED,
    Currency         CHAR(3)        NOT NULL CONSTRAINT DF_Products_Currency DEFAULT 'USD',
    Status           VARCHAR(16)    NOT NULL CONSTRAINT DF_Products_Status DEFAULT 'DRAFT',
    Featured         BIT            NOT NULL CONSTRAINT DF_Products_Featured DEFAULT 0,
    SoldCount        INT            NOT NULL CONSTRAINT DF_Products_SoldCount DEFAULT 0,   -- denormalized for "best sellers" sort
    PublishedAt      DATETIME2(3)   NULL,
    ArchivedAt       DATETIME2(3)   NULL,
    CreatedAt        DATETIME2(3)   NOT NULL CONSTRAINT DF_Products_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt        DATETIME2(3)   NOT NULL CONSTRAINT DF_Products_UpdatedAt DEFAULT SYSUTCDATETIME(),
    Version          BIGINT         NOT NULL CONSTRAINT DF_Products_Version DEFAULT 0,
    CONSTRAINT PK_Products PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_Products_Slug UNIQUE (Slug),
    CONSTRAINT UQ_Products_Sku UNIQUE (Sku),
    CONSTRAINT FK_Products_Categories FOREIGN KEY (CategoryId) REFERENCES dbo.Categories (Id),
    CONSTRAINT CK_Products_Status CHECK (Status IN ('DRAFT', 'ACTIVE', 'ARCHIVED')),
    CONSTRAINT CK_Products_BasePrice CHECK (BasePrice >= 0),
    CONSTRAINT CK_Products_DiscountPrice CHECK (DiscountPrice IS NULL OR (DiscountPrice >= 0 AND DiscountPrice < BasePrice)),
    CONSTRAINT CK_Products_Currency CHECK (Currency COLLATE Latin1_General_BIN2 NOT LIKE '%[^A-Z]%' AND LEN(Currency) = 3),
    CONSTRAINT CK_Products_SoldCount CHECK (SoldCount >= 0)
);
-- Storefront listing patterns: by category, newest, price, popularity, featured
CREATE INDEX IX_Products_Category_Status ON dbo.Products (CategoryId, Status, PublishedAt DESC) INCLUDE (EffectivePrice, SoldCount);
CREATE INDEX IX_Products_Status_PublishedAt ON dbo.Products (Status, PublishedAt DESC);
CREATE INDEX IX_Products_Status_EffectivePrice ON dbo.Products (Status, EffectivePrice);
CREATE INDEX IX_Products_Status_SoldCount ON dbo.Products (Status, SoldCount DESC);
CREATE INDEX IX_Products_Featured ON dbo.Products (PublishedAt DESC) WHERE Featured = 1 AND Status = 'ACTIVE';
CREATE INDEX IX_Products_Name ON dbo.Products (Name) WHERE Status = 'ACTIVE';

CREATE TABLE dbo.ProductVariants (
    Id                BIGINT IDENTITY(1,1) NOT NULL,
    ProductId         BIGINT         NOT NULL,
    Sku               VARCHAR(64)    NOT NULL,
    Size              NVARCHAR(50)   NULL,
    Color             NVARCHAR(50)   NULL,
    PriceOverride     DECIMAL(19,4)  NULL,               -- replaces product price for this variant
    StockQuantity     INT            NOT NULL CONSTRAINT DF_ProductVariants_Stock DEFAULT 0,
    LowStockThreshold INT            NOT NULL CONSTRAINT DF_ProductVariants_LowStock DEFAULT 5,
    Active            BIT            NOT NULL CONSTRAINT DF_ProductVariants_Active DEFAULT 1,
    CreatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_ProductVariants_CreatedAt DEFAULT SYSUTCDATETIME(),
    UpdatedAt         DATETIME2(3)   NOT NULL CONSTRAINT DF_ProductVariants_UpdatedAt DEFAULT SYSUTCDATETIME(),
    Version           BIGINT         NOT NULL CONSTRAINT DF_ProductVariants_Version DEFAULT 0,  -- optimistic lock for stock
    CONSTRAINT PK_ProductVariants PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT UQ_ProductVariants_Sku UNIQUE (Sku),
    -- SQL Server treats NULLs as equal here, so a product can have one (NULL, NULL) default variant
    CONSTRAINT UQ_ProductVariants_Options UNIQUE (ProductId, Size, Color),
    CONSTRAINT FK_ProductVariants_Products FOREIGN KEY (ProductId) REFERENCES dbo.Products (Id),
    CONSTRAINT CK_ProductVariants_Stock CHECK (StockQuantity >= 0),
    CONSTRAINT CK_ProductVariants_LowStock CHECK (LowStockThreshold >= 0),
    CONSTRAINT CK_ProductVariants_Price CHECK (PriceOverride IS NULL OR PriceOverride >= 0)
);
-- Low-stock dashboard widget
CREATE INDEX IX_ProductVariants_LowStock ON dbo.ProductVariants (StockQuantity) INCLUDE (ProductId, LowStockThreshold) WHERE Active = 1;

CREATE TABLE dbo.ProductImages (
    Id          BIGINT IDENTITY(1,1) NOT NULL,
    ProductId   BIGINT         NOT NULL,
    VariantId   BIGINT         NULL,                     -- optional: image specific to a color variant
    Url         NVARCHAR(1024) NOT NULL,
    StorageKey  NVARCHAR(512)  NULL,                     -- object key in local/S3 storage
    AltText     NVARCHAR(255)  NULL,
    ContentType VARCHAR(64)    NULL,
    SizeBytes   BIGINT         NULL,
    Width       INT            NULL,
    Height      INT            NULL,
    SortOrder   INT            NOT NULL CONSTRAINT DF_ProductImages_SortOrder DEFAULT 0,
    IsPrimary   BIT            NOT NULL CONSTRAINT DF_ProductImages_IsPrimary DEFAULT 0,
    CreatedAt   DATETIME2(3)   NOT NULL CONSTRAINT DF_ProductImages_CreatedAt DEFAULT SYSUTCDATETIME(),
    CONSTRAINT PK_ProductImages PRIMARY KEY CLUSTERED (Id),
    CONSTRAINT FK_ProductImages_Products FOREIGN KEY (ProductId) REFERENCES dbo.Products (Id) ON DELETE CASCADE,
    CONSTRAINT FK_ProductImages_Variants FOREIGN KEY (VariantId) REFERENCES dbo.ProductVariants (Id),
    CONSTRAINT CK_ProductImages_ContentType CHECK (ContentType IS NULL OR ContentType IN ('image/jpeg', 'image/png', 'image/webp')),
    CONSTRAINT CK_ProductImages_Size CHECK (SizeBytes IS NULL OR (SizeBytes > 0 AND SizeBytes <= 5242880))
);
CREATE INDEX IX_ProductImages_Product ON dbo.ProductImages (ProductId, SortOrder);
CREATE UNIQUE INDEX UX_ProductImages_OnePrimary ON dbo.ProductImages (ProductId) WHERE IsPrimary = 1;
