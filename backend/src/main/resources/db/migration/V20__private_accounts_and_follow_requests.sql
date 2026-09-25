-- Private accounts (Instagram-style) + follow requests.
ALTER TABLE users ADD COLUMN account_private BOOLEAN NOT NULL DEFAULT FALSE;

-- Existing instant follows stay ACTIVE; private targets now create PENDING rows.
ALTER TABLE follows ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
