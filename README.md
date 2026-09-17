# SkillSphere

**Every claim about what a person knows comes with proof that can be checked.**

![Java](https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-6DB33F?logo=springboot&logoColor=white)
![Spring Modulith](https://img.shields.io/badge/Spring%20Modulith-2.1.1-6DB33F?logo=spring&logoColor=white)
![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=black)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)
![Kafka](https://img.shields.io/badge/Kafka-Redpanda-231F20?logo=apachekafka&logoColor=white)
![Cost](https://img.shields.io/badge/infra%20cost-%240-brightgreen)

A learning platform built around a simple inversion: most LMS products treat a *course* as the thing you own — watch videos 1, 2, 3, take a quiz, get a certificate that proves you finished, not that you learned. SkillSphere makes **the skill** the first-class entity instead. A course is evidence a skill was acquired. An assessment is a measurement instrument. A career is a target vector of skills. Everything else — the adaptive engine, the AI viva, the skill passport — follows from that one modeling decision.

## Demo

<video src="docs/media/skillsphere-demo-final.mp4" controls muted title="SkillSphere demo"></video>

> If the player above doesn't render (e.g. viewing outside GitHub), watch/download it directly: [`docs/media/skillsphere-demo-final.mp4`](docs/media/skillsphere-demo-final.mp4).

<details>
<summary><strong>Table of contents</strong></summary>

- [Demo](#demo)
- [Why this exists](#why-this-exists)
- [Monolith to microservices](#monolith-to-microservices)
- [Architecture](#architecture)
- [Tech stack](#tech-stack)
- [Running it locally](#running-it-locally)
- [Tests](#tests)

</details>

```
Pick a career goal        →  "Backend Java Developer"
        ↓
Diagnostic assessment     →  finds what you know and what you don't
        ↓
Skill profile             →  Java 90% · OOP 72% · Collections 45% · Concurrency 25%
        ↓
Gap analysis vs target role, personalised path, explained ("why this next?")
        ↓
Learn → practise → adaptive assessment
        ↓                    ↓
        ↓     wrong answer → misconception identified → targeted remediation
        ↓     3 related failures → instructor alerted live
        ↓
Real project → process ledger records the journey
        ↓
AI viva → defend your own submission, live, adaptive follow-up questions
        ↓
Evidence recorded → Skill Passport updated → Readiness recalculated
        ↓
Employer opens the passport, inspects the evidence
```

## Why this exists

Two 2026 findings turn out to be the same problem. Roughly a third of students at 20 US public research universities regularly use generative AI on assignments — a Brown take-home exam averaged 96% against a 65–80% historical range — and detectors have been abandoned faster than they were adopted. Meanwhile over 90% of employers say they'd prefer a candidate with a verifiable micro-credential, but 53% name *verifying the claim* as their main obstacle to actually hiring on skills.

Both gaps are the same gap: **nobody can trust a claim about what someone knows.** The literature's answer to the cheating crisis — oral defence, process documentation, authentic tasks — is almost entirely unbuilt software. SkillSphere builds it: an AI viva that questions a learner on their own submission (oral defence doesn't scale with human examiners; software makes it scale), a process ledger that records how the work was actually done, and a skill passport that carries the evidence, not just a grade.

Every score in this system comes from a named psychometric model — Elo, Item Response Theory, Bayesian Knowledge Tracing — not an averaged quiz percentage, and every recommendation exposes its reasoning chain. Those are the two things a skeptical reviewer checks first, so they're built in rather than bolted on.

## Monolith to microservices

**Phase 1** builds the full product as a modular Spring Boot monolith — [Spring Modulith](https://spring.io/projects/spring-modulith) enforces real module boundaries at build time, which is what turns Phase 2 into an actual extraction instead of a rewrite. **Phase 2** takes the same codebase and splits it into Spring Cloud microservices, one module at a time, each one verified live against the running system before the next starts.

| | Status |
|---|---|
| **Phase 1 — Monolith** | Feature-complete. All 11 phases built; three showpieces (adaptive diagnostic engine, AI viva, live arena with confusion detection) fully working, not stubbed. |
| **Phase 2 — Microservices** | In progress. Platform layer live (Eureka, Config Server, Gateway). 3 of 9 services fully extracted and verified end-to-end: **identity**, **notification**, **analytics**. Database-per-service, Resilience4j, Prometheus/Grafana and Zipkin tracing are scoped but not yet started. |

See [`SKILLSPHERE.md`](SKILLSPHERE.md) for the full design document — research citations, domain model, event catalogue, API surface, and the build order this project actually followed.

## Architecture

```
                              ┌──────────────────┐
                              │  React frontend   │  Vite · TypeScript · Tailwind
                              │   (Vercel-ready)   │  shadcn/ui · TanStack Query
                              └─────────┬─────────┘
                                        │
                              ┌─────────▼─────────┐
                              │   API Gateway      │  Spring Cloud Gateway (Server MVC)
                              │      :9000          │  routes by path, narrowest-first
                              └──┬───────┬───────┬─┘
                    ┌────────────┘       │       └──────────────┐
          ┌─────────▼────────┐ ┌─────────▼────────┐  ┌──────────▼─────────┐
          │  identity-service │ │ analytics-service │  │  skillsphere-backend │
          │       :8081        │ │      :8083         │  │   (the monolith)     │
          │  accounts · JWT     │ │  risk scoring ·     │  │  content · skill ·   │
          │  · admin audit      │ │  cohort dashboard   │  │  assessment · career │
          └─────────┬──────────┘ └─────────▲──────────┘  │  · verification ·     │
                    │                       │              │  realtime · gamif.   │
                    │   Kafka (Redpanda)     │              └──────────┬──────────┘
                    └──────────┬────────────┴──────────────────────────┘
                               │
                    ┌──────────▼───────────┐        ┌────────────────────┐
                    │ notification-service   │        │   Eureka registry    │
                    │         :8082           │        │        :8761          │
                    │  Kafka consumer only —  │        │  every service above  │
                    │  no DB, no Redis, no     │        │  self-registers here  │
                    │  security chain          │        └────────────────────┘
                    └──────────────────────────┘
                                                          ┌────────────────────┐
                                                          │    Config Server     │
                                                          │        :8888          │
                                                          │  centralised, per-    │
                                                          │  service overrides;   │
                                                          │  every service still  │
                                                          │  boots standalone      │
                                                          │  if this is down       │
                                                          └────────────────────┘

          Shared: PostgreSQL 16 · Redis · one physical database for now —
          see "Database-per-service" below for why that's a deliberate,
          documented interim step rather than an oversight.
```

Every extracted service keeps its own JWT validation (the same shared signing secret, no network round-trip per request) and, where it needs a fact from a module still inside the monolith, either a Kafka event bridge (async: identity's account events, assessment's response events) or — for the handful of cases that are a genuine synchronous read, not a fact worth publishing — a narrow, explicitly-interim, read-only query against the still-shared database, exactly documented as a placeholder for the real HTTP client that module's own extraction will bring.

### Why database-per-service is deferred

51 real foreign keys reference `users(id)` alone, spanning nearly every table in the schema. Dropping all of them and standing up per-service databases is genuine, separate infrastructure work — deliberately *not* combined with any single service's extraction, so that a large unverified risk (breaking referential integrity across the whole schema) is never bundled with another large unverified risk (does this service actually work once it's its own process). Each is proven independently; this one is next once every service is out.

## Tech stack

**Backend** — Java 21 · Spring Boot 4.1.1 · Spring Modulith 2.1.1 (Phase 1's module boundaries) · Spring Cloud 2025.1.2 "Oakwood" (Phase 2) · Spring Data JPA + PostgreSQL 16 (recursive CTEs for the skill graph) · Spring Security + JJWT · Redis (JWT denylist, rate limiting) · Kafka-wire-protocol via Redpanda · Flyway · springdoc-openapi

**Frontend** — React 19 · TypeScript · Vite · Tailwind CSS 4 · shadcn/ui (Radix primitives) · TanStack Query · Zustand · Recharts · STOMP.js (WebSocket client) · Framer Motion

**AI layer, entirely free** — [Ollama](https://ollama.com) running a local model generates the AI viva's questions; Claude Code pre-generated the item bank, distractors, misconception tags and skill graph as Flyway seed data during development, so none of that costs anything at runtime. The adaptive engine itself — Elo, IRT, Bayesian Knowledge Tracing — is plain deterministic Java, no LLM involved.

**Everything above runs free.** No paid APIs, no managed services, no hosting bill — see the [Constraints](SKILLSPHERE.md#18-constraints) section of the design doc for the reasoning.

## Running it locally

```bash
# 1. Infrastructure: Postgres, Redis, Redpanda, Mailpit
docker compose up -d

# 2. Platform layer (each in its own terminal, in this order)
cd discovery-server && ./mvnw spring-boot:run   # :8761 — Eureka
cd config-server    && ./mvnw spring-boot:run   # :8888 — centralised config
cd gateway           && ./mvnw spring-boot:run   # :9000 — public entry point

# 3. Extracted services
cd identity-service     && ./mvnw spring-boot:run   # :8081
cd notification-service && ./mvnw spring-boot:run   # :8082
cd analytics-service     && ./mvnw spring-boot:run   # :8083

# 4. The monolith — everything not yet extracted
cd backend && ./mvnw spring-boot:run             # :8080

# 5. Frontend
cd frontend && npm install && npm run dev         # :5173
```

Mailpit (dev SMTP capture) is at `http://localhost:8025`; the Eureka dashboard at `http://localhost:8761`. Every service also runs standalone against the monolith alone if you only start step 1 and step 4 — the platform layer is additive by design, not a hard dependency.

## Tests

```bash
cd backend            && ./mvnw test   # the monolith's own suite
cd identity-service    && ./mvnw test   # ported, independently green
```

Every extraction in this project is verified two ways: the automated suite (ported where it existed, or re-run against the reduced monolith to confirm nothing broke), and a live pass with real HTTP calls, real Kafka messages and real database rows — not just a clean boot log. The commit history is the honest record of that: what broke, what the actual root cause was, and what the fix looked like, not a tidied-up retelling.

---

*Built end-to-end with zero paid infrastructure — no managed services, no hosting bill, no paid APIs anywhere in the stack.*
