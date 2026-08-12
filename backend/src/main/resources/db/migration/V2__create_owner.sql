CREATE TABLE owners (
    id UUID PRIMARY KEY,
    singleton_key SMALLINT NOT NULL DEFAULT 1,
    email VARCHAR(254) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    display_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_owners_singleton UNIQUE (singleton_key),
    CONSTRAINT ck_owners_singleton CHECK (singleton_key = 1),
    CONSTRAINT uk_owners_email UNIQUE (email)
);
