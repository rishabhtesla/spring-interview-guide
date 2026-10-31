# Lesson 3: APIs, databases and transactions

[Module home](../readme.md) | [Next: Kafka](04-kafka.md)

## Database ownership and migrations

Each service accesses only its own PostgreSQL database. Flyway applies
versioned SQL migrations at startup and records the schema history.
Change an applied schema through a new migration, not by editing a migration
that another environment has already executed.

| Database | Tables | Why |
| --- | --- | --- |
| auth-db | `accounts` | Identity and password hashes |
| order-db | `orders`, `outbox` | Business state and durable event intent |
| notification-db | `notifications` | Consumer result and deduplication key |

The order and notification owner UUIDs are references to an identity in
another service, not cross-database foreign keys. Cross-service account
deletion would need an explicit business/event policy.

We use `JdbcTemplate` and SQL intentionally so transactions, constraints and
locks remain visible. This module does not demonstrate JPA; module 6 does.
SQL parameters are bound, not assembled from user input.

## API contract

All public URLs use the gateway base `http://localhost:8080`.

| Method/path | Authentication | Success | Main errors |
| --- | --- | --- | --- |
| `POST /auth/register` | Public | 201 account ID/email | 400 invalid input, 409 duplicate |
| `POST /auth/login` | Public | 200 token/type/lifetime | 400 invalid input, 401 credentials |
| `POST /orders` | Bearer + `orders` scope | 201 order + `Location` | 400 input/key, 401 token, 403 scope, 409 key conflict |
| `GET /orders` | Bearer + `orders` scope | 200 latest 100 owned orders | 401/403 |
| `GET /orders/{uuid}` | Bearer + `orders` scope | 200 owned order | 400 invalid UUID, 404 unknown/not owned |
| `GET /notifications` | Bearer + `notifications` scope | 200 latest 100 owned notifications | 401/403 |
| `GET /actuator/health` | Public, gateway only from host | 200 gateway health | Health does not prove the whole workflow |

Lists are bounded to 100 for simplicity; they are not a paginated full
history. Production APIs should define cursor/page contracts. Unsupported
routes are denied rather than exposed automatically.

## Order input and trust boundaries

```json
{"product":"Spring Interview Book","quantity":2,"unitPrice":19.99}
```

Product must be nonblank and at most 120 characters. Quantity is 1-100.
Unit price is a positive decimal with at most two fractional digits and a
maximum of 999999.99. The database also enforces relevant constraints.
`BigDecimal` and `NUMERIC` avoid binary floating-point arithmetic for prices.

These numbers are lab inputs, not authoritative catalog prices. A real order
service would resolve a product ID against trusted catalog/pricing data and
handle stock/payment policy. No inventory or payment side effects are claimed.

The request does not contain an authoritative owner field; ownership always
comes from the verified JWT. Unknown extra JSON fields are not ownership
controls.

## HTTP idempotency

Creating an order requires a UUID `Idempotency-Key` header. Store it with the
owner, and enforce `UNIQUE(owner_id, request_key)` in PostgreSQL.

Same owner + same key + same payload returns the same order ID (201 again in
this lab). Same owner + same key + different payload returns 409. Different
owners can safely choose the same key.

The database constraint, not a "check then save" in Java, prevents concurrent
duplicates. `ON CONFLICT DO NOTHING` resolves the race, then the service
loads the existing order and compares inputs. Exact product text matters;
decimal comparison ignores scale, so 10.0 and 10.00 mean the same price.

Read [OrderService](../order-service/src/main/java/com/example/interview/orders/OrderService.java).
Only a genuinely new order creates an outbox row. HTTP retries therefore do
not create a second event intent for the same accepted order.

## Transactional outbox

The `create` service method is called through Spring's transactional proxy.
It inserts the order and serialized event intent within one DB transaction.
Serialization failure rolls back the order as well; the annotation explicitly
includes the checked Jackson exception in its rollback rules.

```text
BEGIN
  insert order if key not already used
  compare existing input on replay
  if new: insert outbox event
COMMIT
```

No Kafka call takes place inside this order-creation transaction. That avoids
turning a broker outage into a lost event intent.

The publisher runs separately. It selects a bounded pending batch using
`FOR UPDATE SKIP LOCKED`. Another publisher replica can skip rows already
locked by a worker rather than send them concurrently from the same batch.
It waits for Kafka acknowledgement before marking a row published.

Holding DB locks during network calls is a deliberate simple-lab trade-off;
timeouts bound individual waits but a batch can still take time. High-throughput
relays often use leasing or CDC. `SKIP LOCKED` also means future extensions
with many events per aggregate need an explicit ordering strategy.

## Why duplicates are still possible

Kafka can accept an event, then the publisher can crash before committing
`published_at`. After restart, the row is still pending and is sent again.
An acknowledgement timeout can have the same ambiguity. Producer idempotence
does not eliminate duplicate application-level relay attempts across restarts.

The consumer stores `event_id` as its primary key. Inserting a duplicate does
nothing, so the same event does not create multiple notifications.
This is an idempotent database effect, not exactly-once end-to-end messaging.

## Indexes and cleanup

Indexes support owner/time lists and pending outbox scans. The partial outbox
index includes only unpublished rows. Published rows remain as evidence.
There is no retention cleanup job in this lab; growth must be monitored and
a safe cleanup/archiving policy added before long-term use.

Do not erase pending rows to silence a failing publisher. That discards
business event intent. Investigate the broker/configuration first.

## Interview self-check

Explain why `@Transactional` cannot magically make PostgreSQL and Kafka one
atomic operation. Explain the race solved by a unique constraint, checked
exception rollback, and why idempotent HTTP creation and idempotent event
consumption are separate requirements.
