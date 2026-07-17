# Ticket App

High-concurrency event ticket booking system (concert/event, quantity-based ticket types, no seat map). Goal: **zero oversell** under high concurrency with maximum sustained throughput on the buy path.

Stack: Java 21, Spring Boot 4.1, Maven multi-module, MySQL 8, Redis + Redisson, Kafka (KRaft), Elasticsearch, Spring Security + JWT, Prometheus/Grafana/ELK, Docker Compose.

## Module layout

Layer-first. Dependency direction: `start -> controller -> application -> infrastructure -> domain`.

| Module | Responsibility |
|--------|----------------|
| `ticket-domain` | Entities, value objects, repository interfaces, domain services |
| `ticket-infrastructure` | JPA impls, Redis/Redisson, Kafka producer, external gateways |
| `ticket-application` | Use-case services, schedulers, Kafka consumers, outbox publisher |
| `ticket-controller` | REST controllers, request/response DTOs, validation |
| `ticket-start` | @SpringBootApplication, application.yml, metrics registry, main class |

## Prerequisites

- JDK 21
- Maven wrapper (`./mvnw`) is bundled; no system Maven required

## Build & test

```bash
./mvnw clean verify
```

## Run

```bash
./mvnw -pl ticket-start spring-boot:run
# or run the packaged jar under ticket-start after a build
```

Health check:

```bash
curl http://localhost:8080/actuator/health   # => {"status":"UP"}
```

## Configuration

Copy `environment/.env.example` to `environment/.env` and fill in values (gitignored). Docker Compose reads it; the app's local defaults live in `application-local.yml`.

## Observability

Two Docker Compose profiles, run from `environment/`:

```bash
docker compose --profile bench up -d   # load-test stack: mysql, redis, kafka, prometheus, grafana
docker compose --profile full  up -d   # dev/demo: the above plus elasticsearch, logstash, kibana
```

ELK never runs under `bench`: at the target request rate a line-per-request log is a firehose that would contend for the same cores the benchmark is measuring.

| Surface | URL |
|---------|-----|
| App metrics | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin/admin) |
| Kibana | http://localhost:5601 |

Grafana's Prometheus datasource and the `Ticket App - Overview` dashboard (HTTP, buy-path KPIs, JVM, Hikari, Kafka/outbox lag) are provisioned as code from `environment/grafana/provisioning`. Logs ship as JSON over TCP to Logstash and land in Elasticsearch under `logs-*`; the appender is non-blocking and fails open when Logstash is down.

Log levels default to INFO and are env-toggled — set to DEBUG to trace every Redis/DB access while debugging, and leave at INFO (or WARN via the `bench` profile) for benchmarks:

| Variable | Covers |
|----------|--------|
| `LOG_LEVEL_APP` | all `com.ticketapp` code |
| `LOG_LEVEL_REDIS` | Redis stock counter operations |
| `LOG_LEVEL_LOCK` | Redisson distributed lock acquire/release |
| `LOG_LEVEL_SQL` | Hibernate SQL statements |

## Git flow

Two long-lived branches: `dev` (working base) and `main` (never committed to directly). Each feature: short-lived `feat/<slug>` off `dev` -> PR -> merge into `dev` -> delete branch.
