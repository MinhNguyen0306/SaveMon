CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('CASH', 'BANK', 'EWALLET', 'OTHER')),
    current_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX accounts_user_id_idx ON accounts(user_id);
CREATE INDEX accounts_user_id_status_idx ON accounts(user_id, status);

CREATE TABLE categories (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    parent_id UUID REFERENCES categories(id),
    name VARCHAR(120) NOT NULL,
    type VARCHAR(20) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (parent_id IS NULL OR parent_id <> id)
);
CREATE INDEX categories_user_id_idx ON categories(user_id);
CREATE INDEX categories_user_id_parent_id_idx ON categories(user_id, parent_id);

CREATE TABLE shared_vaults (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    currency CHAR(3) NOT NULL,
    current_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    created_by UUID NOT NULL REFERENCES users(id),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE vault_members (
    id UUID PRIMARY KEY,
    vault_id UUID NOT NULL REFERENCES shared_vaults(id),
    user_id UUID NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL CHECK (role IN ('OWNER', 'MEMBER')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REMOVED')),
    joined_at TIMESTAMPTZ NOT NULL,
    left_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE(vault_id, user_id)
);
CREATE INDEX vault_members_user_id_idx ON vault_members(user_id, status);

CREATE TABLE transactions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    account_id UUID REFERENCES accounts(id),
    vault_id UUID REFERENCES shared_vaults(id),
    type VARCHAR(20) NOT NULL CHECK (type IN ('INCOME', 'EXPENSE')),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    currency CHAR(3) NOT NULL,
    transaction_date TIMESTAMPTZ NOT NULL,
    description VARCHAR(500),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'REVERSED')),
    idempotency_key VARCHAR(200),
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CHECK (account_id IS NOT NULL OR vault_id IS NOT NULL)
);
CREATE INDEX transactions_user_date_idx ON transactions(user_id, transaction_date DESC);
CREATE INDEX transactions_account_date_idx ON transactions(account_id, transaction_date DESC);
CREATE INDEX transactions_vault_date_idx ON transactions(vault_id, transaction_date DESC);
CREATE INDEX transactions_status_idx ON transactions(status);

CREATE TABLE transaction_items (
    id UUID PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES transactions(id),
    category_id UUID NOT NULL REFERENCES categories(id),
    description VARCHAR(500),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX transaction_items_transaction_id_idx ON transaction_items(transaction_id);
CREATE INDEX transaction_items_category_id_idx ON transaction_items(category_id);

CREATE TABLE contributions (
    id UUID PRIMARY KEY,
    vault_id UUID NOT NULL REFERENCES shared_vaults(id),
    member_id UUID NOT NULL REFERENCES vault_members(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    contributed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE shared_expenses (
    id UUID PRIMARY KEY,
    vault_id UUID NOT NULL REFERENCES shared_vaults(id),
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(id),
    paid_by_member_id UUID REFERENCES vault_members(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    description VARCHAR(500),
    expense_date TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX shared_expenses_vault_date_idx ON shared_expenses(vault_id, expense_date DESC);

CREATE TABLE expense_splits (
    id UUID PRIMARY KEY,
    shared_expense_id UUID NOT NULL REFERENCES shared_expenses(id),
    member_id UUID NOT NULL REFERENCES vault_members(id),
    amount NUMERIC(19,4) NOT NULL CHECK (amount > 0),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(shared_expense_id, member_id)
);

CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    key VARCHAR(200) NOT NULL,
    operation VARCHAR(100) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_reference UUID,
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ,
    UNIQUE(user_id, operation, key)
);

CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    actor_user_id UUID REFERENCES users(id),
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL
);
