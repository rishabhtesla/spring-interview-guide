# Lesson 4: Kafka, retries and eventual consistency

[Module home](../readme.md) | [Next: hands-on runbook](05-runbook.md)

## Explain Kafka without jargon first

Kafka stores a durable sequence of records. Producers append records;
consumers read them and track their position. A consumer that is temporarily
offline can resume, provided the records are still within retention.

A **topic** is a named stream. A **partition** is one ordered sequence within
that stream. An **offset** identifies a position inside one partition.
A **consumer group** coordinates consumers sharing the work.

This lab creates `orders.created.v1` and `orders.created.v1.DLT`, each with
three partitions and replication factor one. Kafka uses KRaft, not ZooKeeper.
The single broker/controller is convenient locally but has no broker HA.

## Event contract

[OrderCreated](../common/src/main/java/com/example/interview/common/OrderCreated.java)
defines the JSON record:

```json
{
  "version": 1,
  "eventId": "11111111-1111-1111-1111-111111111111",
  "orderId": "22222222-2222-2222-2222-222222222222",
  "ownerId": "33333333-3333-3333-3333-333333333333",
  "product": "Spring Interview Book",
  "quantity": 2,
  "unitPrice": 19.99,
  "occurredAt": "2026-01-01T00:00:00Z"
}
```

`eventId` identifies a particular event for deduplication; `orderId` identifies
the business object and is the Kafka key. Version is validated, not silently
ignored. The consumer rejects unsupported or invalid contracts.

Both producer and consumer use String serialization, with Jackson explicitly
handling JSON. This avoids requiring Kafka records to carry arbitrary Java
class type headers. The common Java record is still a build-time coupling;
independent teams should manage schemas and compatibility deliberately.

## Partitions, keys and ordering

The publisher keys records by order ID. The same key maps to a partition under
the normal partitioner, so related records can share an ordered log.
Kafka does not guarantee one global ordering across every partition.
Changing partition count can change key-to-partition mapping.

This lab emits only one event per newly accepted order. It therefore does not
prove correct ordering for an extended order lifecycle with several events per
order. Such an extension needs aggregate sequence/version rules and an outbox
relay design that preserves the required order.

Three partitions allow up to three consumers in this group to actively share
the topic; extra consumers will be idle for it. Different groups can each
read the same records independently.

## Producer acknowledgement versus business delivery

The producer enables idempotence and `acks=all`. This reduces duplicates from
producer retries and waits for the in-sync replica acknowledgement policy.
With replication factor one, "all" is still only one broker; it does not create
redundancy. Real durability depends on replication, ISR policy and operations.

The outbox publisher waits for a send result before marking a row published.
That proves broker acknowledgement, not that notification was created.
End-to-end confirmation comes from the consumer database/result.

Broker retries, outbox retries and HTTP retries are three different layers:

| Layer | Retry reason | Duplicate defence |
| --- | --- | --- |
| HTTP create | Client lost the response | Owner + idempotency-key uniqueness |
| Outbox relay | Broker unavailable or ambiguous send | Stable event ID + consumer deduplication |
| Kafka producer | Transient producer delivery failures | Producer idempotence within its guarantees |
| Consumer | Processing/database failure | Event-ID primary key in notification DB |

## Consumer transaction and offset

Read [OrderListener](../notification-service/src/main/java/com/example/interview/notifications/OrderListener.java)
and [NotificationStore](../notification-service/src/main/java/com/example/interview/notifications/NotificationStore.java).
The listener deserializes the record and invokes a transactional store method.
If the DB transaction fails, the listener throws. It does not return pretend
success.

Auto-commit is disabled and the container uses record acknowledgement mode.
After the listener successfully returns, its offset is eligible for commit.
There is still a crash window between DB commit and offset commit. The record
may be delivered again; the unique event ID prevents duplicate inbox rows.

This is **at-least-once processing with an idempotent database effect**.
It is not a PostgreSQL/Kafka atomic transaction or universal "exactly once".
Kafka transaction guarantees do not automatically cover external database or
email side effects.

## Retry and dead-letter topic

[KafkaConfiguration](../notification-service/src/main/java/com/example/interview/notifications/KafkaConfiguration.java)
configures the error handler. Retryable processing failures get two retries
after the initial attempt, with a one-second fixed backoff. Spring's default
fatal-error classification can recover some exception types without retrying.
Each failed attempt is logged with topic/partition/offset.

After exhaustion, the recoverer publishes the original record to the DLT on
the same partition, with diagnostic headers. DLT publication failure is not
treated as successful recovery. The handler must not silently skip a record
whose recovery write failed.

A DLT is a quarantine/work queue, not a trash bin. Investigate the failure,
fix the contract or dependency, and replay deliberately. A prolonged database
outage can move valid events to the DLT after retries; restoration alone does
not automatically replay them. This operational choice must be understood.

The lab does not expose an unauthenticated HTTP replay API.

## Eventual consistency

An order can appear before its notification. That temporary difference is
expected. The smoke test polls notifications with a deadline instead of
assuming instant equality between databases.

Measure consumer lag, pending outbox count/age, DLT growth and processing
latency. A health endpoint alone cannot prove events are flowing.
The lab includes logs and inspectable state; it does not install a full
Prometheus/Grafana tracing/alerting platform.

## Interview self-check

Explain why restarting the publisher can duplicate an acknowledged event,
why another consumer group reads the stream independently, and what happens
when a consumer commits an offset before persisting its result. Describe how
you would investigate a growing DLT rather than repeatedly restarting services.
