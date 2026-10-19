CREATE TABLE invitations (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    organization_id UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    inviter_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role VARCHAR(50) NOT NULL,
    token VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    accepted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uk_invitations_token UNIQUE (token)
);

CREATE INDEX idx_invitations_email ON invitations(email);
CREATE INDEX idx_invitations_org_id ON invitations(organization_id);
