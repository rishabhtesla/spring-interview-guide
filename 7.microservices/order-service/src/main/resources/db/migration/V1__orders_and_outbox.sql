CREATE TABLE orders (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL,
    request_key UUID NOT NULL,
    product VARCHAR(120) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 100),
    unit_price NUMERIC(8, 2) NOT NULL CHECK (unit_price > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (owner_id, request_key)
);
CREATE INDEX orders_owner_created ON orders(owner_id, created_at DESC);
CREATE TABLE outbox (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMPTZ
);
CREATE INDEX outbox_pending ON outbox(created_at) WHERE published_at IS NULL;
