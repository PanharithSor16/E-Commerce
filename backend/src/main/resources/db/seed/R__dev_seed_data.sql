-- =============================================================================
-- Development seed data (loaded ONLY by the dev profile: spring.flyway.locations
-- includes classpath:db/seed). Idempotent: safe to re-run. Never used in prod.
-- =============================================================================

-- Categories
MERGE dbo.Categories AS t
USING (VALUES
    (N'Running', 'running', N'Shoes and gear for every run', 1),
    (N'Outdoor', 'outdoor', N'Jackets, packs and trail essentials', 2),
    (N'Accessories', 'accessories', N'Bottles, socks, caps and more', 3)
) AS s (Name, Slug, Description, SortOrder)
ON t.Slug = s.Slug
WHEN NOT MATCHED THEN INSERT (Name, Slug, Description, SortOrder) VALUES (s.Name, s.Slug, s.Description, s.SortOrder);

-- Products
MERGE dbo.Products AS t
USING (
    SELECT c.Id AS CategoryId, v.*
    FROM (VALUES
        ('running',     N'Trail Runner X',        'trail-runner-x',     'TRX-100', N'Acme',    N'Grippy, light trail shoe',          N'A responsive trail shoe with an aggressive outsole for mud and rock.', 129.00, 99.00, 1),
        ('running',     N'Road Glide 2',          'road-glide-2',       'RG2-200', N'Acme',    N'Cushioned daily trainer',           N'Soft foam and a breathable mesh upper for everyday miles.',            110.00, NULL,  1),
        ('running',     N'Tempo Racer',           'tempo-racer',        'TR-300',  N'Swift',   N'Fast and firm race-day shoe',       N'Carbon-infused plate for tempo runs and race day.',                    160.00, NULL,  0),
        ('outdoor',     N'Storm Shell Jacket',    'storm-shell-jacket', 'SSJ-400', N'Northpeak', N'Waterproof, breathable shell',    N'3-layer waterproof shell with taped seams and pit zips.',              220.00, 189.00, 1),
        ('outdoor',     N'Daypack 22L',           'daypack-22l',        'DP-500',  N'Northpeak', N'Everyday hiking backpack',        N'Ventilated back panel, hydration sleeve and hip belt pockets.',        89.00,  NULL,  0),
        ('accessories', N'Insulated Bottle 750ml','insulated-bottle',   'IB-600',  N'Hydra',   N'Keeps drinks cold for 24 hours',    N'Double-wall stainless steel, leak-proof lid.',                         32.00,  NULL,  0),
        ('accessories', N'Merino Running Socks',  'merino-socks',       'MS-700',  N'Swift',   N'Soft, odor-resistant merino blend', N'Seamless toe and arch support. Pack of 2.',                            24.00,  19.00, 0),
        ('accessories', N'Trucker Cap',           'trucker-cap',        'TC-800',  N'Acme',    N'Breathable mesh cap',               N'Adjustable snapback with a quick-dry sweatband.',                      28.00,  NULL,  0)
    ) AS v (CategorySlug, Name, Slug, Sku, Brand, ShortDescription, Description, BasePrice, DiscountPrice, Featured)
    JOIN dbo.Categories c ON c.Slug = v.CategorySlug
) AS s
ON t.Sku = s.Sku
WHEN NOT MATCHED THEN INSERT (CategoryId, Name, Slug, Sku, Brand, ShortDescription, Description, BasePrice, DiscountPrice,
                              Status, Featured, PublishedAt)
    VALUES (s.CategoryId, s.Name, s.Slug, s.Sku, s.Brand, s.ShortDescription, s.Description, s.BasePrice, s.DiscountPrice,
            'ACTIVE', s.Featured, SYSUTCDATETIME());

-- Variants: sizes for shoes, sizes for the jacket, a default variant for everything else
MERGE dbo.ProductVariants AS t
USING (
    SELECT p.Id AS ProductId, v.*
    FROM (VALUES
        ('TRX-100', 'TRX-100-40', N'40', NULL, 12), ('TRX-100', 'TRX-100-42', N'42', NULL, 8), ('TRX-100', 'TRX-100-44', N'44', NULL, 3),
        ('RG2-200', 'RG2-200-41', N'41', NULL, 20), ('RG2-200', 'RG2-200-43', N'43', NULL, 15),
        ('TR-300',  'TR-300-42',  N'42', NULL, 4),
        ('SSJ-400', 'SSJ-400-M',  N'M',  N'Blue', 10), ('SSJ-400', 'SSJ-400-L', N'L', N'Blue', 6), ('SSJ-400', 'SSJ-400-M-B', N'M', N'Black', 2),
        ('DP-500',  'DP-500',     NULL,  NULL, 25),
        ('IB-600',  'IB-600',     NULL,  NULL, 60),
        ('MS-700',  'MS-700',     NULL,  NULL, 100),
        ('TC-800',  'TC-800',     NULL,  NULL, 0)
    ) AS v (ProductSku, Sku, Size, Color, Stock)
    JOIN dbo.Products p ON p.Sku = v.ProductSku
) AS s
ON t.Sku = s.Sku
WHEN NOT MATCHED THEN INSERT (ProductId, Sku, Size, Color, StockQuantity) VALUES (s.ProductId, s.Sku, s.Size, s.Color, s.Stock);

-- Opening stock ledger entries for seeded variants that have none yet
INSERT INTO dbo.InventoryTransactions (VariantId, ChangeQuantity, QuantityAfter, Reason, Note)
SELECT v.Id, v.StockQuantity, v.StockQuantity, 'INITIAL', N'Dev seed'
FROM dbo.ProductVariants v
WHERE v.StockQuantity > 0
  AND NOT EXISTS (SELECT 1 FROM dbo.InventoryTransactions t WHERE t.VariantId = v.Id);

-- Coupons
MERGE dbo.Coupons AS t
USING (VALUES
    ('WELCOME10', N'10% off your first order', 'PERCENTAGE', 10.00, NULL, 1),
    ('FREESHIP',  N'Free shipping over $40',   'FREE_SHIPPING', 0.00, 40.00, NULL)
) AS s (Code, Description, DiscountType, DiscountValue, MinOrderAmount, PerUserLimit)
ON t.Code = s.Code
WHEN NOT MATCHED THEN INSERT (Code, Description, DiscountType, DiscountValue, MinOrderAmount, PerUserLimit)
    VALUES (s.Code, s.Description, s.DiscountType, s.DiscountValue, s.MinOrderAmount, s.PerUserLimit);
