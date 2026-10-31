# Lesson 5: run, observe and break the system safely

[Module home](../readme.md) | [Next: interview practice](06-interview.md)

All shell commands below are run from `7.microservices/`. Use local demo
accounts only. Never paste a real password or token into shared logs/commits.

## 1. Start and verify readiness

```bash
sh scripts/init-local.sh
docker compose config --quiet
docker compose up --build -d
sh scripts/wait-local.sh
docker compose ps
python3 scripts/smoke.py
```

Run initialization once. It refuses to overwrite existing keys/passwords.
Reusing volumes requires reusing the original DB password. Changing `.env`
does not reset an existing PostgreSQL database password.

The wait script has a 180-second bound and checks every application's health,
not merely open ports. Gateway health does not aggregate downstream health;
the script explicitly inspects all four containers.

If launch fails:

```bash
docker compose logs --tail=100 auth-service order-service notification-service api-gateway kafka
```

Check the first meaningful failure, such as key-file permissions, database
authentication, Flyway migration failure, unavailable topic or port conflict.
The auth container intentionally runs with the host UID/GID to read the
owner-only private key. Regenerating keys is not a fix for a routing error.

## 2. Make requests manually

For convenience, these examples use a **throwaway learning password**, not an
embedded application credential. Never use it for a real account.

```bash
curl -i -H 'Content-Type: application/json' \
  -d '{"email":"learner@example.com","password":"throwaway-learning-password"}' \
  http://localhost:8080/auth/register

curl -i -H 'Content-Type: application/json' \
  -d '{"email":"learner@example.com","password":"throwaway-learning-password"}' \
  http://localhost:8080/auth/login
```

Registration returns 201, repeated registration 409. Successful login returns
an `accessToken`, `tokenType` and `expiresIn` of 900 seconds.

Copy the access token into a local shell variable without printing it:

```bash
read -r TOKEN
```

Paste the token and press Enter. Generate a request key and create an order:

```bash
KEY=$(python3 -c 'import uuid; print(uuid.uuid4())')
curl -i -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"product":"Spring Interview Book","quantity":2,"unitPrice":19.99}' \
  http://localhost:8080/orders

curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/orders
curl -i -H "Authorization: Bearer $TOKEN" http://localhost:8080/notifications
```

Expect 201 with a `Location` and order body. Repeat the POST with the same key
and body: same order ID, no additional event intent. Change quantity with the
same key: 409. Notifications may appear after a short delay.
At the end, clear the shell token with `unset TOKEN`.

## 3. Inspect persistent state

These read-only queries show each service's boundary:

```bash
docker compose exec -T auth-db psql -U lab -d lab \
  -c 'SELECT id, email, created_at FROM accounts;'
docker compose exec -T order-db psql -U lab -d lab \
  -c 'SELECT id, owner_id, request_key, product, quantity FROM orders;'
docker compose exec -T order-db psql -U lab -d lab \
  -c 'SELECT event_id, order_id, created_at, published_at FROM outbox;'
docker compose exec -T notification-db psql -U lab -d lab \
  -c 'SELECT event_id, order_id, owner_id, message FROM notifications;'
```

Do not dump password hashes or tokens as interview evidence. Use order/event
IDs and the gateway `X-Request-Id` instead.

Inspect consumer assignment/lag:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server kafka:9092 --describe --group notification-service-v1
```

An empty/new group may have no offsets yet. Creating orders gives it records
to process.

## 4. Failure lab: stop Kafka, not the database

```bash
docker compose stop kafka
```

Create an order through curl using a **new** key. The order DB can commit and
return 201 even though publishing fails. Inspect the pending outbox row and
publisher error log. A pending row is recoverable intent, not a failed order.

Restore the broker:

```bash
docker compose start kafka
docker compose logs --tail=50 order-service
```

Wait for `published_at` and the notification to appear. The publisher retries
on its scheduled polling cycle. Kafka retains its volume on stop/start.

## 5. Failure lab: stop the consumer

```bash
docker compose stop notification-service
```

Create a new order. Its event can reach Kafka, but the inbox will not update
until the consumer returns. Start it again:

```bash
docker compose start notification-service
```

It resumes the same consumer group and processes retained events.
Retention is finite; an indefinite outage is not guaranteed recoverable.

## 6. Failure lab: inject a poison event

This is deliberately invalid local test data:

```bash
printf '%s\n' '{"version":999,"eventId":null}' | \
  docker compose exec -T kafka /opt/kafka/bin/kafka-console-producer.sh \
    --bootstrap-server kafka:9092 --topic orders.created.v1
```

Watch consumer retry logs. Then inspect the DLT:

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic orders.created.v1.DLT \
  --from-beginning --max-messages 1 --timeout-ms 10000
```

The original invalid payload should be present. This proves quarantine, not
business success. Fixing/replaying DLT data is a deliberate operational
workflow; it is not automatic in this lab.

## 7. Authorization lab

Try `/orders` with no token: 401. Alter a token: 401. Register another account
and use its token to fetch the first account's order ID: 404, and its lists do
not expose the first account's records.

The tests also exercise tokens without the required scope: 403. Registration
always grants the lab's fixed scopes, so a normal client cannot elevate or
arbitrarily change scopes.

## 8. Automated tests and what they cover

With a local JDK 21+ and Maven:

```bash
mvn -B -f pom.xml verify
```

The reactor builds every new service, not the old modules. Tests include real
RSA signature/claim checks, MVC validation/security, gateway forwarding to a
local HTTP server, publisher failure behaviour, an embedded Kafka DLT
experiment, and PostgreSQL persistence/constraint/rollback checks.

PostgreSQL tests use Testcontainers and **skip when Docker is unavailable**.
The other tests and embedded Kafka do not require Docker. A green build with
skipped PostgreSQL tests is not proof of database integration. Inspect the
Surefire summaries in each `target/surefire-reports/`.

`scripts/smoke.py` needs the running stack and verifies the complete
HTTP -> order DB -> outbox -> Kafka -> notification path.
The [GitHub workflow](../../.github/workflows/microservices.yml) runs Java 21
tests plus Compose startup and smoke checks on a Docker-enabled runner.
Workflow configuration is not a claim that a particular run passed.

## 9. Stop versus reset

```bash
docker compose down
```

This preserves volumes and local key/configuration files.

**Destructive, optional:** `docker compose down --volumes` deletes this
Compose project's database and Kafka volumes. Use it only when you intend to
erase all local lab accounts/orders/events. Never apply this to an unrelated
project or production environment. Local `.env`/keys remain; there is normally
no reason to regenerate them just to reset demo data.
