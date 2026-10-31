CREATE TABLE notifications (
    event_id UUID PRIMARY KEY,
    order_id UUID NOT NULL,
    owner_id UUID NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX notifications_owner_created ON notifications(owner_id, created_at DESC);
