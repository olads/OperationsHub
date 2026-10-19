CREATE TABLE customers (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    phone VARCHAR(50),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_customer_org_email UNIQUE (organization_id, email)
);

CREATE INDEX idx_customers_org_status ON customers(organization_id, status);
CREATE INDEX idx_customers_org_name ON customers(organization_id, name);
CREATE INDEX idx_customers_org_email ON customers(organization_id, email);

CREATE TABLE products (
    id UUID PRIMARY KEY,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    price NUMERIC(19, 4) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_product_org_sku UNIQUE (organization_id, sku)
);

CREATE INDEX idx_products_org_sku ON products(organization_id, sku);
CREATE INDEX idx_products_org_status ON products(organization_id, status);
CREATE INDEX idx_products_org_name ON products(organization_id, name);
