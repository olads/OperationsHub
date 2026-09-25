-- 1. Refactor organizations
ALTER TABLE organizations
    ADD COLUMN default_currency VARCHAR(10),
    ADD COLUMN logo_url VARCHAR(255),
    ADD COLUMN contact_email VARCHAR(255),
    ADD COLUMN contact_phone VARCHAR(50),
    ADD COLUMN website VARCHAR(255),
    ADD COLUMN owner_invitation_sent BOOLEAN NOT NULL DEFAULT FALSE;

-- 2. Drop memberships table
DROP TABLE IF EXISTS memberships;

-- 3. Refactor users table
ALTER TABLE users
    DROP CONSTRAINT IF EXISTS uk_6dotkott2kjsp8vw4d0m25fb7, -- default constraint name for email unique, we will just drop the unique index
    DROP CONSTRAINT IF EXISTS users_email_key;

-- Sometimes Postgres names it differently, so let's try to drop the index directly if it exists, or handle it
-- Assuming standard naming:
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'users_email_key'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT users_email_key;
    END IF;
    
    IF EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uk_6dotkott2kjsp8vw4d0m25fb7'
    ) THEN
        ALTER TABLE users DROP CONSTRAINT uk_6dotkott2kjsp8vw4d0m25fb7;
    END IF;
END $$;

ALTER TABLE users
    ADD COLUMN organization_id UUID REFERENCES organizations(id),
    ADD COLUMN role VARCHAR(50) NOT NULL DEFAULT 'EMPLOYEE',
    ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;

-- Add unique constraint for (organization_id, email)
ALTER TABLE users
    ADD CONSTRAINT uq_users_org_email UNIQUE (organization_id, email);

-- Add partial unique index for platform admins
CREATE UNIQUE INDEX idx_users_admin_email ON users(email) WHERE organization_id IS NULL;

-- 4. Refactor products table
ALTER TABLE products
    ADD COLUMN category VARCHAR(100),
    ADD COLUMN unit VARCHAR(50),
    ADD COLUMN currency VARCHAR(10),
    ADD COLUMN attributes JSONB DEFAULT '{}'::jsonb;

-- Create product_tags table
CREATE TABLE product_tags (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    tag VARCHAR(100) NOT NULL
);

-- Create product_images table
CREATE TABLE product_images (
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    image_url VARCHAR(255) NOT NULL
);

-- 5. Create customer_accounts table
CREATE TABLE customer_accounts (
    id UUID PRIMARY KEY,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id),
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(50),
    status VARCHAR(50) NOT NULL,
    customer_id UUID REFERENCES customers(id), -- Assuming customers table exists from V4
    CONSTRAINT uq_customer_accounts_org_email UNIQUE (organization_id, email)
);

-- 6. Make invitation inviter nullable
ALTER TABLE invitations
    ALTER COLUMN inviter_id DROP NOT NULL;
