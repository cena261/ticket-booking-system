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

Copy `.env.example` to `.env` and fill in values (gitignored). Phase 1 boots with no external services; MySQL/Redis/Kafka arrive from Phase 2 onward.

## Git flow

Two long-lived branches: `dev` (working base) and `main` (never committed to directly). Each feature: short-lived `feat/<slug>` off `dev` -> PR -> merge into `dev` -> delete branch.
