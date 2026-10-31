# Lesson 1: architecture and boundaries

[Module home](../readme.md) | [Next: authentication](02-authentication.md)

## Begin with the business story

A learner registers an account and places an order for a book. The order is
accepted immediately after its database transaction succeeds. A separate
service later records a notification. The caller does not wait for that
notification before receiving the order.

This separation teaches two communication styles:

- **Synchronous HTTP:** client -> gateway -> service. The caller waits for a
  status and response.
- **Asynchronous event:** order outbox -> Kafka -> notification consumer.
  Producer and consumer do not have to be available at the same instant.

The accepted order and pending notification are different states. An HTTP 201
does not mean the consumer already processed the event.

## Why these four services?

| Service | Owns | Does not own |
| --- | --- | --- |
| Auth | Account identity, password hashes, signing credentials | Orders or user notifications |
| Gateway | HTTP routing and boundary policy | Business transactions or password checks |
| Order | Accepted orders, idempotency keys, publication intent | Notification delivery |
| Notification | Consumer processing and notification inbox | Order creation or account password storage |

Separating by responsibility is more important than separating by controller.
Four processes alone do not prove a good microservices design. Small teams
often benefit from a modular monolith until independent scaling/deployment,
organizational ownership or fault isolation justify distributed complexity.

Each service has its own database instance in Compose. This avoids accidental
cross-service access in the lab. The shared local database username is a
setup convenience, not a production credential-isolation recommendation.
Use separate principals, least privilege and secret management in deployment.

## Read the startup wiring

The module's [parent POM](../pom.xml) manages Boot and Cloud dependencies and
builds five Maven children (including the non-executable common library).
Each service has a normal Boot main class. Gateway uses WebFlux and Reactor;
the other APIs use servlet MVC/JDBC. Blocking JDBC must not run on gateway's
event-loop threads.

Compose's internal DNS resolves names such as `order-service` and `kafka`.
The gateway's route URI uses that DNS name and port. No Eureka server is
required for this single-host lab. In a real platform, service discovery may
come from Kubernetes Services/DNS or another registry.

Only `127.0.0.1:8080` is published to the host. Database, broker and service
ports are reachable within the Compose network, not directly from a browser.
Internal reachability is not authorization; services still verify JWTs.

## Trace a request and an event separately

```text
POST /orders + Bearer JWT + Idempotency-Key
  -> gateway verifies signature, issuer, expiry, audience, subject, scope
  -> forwards Authorization header
  -> order service repeats token/scope verification
  -> binds and validates request
  -> derives owner UUID from token
  -> writes order and outbox in one local DB transaction
  -> returns 201 + Location + order data
```

```text
scheduled publisher
  -> locks pending outbox rows
  -> sends string JSON event to Kafka, keyed by order ID
  -> waits for broker acknowledgement
  -> marks outbox row published

consumer
  -> deserializes and validates event
  -> inserts notification with unique event ID
  -> database transaction commits
  -> listener returns
  -> Kafka container may commit the consumed offset
```

An HTTP request ID appears in gateway logs and response headers for routed
requests. It is not a complete distributed trace: the outbox event ID is the
durable identifier to use when investigating asynchronous delivery.
Authorization failures before route filters are not logged by that global
route filter.

## Why not just save the order and call Kafka?

Imagine saving the order successfully and then the broker fails. The caller
has an accepted order, but no durable event intent. Reversing the steps is
also unsafe: Kafka might receive an event for an order whose database write
fails. This is the **dual-write problem**.

The transactional outbox writes both business data and publication intent to
one database. A separate relay publishes the intent later. This provides
recoverability, not exactly-once delivery.

## Failure boundaries

| Failure | Expected effect |
| --- | --- |
| Auth unavailable | New logins fail; existing valid tokens can still be verified locally |
| Order DB unavailable | Orders fail; no pretend-success fallback |
| Kafka unavailable | Orders can commit with pending outbox rows; publisher logs/retries |
| Notification service unavailable | Kafka retains events within its retention policy |
| Poison event | Consumer retries and recovers to DLT; other records can progress |
| Gateway unavailable | Public HTTP entry point unavailable; services are not exposed as bypass ports |

There is a single instance of each service and one broker. The lab illustrates
fault handling, not high availability. Adding replicas requires checking DB
contention, Kafka groups, startup, routing, key management and failure recovery.

## Check your understanding

Explain why notification has no foreign key into order-db. Explain why auth
unavailability does not invalidate an already-issued token. Then draw where a
transaction starts/ends and where a network call can fail.
