-- =============================================================================
-- Database provisioning (run once, as sa, by the db-init container or a DBA).
-- Creates the database and two least-privilege logins:
--   ecommerce_migrator : DDL rights, used only by Flyway
--   ecommerce_app      : read/write data only, used by the running application
-- Passwords are passed as sqlcmd variables: -v MIGRATOR_PASSWORD=... APP_PASSWORD=...
-- =============================================================================
:setvar DB_NAME ecommerce
:on error exit

IF DB_ID('$(DB_NAME)') IS NULL
BEGIN
    CREATE DATABASE [$(DB_NAME)];
END
GO

-- Row-versioned reads: readers don't block writers (recommended for OLTP on SQL Server).
ALTER DATABASE [$(DB_NAME)] SET READ_COMMITTED_SNAPSHOT ON WITH ROLLBACK IMMEDIATE;
ALTER DATABASE [$(DB_NAME)] SET RECOVERY FULL;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ecommerce_migrator')
    CREATE LOGIN ecommerce_migrator WITH PASSWORD = '$(MIGRATOR_PASSWORD)', CHECK_POLICY = ON, DEFAULT_DATABASE = [$(DB_NAME)];
ELSE
    ALTER LOGIN ecommerce_migrator WITH PASSWORD = '$(MIGRATOR_PASSWORD)';

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = 'ecommerce_app')
    CREATE LOGIN ecommerce_app WITH PASSWORD = '$(APP_PASSWORD)', CHECK_POLICY = ON, DEFAULT_DATABASE = [$(DB_NAME)];
ELSE
    ALTER LOGIN ecommerce_app WITH PASSWORD = '$(APP_PASSWORD)';
GO

USE [$(DB_NAME)];
GO

IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ecommerce_migrator')
    CREATE USER ecommerce_migrator FOR LOGIN ecommerce_migrator;
IF NOT EXISTS (SELECT 1 FROM sys.database_principals WHERE name = 'ecommerce_app')
    CREATE USER ecommerce_app FOR LOGIN ecommerce_app;
GO

ALTER ROLE db_ddladmin ADD MEMBER ecommerce_migrator;
ALTER ROLE db_datareader ADD MEMBER ecommerce_migrator;
ALTER ROLE db_datawriter ADD MEMBER ecommerce_migrator;

-- The application can read and write rows and use the order-number sequence, nothing else.
ALTER ROLE db_datareader ADD MEMBER ecommerce_app;
ALTER ROLE db_datawriter ADD MEMBER ecommerce_app;
GRANT EXECUTE TO ecommerce_app;
GO

-- Flyway creates dbo.OrderNumberSeq later; grant schema-level sequence usage now.
GRANT UPDATE ON SCHEMA::dbo TO ecommerce_app;
GO
