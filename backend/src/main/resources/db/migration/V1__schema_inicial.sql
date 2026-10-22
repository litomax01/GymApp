CREATE TABLE app_users (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email         VARCHAR(160) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'RECEPTION')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_app_users_email ON app_users (lower(email));

CREATE TABLE members (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    document_number VARCHAR(30)  NOT NULL,
    first_name      VARCHAR(80)  NOT NULL,
    last_name       VARCHAR(80)  NOT NULL,
    email           VARCHAR(160),
    phone           VARCHAR(30),
    birth_date      DATE,
    notes           VARCHAR(500),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_members_document ON members (document_number);
CREATE INDEX ix_members_last_name ON members (lower(last_name), lower(first_name));

CREATE TABLE plans (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(80)   NOT NULL,
    price         NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    duration_days INTEGER       NOT NULL CHECK (duration_days > 0),
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_plans_name ON plans (lower(name));

-- Una membresía es la contratación de un plan por un periodo [start_date, end_date].
-- price se copia del plan al contratar: cambiar el precio del plan no altera el histórico.
CREATE TABLE memberships (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id  BIGINT        NOT NULL REFERENCES members (id),
    plan_id    BIGINT        NOT NULL REFERENCES plans (id),
    start_date DATE          NOT NULL,
    end_date   DATE          NOT NULL,
    price      NUMERIC(10,2) NOT NULL CHECK (price >= 0),
    status     VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CANCELED')),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date)
);
CREATE INDEX ix_memberships_member ON memberships (member_id, end_date DESC);
CREATE INDEX ix_memberships_end_date ON memberships (end_date) WHERE status = 'ACTIVE';

CREATE TABLE payments (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    membership_id BIGINT        NOT NULL REFERENCES memberships (id),
    amount        NUMERIC(10,2) NOT NULL CHECK (amount > 0),
    method        VARCHAR(20)   NOT NULL CHECK (method IN ('CASH', 'CARD', 'TRANSFER')),
    reference     VARCHAR(80),
    paid_at       TIMESTAMPTZ   NOT NULL DEFAULT now(),
    registered_by BIGINT        NOT NULL REFERENCES app_users (id)
);
CREATE INDEX ix_payments_membership ON payments (membership_id);
CREATE INDEX ix_payments_paid_at ON payments (paid_at);

CREATE TABLE check_ins (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    member_id     BIGINT      NOT NULL REFERENCES members (id),
    checked_in_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    result        VARCHAR(10) NOT NULL CHECK (result IN ('ALLOWED', 'DENIED')),
    reason        VARCHAR(40),
    registered_by BIGINT      NOT NULL REFERENCES app_users (id)
);
CREATE INDEX ix_check_ins_member ON check_ins (member_id, checked_in_at DESC);
CREATE INDEX ix_check_ins_date ON check_ins (checked_in_at);
