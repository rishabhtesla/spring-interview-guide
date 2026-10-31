# Microservices interview lab: build and explain one complete flow

[Project overview](../readme.md)

This module is a new, independent application using **Java 21, Spring Boot
3.5.7, Spring Cloud Gateway, PostgreSQL, Kafka and custom JWT authentication**.
It does not change or upgrade the older course examples.

The learning goal is concrete: register, log in, create an order through a
gateway, persist it safely, publish an event, and read the resulting
notification. Then explain what happens when a request is retried, Kafka is
unavailable, a token is invalid or another user guesses an order ID.

This is a runnable **end-to-end educational vertical slice**, not an enterprise
identity provider, production commerce system, or guarantee of interview
success. It makes the core mechanisms visible and provides failure labs and
worked interview questions.

## What you will build

```text
HTTP client
    |
    v
API Gateway :8080 -- only host-exposed port (localhost)
    |                         |
    | /auth/*                 | /orders and /notifications
    v                         | verifies JWT with public RSA key
Auth Service :8081            |
    | BCrypt password hashes  +--> Order Service :8082 --> order-db
    | signs JWT with private key           |                |
    v                                      |      order + outbox in one transaction
auth-db                                    v
                                      Outbox publisher
                                           |
                                           v
                                 Kafka orders.created.v1
                                           |
                                           v
                                Notification Service :8083 --> notification-db
                                           |
                               retries -> dead-letter topic on failure
```

Each service owns a separate PostgreSQL instance. There are no cross-service
SQL joins or shared database tables. The notification service learns about
orders through events, not by querying the order database.

## Start here: a guided reading map

| Lesson | Read | Outcome |
| --- | --- | --- |
| 1 | [Architecture and service boundaries](docs/01-architecture.md) | Explain why each process/database exists |
| 2 | [Custom authentication and authorization](docs/02-authentication.md) | Trace BCrypt -> JWT -> scope -> ownership |
| 3 | [Database transactions and API contracts](docs/03-database-and-api.md) | Explain migrations, idempotency and outbox atomicity |
| 4 | [Kafka and reliable event processing](docs/04-kafka.md) | Explain partitions, groups, retries, DLT and duplicates |
| 5 | [Runbook and failure experiments](docs/05-runbook.md) | Reproduce success and failure, inspect evidence |
| 6 | [Interview questions and extensions](docs/06-interview.md) | Answer with real code examples and trade-offs |

## Quick start

Requirements: Docker Engine/Desktop running, Docker Compose v2, OpenSSL,
Python 3 for the smoke script, internet access for dependencies/images, and
enough Docker memory (start with about 6 GB). Stop older modules using port
8080. The container build supplies Maven and Java 21; a local JDK is not
needed for the Compose launch.

From the repository root:

```bash
cd 7.microservices
sh scripts/init-local.sh
docker compose up --build -d
sh scripts/wait-local.sh
docker compose ps
docker compose logs --tail=50 auth-service order-service notification-service api-gateway
```

Initialization generates an owner-readable RSA private key and a random local
database password. They are ignored by Git and excluded from the build
context. Only auth receives the private key; the other services get the public
key. Do not commit `.env` or `.secrets`.

Compose waits for databases and Kafka, but `service_started` does not mean a
Java application is ready. The wait script checks all four application health
checks with a bounded wait; then:

```bash
curl -i http://localhost:8080/actuator/health
python3 scripts/smoke.py
```

The smoke script makes new random local accounts; it does not need hard-coded
credentials. It asserts registration/login, invalid credentials, invalid JWTs,
validation, ownership, duplicate requests and eventual notification delivery.
Its success message is meaningful only after all assertions pass.

Stop without deleting your database and Kafka volumes:

```bash
docker compose down
```

See the [runbook](docs/05-runbook.md) for request examples, failure labs, test
commands and explicit instructions for an intentional destructive data reset.

## What is implemented, and what is not

| Implemented | Deliberately not implemented |
| --- | --- |
| Custom registration/login, BCrypt hashing, 15-minute RS256 JWTs | OAuth2/OIDC protocol server, refresh tokens, logout revocation, password recovery, MFA |
| Gateway routing, token/scope checks, request ID logging and timeouts | Distributed tracing backend, rate limiting, circuit breakers, service mesh |
| JWT verification again inside resource services, owner-scoped queries | Admin APIs and a configurable role-management system |
| PostgreSQL per service, Flyway migrations, database constraints | Catalog/inventory/payment services and full commerce workflows |
| Idempotency keys, atomic order/outbox writes, polling publisher | CDC/Debezium relay and distributed two-phase commit |
| Kafka partitions, consumer group, durable deduplication, retries and DLT | Multi-broker HA, Kafka TLS/SASL and Schema Registry |
| Unit/web/crypto tests, embedded Kafka test, PostgreSQL container tests, HTTP smoke script | A claim that tests passed without actually running their required environment |

Order prices are supplied by the client **only to keep the lab self-contained**.
A real store must look up trusted catalog pricing and stock, not trust a
browser's price. Notifications are persisted inbox messages, not actual email
delivery. Sending an email would require its own idempotency/retry design.

## Source map

| Path | Responsibility |
| --- | --- |
| `pom.xml` | Maven reactor for this module only |
| `common/` | Small event contract and JWT validation helper |
| `auth-service/` | Credentials, account DB and token issuance |
| `api-gateway/` | Reactive routing and boundary security |
| `order-service/` | Validated order API, ownership, idempotency and outbox |
| `notification-service/` | Event consumer, inbox DB, retries and owner-scoped API |
| `compose.yml`, `Dockerfile` | Local infrastructure and repeatable application packaging |
| `scripts/init-local.sh`, `scripts/smoke.py` | Local key setup and end-to-end assertions |

Sharing `common` is a teaching convenience, not a shared domain/database.
In independent production teams, publish versioned event schemas/contracts
and avoid requiring every service to release in lockstep.
