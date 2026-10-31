# Lesson 6: interview answers backed by this code

[Module home](../readme.md)

For every answer, give **definition -> mechanism -> code -> trade-off**.
Avoid claiming that this lab implements a feature merely because the feature
appears in this chapter as an extension.

## Worked questions

### 1. Why microservices instead of a monolith?

Independent deployment, ownership and scaling can be valuable, but network
failures, distributed consistency, deployment tooling and observability add
cost. A modular monolith is often a better starting point.
Here the order/notification boundary teaches independent processing; it is
not evidence that a four-process system is always the right architecture.

### 2. What should an API gateway do?

Route traffic and enforce boundary policies such as authentication, limits
and timeouts. It should not own order business transactions.
`GatewayConfiguration` routes three API surfaces and validates tokens/scopes.
`RequestLogging` adds a request ID and timing. Rate limiting and a full tracing
backend are not implemented.

### 3. If the gateway authenticates, why check JWTs in services?

Internal networks can be reached by other processes or misconfigured routes.
Services should not trust an arbitrary identity header. Each resource service
verifies the signed token and scope, then checks ownership in its query.
Network policy and service authorization complement each other.

### 4. Why RSA instead of a shared JWT secret?

Auth signs with a private key while other services verify with the public key.
Verification capability does not imply signing capability.
Key rotation and secure storage remain operational requirements; this lab
uses local PEM mounts and does not implement JWKS rotation.

### 5. How do you prevent duplicate orders on HTTP retries?

Use a client idempotency key scoped to the caller and a database unique
constraint. A repeat with matching input returns the original result;
different input for the same key returns 409. Merely checking an in-memory map
is not safe across replicas or restarts.

### 6. How do you avoid losing events when writing to DB and Kafka?

Store business data and event intent in one local DB transaction, then relay
the outbox. `OrderService.create` does that; `OutboxPublisher` sends later.
This solves the lost-intent dual-write window, not all duplicate-delivery
windows. Explain the crash after send but before the publication marker.

### 7. Does `@Transactional` make a distributed workflow atomic?

Not automatically. The order transaction covers order-db writes. Kafka send
and notification-db processing have separate boundaries. A saga coordinates
business steps with compensating actions rather than pretending every
distributed operation is one local rollback.

This lab has no payment/stock saga. Adding one requires states, failure
policies, idempotent commands and compensations, not just another annotation.

### 8. What does Kafka guarantee about ordering?

Records have ordering within a partition, not globally across a topic.
Order ID is the key here. Consumer concurrency is bounded by partition count
for a group. Additional events per order would need relay and sequence design;
the single-create-event lab does not prove that extended lifecycle ordering.

### 9. What is a consumer group?

Consumers with one group ID share partitions. Another group reads the stream
independently. `notification-service-v1` is this application's group.
Changing the group ID can cause historical records to be read according to
offset/reset policy; durable deduplication remains necessary.

### 10. Is this exactly-once messaging?

No. Delivery/processing is at least once with an idempotent notification DB
effect. The event ID primary key absorbs duplicates. Kafka producer
idempotence and Kafka transactions have defined scopes; neither automatically
makes an external database or email atomic with Kafka offsets.

### 11. What happens to an invalid event?

The listener fails visibly. The configured error handler logs attempts,
retries retryable failures and recovers to a DLT. Recovery publication failure
must not be treated as success. DLT records need investigation and controlled
replay; they are not automatically fixed.

### 12. Why might a notification be temporarily missing?

The outbox relay/consumer is asynchronous. Inspect pending outbox age,
publication marker, broker/topic, group lag, consumer logs and DLT.
Polling with a deadline is appropriate in the smoke test; returning a fake
notification to hide an outage is not.

### 13. How do you evolve event schemas?

Version contracts and preserve compatibility for independently deployed
consumers. Additive optional fields are often easier than changing meanings
or removing fields. Consider schema compatibility tooling and migration.
This lab validates version 1 but does not use Schema Registry.

### 14. How would you handle database-per-service queries?

Use APIs, events/materialized views or an explicitly designed reporting
pipeline. Do not couple consumers to another service's private tables.
Notification stores the information it needs from the event.
Data duplication can be intentional; reconciliation and privacy/deletion
policies must then be defined.

### 15. Why use BigDecimal, migrations and constraints?

Decimal arithmetic suits money better than binary floating point. Flyway
versions schema changes; unique/check constraints enforce invariants under
concurrent access. Application validation gives useful errors but cannot
replace database integrity.

### 16. How would you make the system resilient?

Bound network timeouts, avoid unsafe retries, persist retryable intent, detect
duplicates and monitor lag/errors. A circuit breaker can reduce repeated calls
to a failing synchronous dependency; a fallback must not lie about business
success. Gateway timeouts are implemented, circuit breakers are not.

### 17. What is wrong with trusting client-provided price?

The client is outside the trusted business boundary. A real order service
resolves catalog price and availability server-side. This lab accepts price
only as a constrained teaching input and does not claim a real checkout
workflow.

### 18. Which tests are most convincing?

Real signature/claim rejection, web authorization/validation, SQL constraints
and rollback, duplicate delivery, Kafka recovery, and the full smoke path.
A context-startup test alone cannot prove those.
Be explicit when Docker-dependent tests were skipped or smoke was not run.

## A ten-session hands-on study plan

| Session | Work | Evidence to explain |
| --- | --- | --- |
| 1 | Draw service/database boundaries | Why no cross-service SQL join |
| 2 | Register/login and read claims locally | Hashing versus signing; never share tokens |
| 3 | Trace gateway and resource security | 401, 403 and owner-filtered 404 |
| 4 | Create/retry an order | Same key/input -> same ID; changed input -> 409 |
| 5 | Read migrations and service transaction | Order and outbox succeed/fail together |
| 6 | Inspect topic and group offsets | Partition/key/group mechanics |
| 7 | Stop/restart Kafka | Accepted order with durable pending event |
| 8 | Stop consumer and inject poison event | Replay window, retries, DLT investigation |
| 9 | Run tests and inspect skipped counts | What each test actually proves |
| 10 | Answer the questions without notes | Mechanism, code, failure and trade-off |

## Extension exercises: not implemented yet

| Extension | Requirements before claiming completion |
| --- | --- |
| Catalog/inventory service | Server-side price, stock policy, concurrency tests |
| Payment saga | State machine, compensation, duplicate command handling and failure tests |
| Refresh tokens | Hash storage, expiry, transactional rotation and reuse detection |
| Redis rate limiting | Distributed keys, bounded limits, trusted client address and 429 tests |
| JWKS/key rotation | Key IDs, overlapping verification, cache/expiry and rotation tests |
| CDC outbox relay | Connector setup, schema contract, duplicate/ordering/restart tests |
| Tracing and metrics | HTTP/event propagation, lag/outbox/DLT dashboards and useful alerts |
| Multi-broker Kafka | Replication/ISR policy, broker-failure experiments and durability evidence |
| Pagination | Stable cursor/order, boundaries and access-control tests |

Do one extension at a time. Label proposals as proposals until tests and
runtime evidence prove the behaviour.

## An honest project introduction

"I built/studied an order-notification microservices lab using Spring Boot,
PostgreSQL, Kafka and a custom JWT service. I can trace authentication,
ownership, idempotent creation and outbox-based event delivery, and demonstrate
failure handling. I understand which production identity, commerce and
operational features are outside the lab."

Use "built" only for work you actually did, and credit the original course
material elsewhere in the repository. Focus on a failure you reproduced and
understood; that is more persuasive than a list of technologies.
