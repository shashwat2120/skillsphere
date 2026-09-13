# SkillSphere — Master Project Document

> **Single source of truth.** Everything about this project lives here: the idea, the research behind it, the full technology stack, architecture, domain model, build order and demo plan.
>
> **For a new Claude session:** read this file first and you have complete context.
> **Keep it current:** when a decision changes, update this file in the same commit as the code.

**Sprint 5** — Java + Spring Boot (modular monolith) · **Sprint 6** — Enterprise Java + Spring Cloud (microservices)

---

## Table of Contents

1. [The Idea](#1-the-idea)
2. [Why This Product — The Research](#2-why-this-product--the-research)
3. [Users](#3-users)
4. [Features — The Nine Modules](#4-features--the-nine-modules)
5. [Technology Stack](#5-technology-stack)
5b. [Why These Choices — The Research](#5b-why-these-choices--the-research)
6. [Authentication & Security](#6-authentication--security)
7. [Performance](#7-performance)
8. [UI & Design System](#8-ui--design-system)
9. [Real-time Architecture](#9-real-time-architecture)
10. [Domain Model](#10-domain-model)
11. [Event Catalogue](#11-event-catalogue)
12. [API Surface](#12-api-surface)
13. [Scope — Sprint 5 vs Sprint 6](#13-scope--sprint-5-vs-sprint-6)
14. [Build Order](#14-build-order)
15. [Testing Strategy](#15-testing-strategy)
16. [The Demo](#16-the-demo)
17. [Credibility Rules](#17-credibility-rules)
18. [Constraints](#18-constraints)
19. [Decision Log](#19-decision-log)
20. [Sources](#20-sources)

---

## 1. The Idea

### One line

**SkillSphere is a learning platform where every claim about what a person knows comes with proof that can be checked.**

### In plain words

A normal LMS works like this: buy a course, watch videos 1, 2, 3, take a quiz, get a certificate. Everyone gets the same path regardless of what they already know, and the certificate only says "finished" — it proves nothing about ability.

SkillSphere inverts that:

```
Pick a career goal          →  "Backend Java Developer"
        ↓
Diagnostic assessment       →  finds what you know and what you don't
        ↓
Skill profile built            Java 90% · OOP 72% · Collections 45% · Concurrency 25%
        ↓
Gap analysis vs target role
        ↓
Personalised path           →  skips what you know, targets your gaps, explains why
        ↓
Learn → practise → adaptive assessment
        ↓                        ↓
        ↓         wrong answer → misconception identified → targeted remediation
        ↓                        ↓
        ↓         3 related failures → instructor alerted live
        ↓
Real project                →  process ledger records the journey
        ↓
AI Viva                     →  defend your own submission, live
        ↓
Evidence recorded → Skill Passport updated → Readiness recalculated
        ↓
Employer searches, opens the passport, inspects the evidence
```

### The founding design decision

**`Skill` is the first-class entity, not `Course`.**

| Traditional LMS | SkillSphere |
|---|---|
| Course is the product | Skill is the product |
| Course completion is the goal | Skill mastery is the goal |
| A lesson is content you consume | A lesson is *evidence* toward a skill |
| A quiz is a gate | A quiz is a *measurement instrument* |
| A certificate says "finished" | A passport says "can do — here's proof" |
| Career is not modelled | A career is a *target vector of skills* |

Everything in this document follows from that one decision — including why the Sprint 6 services have real boundaries rather than being a CRUD app chopped into pieces.

---

## 2. Why This Product — The Research

Two things broke at the same time, and they turn out to be one problem.

### Schools can no longer tell who did the work

- Around **one in three students** at 20 US public research universities regularly use generative AI on assignments (Cornell, May 2026)
- A Brown take-home exam — *harder* than previous years — averaged **96%** against a **65–80%** historical range
- **AI detectors were tried and abandoned.** Yale, Vanderbilt, Johns Hopkins and Indiana discourage them; roughly a dozen universities disabled Turnitin's AI detection
- Researchers describe assessment reform as *"necessary and urgent"*

The take-home assignment — what every LMS on earth is built around — no longer measures anything.

### Employers can no longer trust credentials

- **90%+** of employers would rather hire someone with a relevant micro-credential
- Only **46%** are expanding skills-based hiring
- **53%** name *verifying skill claims* as their main obstacle

### People quit almost immediately

- Free online courses complete at **5–15%**
- **50% of all dropouts happen in the first two weeks**
- But past the first **30%** of a course → **75%** chance of finishing
- Why they quit: no time 38%, lost motivation 25%, too difficult 14%, irrelevant 10%
- What helps: early hands-on projects **−22%**, cohort/social **−28%**, adaptive learning **−28%**

### Buyers now demand proof of efficacy

The 2026 market reset around *"does this actually work?"* — pre/post assessment, adaptive pathways, real analytics.

### The realisation

> Schools can't trust that a student did their own work.
> Employers can't trust that a certificate means anything.
>
> **Same problem: nobody can trust any claim about what a person knows.**

So the positioning is **not** "personalised learning" — hundreds of products do that. It is **verifiable learning**.

**Pitch line:** *"Learning platforms tell you someone finished a course. We prove they can do the job — and show you the evidence."*

---

## 3. Users

| Persona | What they do | Key screens |
|---|---|---|
| **Learner** | Sets a career goal, takes diagnostics, follows a generated path, learns, practises, builds projects, defends them in viva, owns a skill passport | Dashboard, Path, Lesson, Practice, Project, Viva, Passport |
| **Instructor** | Authors content and items, tags skills and misconceptions, reviews projects, responds to confusion alerts, runs live arenas | Authoring, Item bank, Class heatmap, Alerts, Arena control, Review queue |
| **Admin** | Manages the skill graph and career catalogue, approves instructors, moderates, monitors platform health | Skill graph editor, Career roles, User management, Platform dashboard |
| **Employer** *(stretch)* | Searches verified skills, opens a candidate passport, inspects evidence | Talent search, Passport viewer, Evidence trail |

---

## 4. Features — The Nine Modules

These are Sprint 5 modules. In Sprint 6 each becomes a microservice — boundaries are drawn now so extraction is a move, not a rewrite.

### Module 1 — Identity
Registration, login, JWT issue and refresh, roles (learner / instructor / admin / employer), instructor approval workflow, account status with instant revocation, MFA, session and device management, profile.

Full detail in [§6 Authentication & Security](#6-authentication--security).

### Module 2 — Skill Engine ⭐ *the heart*

- **Skill graph** — skills as nodes, prerequisites as directed edges (a DAG), cycle detection on edit
- **Ability estimation** — **Elo** and **IRT (2PL)** so questions have *difficulty* and learners have *ability* on one scale
- **Mastery tracking** — **Bayesian Knowledge Tracing** gives P(mastery) over time, not a running average
- **Item calibration** — difficulty starts from an instructor-declared prior and self-corrects from real responses (the cold-start answer)
- **Skill decay** — mastery erodes with disuse, triggering refreshers
- **Gap analysis** — current profile vs target role requirements
- **Readiness scoring** — weighted aggregation across a role's required skills

**All deterministic maths.** No AI service, no cost, no latency, predictable in a live demo.

### Module 3 — Content
Courses, modules and lessons (text / video-embed / resource) — but **every lesson is tagged to the skills it teaches**, and every item to the skills it measures. Content is an *input* to the skill engine, not the product. Authoring CRUD, ordering, draft/publish/archive lifecycle, ownership policies.

### Module 4 — Assessment Engine

- **Diagnostic** — adaptive item selection maximising information gain (ask the question that tells us the most, not the next in a list)
- **Adaptive practice** — difficulty tracks ability in real time
- **Misconception diagnosis** — every *wrong option* is tagged with the belief it reveals. Choosing option C doesn't mean "wrong," it means *"believes HashMap preserves insertion order"* — and remediation targets that belief, not the whole topic
- **Checkpoints** — mastery confirmation before a path advances
- Attempt history, per-item response records, response timing

### Module 5 — Verification Engine ⭐ *the identity of the product*

**5a. Process ledger** — records *how* the work happened: draft snapshots over time, revision history, run/test events, large-paste events, declared AI usage, time-on-task, points of struggle. Real learning leaves a messy human trail; a pasted answer appears fully formed with no history.

> **We are not detecting AI — we are recording evidence of real work.** That distinction is exactly why detectors failed and this won't.

**5b. AI Viva** — the showpiece. After submission, the system generates questions **from the learner's own submission** and asks adaptive follow-ups:

> *"On line 14 you chose a HashMap. Why not a TreeMap?"*
> *"Your design has no retry on that call. Was that deliberate?"*

Cannot be outsourced: live, specific to *their* artefact, and the questions don't exist until they submit.

Universities already know oral defence solves cheating. They don't use it because a human viva for 300 students is impossible. **Oral defence is the accepted answer and it doesn't scale. Software makes it scale.** That is the product thesis in one feature.

**5c. Declared AI use** — learners state what they used AI for and defend their edits. Banning is unenforceable and poor preparation for real work; assessing *judgement about AI* is the honest response.

**5d. Evidence store** — every verified outcome (assessment, project, viva, instructor sign-off) becomes an immutable evidence record linked to a skill, with a weight. The passport is derived from evidence, never self-reported.

### Module 6 — Career Engine
Career role catalogue, required-skill vectors with weights and thresholds, gap computation, **learning path generation** (topological walk of the skill DAG filtered by gaps, ordered by prerequisites and role weighting), re-planning as mastery changes, **skill passport** with readiness score.

**Explainability lives here.** Every path step stores its rationale:

```
Why "Collections" next?
├── Prerequisite "OOP" mastered (0.87) ✓
├── Diagnostic flagged Collections weakest (0.45)
├── Required by target role: Backend Java Developer (weight 0.8)
└── 3 gap-questions failed: HashMap vs TreeMap
```

Cheap to build — the data already exists, we persist and surface the decision trace — and it is the complete answer to "is your AI a black box?"

### Module 7 — Real-time Engine
Live arena with leaderboard, confusion detection, class heatmap, presence, live passport updates, push notifications. Full detail in [§9](#9-real-time-architecture).

### Module 8 — Analytics & Early Intervention
Event-sourced learning events feeding:

- **At-risk scoring** — engagement slope, performance trend, failure clustering, inactivity
- **Week-1-and-2 rescue** — aimed where 50% of dropouts actually happen
- **Instructor dashboards** — cohort heatmaps, item analysis, funnels
- **Learner analytics** — progress, projected readiness
- **What-if simulator** — drag study-hours-per-week, see projected readiness over six months

### Module 9 — Notification
In-app notification store, real-time delivery, digest emails (async via queue), preferences.

---

## 5. Technology Stack

### Backend

| Tool | Version | Purpose |
|---|---|---|
| **Java** | 21 (LTS) | Records, pattern matching, virtual threads |
| **Spring Boot** | 3.x | Core framework |
| **Spring Web** | — | REST API (stateless JSON) |
| **Spring Security** | — | Authentication + role-based authorization |
| **JJWT** | — | JWT creation/validation |
| **Spring Data JPA** | — | Persistence (Hibernate) |
| **Spring WebSocket** | — | Real-time via STOMP |
| **Spring Validation** | — | Request validation |
| **Spring Actuator** | — | Health, metrics |
| **Lombok** | — | Boilerplate removal |
| **MapStruct** | — | Compile-time DTO mapping |
| **Flyway** | — | Versioned migrations |
| **springdoc-openapi** | — | Swagger UI |
| **Bucket4j** | — | Rate limiting |
| **webauthn4j** | — | Passkeys |
| **HikariCP** | built-in | Connection pooling |

### Database & Cache

| Tool | Purpose |
|---|---|
| **PostgreSQL 16** | Main database — recursive CTEs for the skill graph, JSONB for rationale payloads |
| **Redis** | JWT denylist, live leaderboards, arena state, caching |

### Frontend

| Tool | Purpose |
|---|---|
| **React 18** | UI framework |
| **Vite** | Build tool + dev server |
| **Tailwind CSS** | Styling, dark mode |
| **shadcn/ui + Radix** | Professional accessible component layer |
| **React Router** | Routing |
| **TanStack Query** | Server state — caching, refetch, optimistic updates |
| **Zustand** | Client state |
| **Axios** | HTTP + JWT refresh interceptor |
| **Recharts** | Skill radar, passport bars |
| **STOMP.js + SockJS** | WebSocket client |
| **Framer Motion** | Micro-interactions |
| **Sonner** | Toasts |
| **Lucide** | Icons |

### AI Layer — all free

| Tool | Purpose |
|---|---|
| **Claude Code** | Pre-generate question banks, distractors, misconception tags, skill graphs, seed data → shipped as Flyway migrations |
| **Ollama** (Llama 3.1 8B / Qwen 2.5) | Runs locally — generates viva questions. Free forever, offline |
| **Gemini / Groq free tier** | Fallback if local inference is too slow |

> The adaptive engine — **Elo, IRT, BKT** — is plain Java maths. **The maths is the meal; the LLM is the garnish.**

### Dev Tools

| Tool | Purpose |
|---|---|
| **Maven** | Build & dependencies |
| **Docker + Docker Compose** | Postgres + Redis + app in one command |
| **Git + GitHub** | Version control |
| **IntelliJ IDEA Community** | IDE — far better for Spring than VS Code |
| **Swagger UI / Postman** | API testing |
| **DBeaver / pgAdmin** | Database browsing |
| **Cloudflare Tunnel** | Expose localhost for the QR phone demo |

### Sprint 6 additions

| Tool | Purpose |
|---|---|
| **Spring Cloud Gateway** | API gateway — routing, auth filter, rate limiting |
| **Eureka** | Service discovery |
| **Spring Cloud Config** | Centralised configuration |
| **Kafka** (or **Redpanda**) | Event bus |
| **Resilience4j** | Circuit breaker, retry, bulkhead |
| **Micrometer Tracing + Zipkin** | Distributed tracing |
| **Prometheus + Grafana** | Metrics & dashboards |

### Hosting — zero budget

| Need | Free option |
|---|---|
| Backend | **Oracle Cloud Always Free** (~4 ARM cores, 24GB RAM, no expiry) |
| Postgres | Neon or Supabase |
| Redis | Upstash |
| Frontend | Vercel / Cloudflare Pages |
| Email | Brevo or Resend free tier (async via queue) |
| Public URL | Cloudflare Tunnel |

**For the review, run locally under Docker Compose** — no cold starts, no rate limits, works if venue WiFi dies.

**Deliberately cut** (genuinely cost money, never differentiators): video transcoding, WebRTC/TURN, managed auth (Auth0/Clerk), managed vector DBs.

---

## 5b. Why These Choices — The Research

Every choice below was made for a reason specific to *this* project, not because it is popular. If a reviewer asks "why this and not that," the answer is here.

### Frontend: React + Vite — **not** Next.js

This is the choice most people get wrong for an app like ours.

| Factor | Verdict |
|---|---|
| **Our app is 100% behind authentication** | Google never sees a logged-in dashboard. SSR and SSG — Next.js's core value — deliver **zero benefit** here |
| **We already have a backend** | Spring Boot *is* the backend. Next.js would add a **second server runtime (Node)** to a project whose entire point is Enterprise Java. Two backends muddies the architecture story in a microservices review |
| **Bundle size** | Vite SPA ~42KB vs Next ~92KB in comparable builds |
| **Deployment cost** | A static SPA is the cheapest, most portable deploy target that exists — free on Cloudflare Pages with **no Node process to host**. At zero budget this matters |
| **Real-time heavy** | Our app is WebSocket-driven and highly interactive. That work is client-side regardless; SSR actively gets in the way |
| **Complexity** | No `'use client'` / `'use server'` boundary to reason about — cognitive overhead for no benefit |

Industry guidance for 2026 is explicit: *use plain React with Vite for internal tools, admin dashboards and authenticated SPAs where server-side rendering adds no measurable value.* That is exactly our product.

**When Next.js would have won:** public SEO-indexed content, a marketing site, e-commerce. We have one public landing page — not worth a second runtime.

### UI: shadcn/ui + Radix + Tailwind — not MUI or Ant Design

| Option | Why not / why yes |
|---|---|
| **MUI** | Mature and broad, but everything built with it **looks like Material Design**. Instantly recognisable as a template, and fighting its theming to look custom is real work |
| **Ant Design** | Excellent for data-heavy B2B admin, but has an equally recognisable house style and a heavy runtime |
| **shadcn/ui** ✅ | **Not a dependency** — `npx shadcn add button` copies the component source into your repo. You own it, you edit it, it looks like *your* product. **Zero runtime bundle overhead** — ship only what you use. Built on **Radix**, so keyboard navigation and ARIA are correct by default, which is how we hit WCAG AA without hand-rolling accessibility |

2026 guidance puts shadcn/ui as the default for new React projects using Tailwind — which is us.

### Database: PostgreSQL — not MySQL

Three reasons, all specific to features this project actually needs:

**1. Recursive CTEs for the skill graph.** The skill graph is a DAG, and the central question — *"what is this learner ready to study next?"* — is a prerequisite traversal. PostgreSQL's `WITH RECURSIVE` answers it in **one query with a cycle guard**, instead of N+1 round trips through JPA. This is the single hottest query in the product.

**2. JSONB for the explainability trace.** `PathStep.rationale`, `ProcessEvent.payload` and `VivaTurn.evaluation` are semi-structured and evolve. PostgreSQL's **JSONB is binary, indexable (GIN) and queryable** — MySQL's JSON type is materially weaker here. Our "Why this?" feature is built directly on this.

**3. Analytics.** Window functions, partial indexes, array types and richer index families (GIN/GiST) do the work for at-risk scoring and item analysis.

Also: it is free, the free hosting tiers we plan to use (Neon, Supabase) are Postgres, and **pgvector** is available in-database if we ever want embeddings — no separate vector database to pay for.

### The rest of the stack

| Choice | Why it, specifically |
|---|---|
| **Java 21** | **Virtual threads** matter enormously for a WebSocket-heavy app — thousands of concurrent connections without thread-pool tuning. Plus records for DTOs and pattern matching. LTS, so supported |
| **Spring Boot 3** | Sprint requirement, but independently correct: it is the only Java framework with the full **Spring Cloud** ecosystem we need in Sprint 6, so Sprint 5 flows into Sprint 6 with no framework change |
| **Redis** | Two needs nothing else serves as well: the **JWT denylist** requires sub-millisecond lookup on *every* request, and **leaderboards** are exactly what sorted sets (`ZADD`/`ZRANGE`) were built for. Arena state is ephemeral and belongs in memory, not Postgres |
| **JWT over server sessions** | Sprint 6 puts services behind a gateway — stateless auth is required for that to work. We accept JWT's one real weakness (no native revocation) and **fix it with the Redis denylist** |
| **Flyway over Liquibase** | Plain SQL migrations are readable in a code review. Liquibase's XML/YAML abstraction is indirection we gain nothing from |
| **Maven over Gradle** | Declarative, predictable, best-in-class IDE support, and the sprint standard. Gradle builds faster but adds a DSL to learn for no benefit at this size |
| **Testcontainers over H2** | **Non-negotiable here.** H2 does not support our recursive CTEs or JSONB the way Postgres does — testing against H2 would be testing a *different database* than production. Testcontainers runs real Postgres in Docker |
| **MapStruct over ModelMapper** | Compile-time generated mapping — no reflection cost, and mapping errors are caught at build time instead of runtime |
| **TanStack Query over raw fetch/Redux** | Server state is **not** client state. Caching, refetching, stale-while-revalidate, and request deduplication come free — this is most of what makes the UI feel fast |
| **Zustand over Redux** | Once TanStack Query owns server state, very little genuine client state remains. Redux's boilerplate would be ceremony without purpose |
| **Ollama over a hosted LLM API** | Zero cost, works offline, no rate limits during a demo, and "we run inference locally" is a strong privacy answer in review |
| **Argon2id over BCrypt** | Memory-hard, so it resists GPU cracking in a way BCrypt does not. Native support in Spring Security |

---

## 6. Authentication & Security

Target: **stronger than what most production apps ship.**

### Password & token layer

| Choice | Why this over the common option |
|---|---|
| **Argon2id** hashing | Memory-hard, resists GPU cracking — stronger than BCrypt. Native in Spring Security |
| **Access token 15 min + refresh rotation** | Stolen tokens expire fast; each refresh invalidates the old one, making replay detectable |
| **httpOnly + Secure + SameSite cookies** | **Never `localStorage`** — any XSS reads it instantly. The most common auth mistake there is |
| **Redis denylist** | JWT can't normally be revoked. This gives instant logout/suspension, mid-session and mid-socket |

### Multi-factor — both free

- **TOTP** (Authenticator apps) — shared secret, QR enrollment, 6-digit rolling code. No SMTP, no SMS, no cost
- **Passkeys / WebAuthn** (`webauthn4j`) — fingerprint / Face ID / security key. **Phishing-proof, and the actual current state of the art**
- **Backup recovery codes** at enrollment so nobody is permanently locked out

### Hardening

- **Bucket4j rate limiting** — per-IP *and* per-account with exponential backoff. This is what stops credential stuffing
- **Account lockout** after repeated failures
- **Breached-password check** at signup via HaveIBeenPwned k-anonymity (free; the password never leaves the machine)
- **Generic errors** — "invalid email or password," never "that email isn't registered" (prevents enumeration)
- **Session ID rotation on login** (blocks session fixation)
- **Device/session manager** — user sees active sessions and revokes any
- **Auth audit log** — new device, password change, MFA change
- **Security headers** — HSTS, CSP, X-Frame-Options
- **Method-level `@PreAuthorize`** plus ownership policies
- **Google OAuth** as a convenience path (free)
- **OWASP dependency scanning** in CI

### Email note
Mail is always sent **asynchronously via a queue**, never inline in a request. Gmail SMTP requires 2-Step Verification and a 16-character **App Password** (the normal password stopped working in 2022). Production-shaped alternative: Brevo/Resend with SPF, DKIM and DMARC.

---

## 7. Performance

### Backend — target API p95 under 200ms

| Technique | Detail |
|---|---|
| **Redis caching** | Skill graphs, career definitions, leaderboards — read constantly, changed rarely |
| **Kill N+1 queries** | `@EntityGraph` / fetch joins. The #1 cause of slow Spring APIs |
| **DTO projections** | Select only needed columns — never load a full entity graph to render a card |
| **Indexes** | Every FK and every hot query path |
| **Virtual threads (Java 21)** | High concurrency without thread-pool tuning |
| **Async non-critical work** | Email, analytics, event writes — off the request path |
| **Pagination / cursor paging** | Never unbounded lists |
| **GZIP/Brotli compression** | One config line, large win |
| **ETags + cache headers** | Browser skips unchanged responses |
| **Recursive CTEs** | Skill-graph traversal in one query, not N+1 JPA walks |

**Hot path:** item selection runs on *every answer* — must stay under 100ms.

### Frontend — target LCP under 2.5s

| Technique | Detail |
|---|---|
| **Route-level code splitting** | `React.lazy` — load only the current page |
| **TanStack Query caching + prefetch** | Hover a link → data loads before the click |
| **Skeleton screens** | Perceived speed beats actual speed. Never a blank spinner |
| **Optimistic updates** | UI responds instantly, reconciles after |
| **Debounced search** | No request per keystroke |
| **Virtual scrolling** | Long lists render only visible rows |
| **Vite optimization** | Tree shaking, chunk splitting, asset hashing |
| **Font preload + `font-display: swap`** | No invisible-text flash |

---

## 8. UI & Design System

Goal: **looks like a professional product, not a student project.**

The single biggest lever is **shadcn/ui** — Radix primitives + Tailwind, copy-pasted into the repo rather than a dependency you fight. Accessible by default, fully themeable, free.

| Layer | Choice |
|---|---|
| **Components** | shadcn/ui — buttons, dialogs, tables, forms, command palette |
| **Design tokens** | Consistent spacing / type / colour scale — no arbitrary values |
| **Typography** | **Inter** or **Geist** |
| **Icons** | **Lucide** — one set, never mixed |
| **Motion** | **Framer Motion** — 150–250ms transitions. Restraint is what reads as professional |
| **Toasts** | **Sonner** |
| **Charts** | Recharts, themed to match |
| **States** | Every screen has designed loading / empty / error states |
| **Dark mode** | Full parity, persisted |
| **Responsive** | Mobile-first — the QR demo puts reviewers on phones |
| **Accessibility** | WCAG AA, verified with axe-core |

> What actually separates professional from amateur UI: **consistent spacing**, **restrained colour**, **real empty states**, and **nothing jumping around while loading**.

---

## 9. Real-time Architecture

| Concern | Approach |
|---|---|
| **Transport** | Spring WebSocket + **STOMP**, SockJS fallback |
| **Auth** | JWT validated in the STOMP `CONNECT` frame via `ChannelInterceptor` — an unauthenticated socket never subscribes |
| **Authorization** | Per-topic — subscribing to `/topic/course/42` requires real enrolment in course 42 |
| **Revocation** | Redis denylist checked on subscribe — a suspended user is kicked off live |
| **Reconnect** | Auto-reconnect with exponential backoff + state resync |
| **Heartbeats** | Detect dead connections, clean up presence |
| **Scale-out** | Redis pub/sub across instances (Sprint 6: Kafka) |
| **UX** | Optimistic updates so the UI never waits on a round trip |

**What goes real-time:** live arena + leaderboard, confusion alerts to instructors, skill bars moving as evidence lands, notifications, presence, viva turns.

---

## 10. Domain Model

```
User ──< LearnerSkillState >── Skill ──< SkillPrerequisite >── Skill
                                 │                    (self-referential DAG)
                                 ├──< LessonSkill >── Lesson ──< Module ──< Course
                                 ├──< Item (question)
                                 └──< RoleSkillRequirement >── CareerRole

Item ──< ItemOption ──> Misconception
Item ──< Response (user, correct, time, ability_before/after)

User ──< LearningPath ──< PathStep (skill, activity, status, rationale JSONB)
User ──< Submission ──< ProcessEvent
                    └──< VivaSession ──< VivaTurn
User ──< Evidence (skill, type, source, weight, verified_at)
User ──< LearningEvent  →  RiskScore
```

### Entities carrying the differentiation

| Entity | Key fields | Why it matters |
|---|---|---|
| `LearnerSkillState` | `ability_theta`, `mastery_probability`, `confidence`, `last_practiced_at`, `decay_rate` | The live model of a person |
| `Item` | `difficulty_b`, `discrimination_a`, `elo_rating`, `times_seen` | Self-calibrating item bank |
| `Misconception` | `skill_id`, `name`, `remediation_hint` | Diagnoses *why* wrong, not *that* wrong |
| `PathStep.rationale` | JSONB decision trace | Powers "Why this?" explainability |
| `ProcessEvent` | `type`, `payload`, `timestamp` | Evidence of real work |
| `VivaTurn` | `question`, `answer`, `evaluation`, `score` | The defence record |
| `Evidence` | `skill_id`, `type`, `source_id`, `weight`, `verified_at` | Makes the passport checkable |

---

### 10.1 Full relational schema

Conventions across every table: `id BIGSERIAL PRIMARY KEY` · `created_at TIMESTAMPTZ NOT NULL DEFAULT now()` · `updated_at TIMESTAMPTZ` · soft delete via `deleted_at TIMESTAMPTZ` where history matters · every foreign key indexed · enums stored as `VARCHAR` with `CHECK` constraints (readable in the database, and easier to migrate than PG enums).

#### A. Identity & Security *(Phase 1)*

| Table | Columns | Notes |
|---|---|---|
| `users` | `id` · `email` UNIQUE · `password_hash` · `full_name` · `avatar_url` · `status` CHECK (`active`/`pending`/`suspended`) · `email_verified_at` · `last_login_at` · `deleted_at` | Argon2id in `password_hash` |
| `roles` | `id` · `name` UNIQUE (`learner`/`instructor`/`admin`/`employer`) · `description` | |
| `user_roles` | `user_id` FK · `role_id` FK · PK(user_id, role_id) | |
| `refresh_tokens` | `id` · `user_id` FK · `token_hash` · `expires_at` · `revoked_at` · `replaced_by_id` · `device_id` FK · `ip` · `user_agent` | **Store the hash, never the token.** `replaced_by_id` enables reuse detection |
| `user_devices` | `id` · `user_id` FK · `device_label` · `user_agent` · `ip` · `last_seen_at` · `trusted` | Powers the session manager |
| `mfa_totp` | `user_id` PK FK · `secret_encrypted` · `enabled` · `confirmed_at` | Secret encrypted at rest |
| `mfa_recovery_codes` | `id` · `user_id` FK · `code_hash` · `used_at` | Single-use |
| `webauthn_credentials` | `id` · `user_id` FK · `credential_id` UNIQUE · `public_key` · `sign_count` · `transports` · `last_used_at` | Passkeys |
| `password_reset_tokens` | `id` · `user_id` FK · `token_hash` · `expires_at` · `used_at` | 15–30 min expiry |
| `email_verification_tokens` | `id` · `user_id` FK · `token_hash` · `expires_at` · `used_at` | |
| `login_attempts` | `id` · `email` · `ip` · `success` · `attempted_at` | Lockout + rate limiting. Index on (email, attempted_at) |
| `auth_audit_log` | `id` · `user_id` FK · `event_type` · `ip` · `user_agent` · `metadata` JSONB | Login, password change, MFA change, new device |

#### B. Skill Graph *(Phase 2)*

| Table | Columns | Notes |
|---|---|---|
| `skill_categories` | `id` · `name` · `slug` UNIQUE · `position` | |
| `skills` | `id` · `slug` UNIQUE · `name` · `description` · `category_id` FK · `level_band` (`foundational`/`intermediate`/`advanced`) | Graph nodes |
| `skill_prerequisites` | `id` · `skill_id` FK · `prerequisite_skill_id` FK · `strength` NUMERIC(3,2) · UNIQUE(skill_id, prerequisite_skill_id) · CHECK(skill_id <> prerequisite_skill_id) | **Graph edges. Cycle detection enforced in application code on insert** — a DB constraint can't express acyclicity |
| `learner_skill_state` | `id` · `user_id` FK · `skill_id` FK · `ability_theta` NUMERIC · `mastery_probability` NUMERIC(5,4) · `confidence` NUMERIC · `attempts_count` · `correct_count` · `last_practiced_at` · `decay_rate` · UNIQUE(user_id, skill_id) | **The live model of a learner.** Hottest write table |
| `skill_mastery_history` | `id` · `user_id` FK · `skill_id` FK · `mastery_probability` · `ability_theta` · `trigger_type` · `source_id` · `recorded_at` | Append-only — drives progress charts and decay |

#### C. Content *(Phase 3)*

| Table | Columns | Notes |
|---|---|---|
| `courses` | `id` · `title` · `slug` UNIQUE · `description` · `thumbnail_url` · `instructor_id` FK · `category_id` FK · `status` (`draft`/`published`/`archived`) · `deleted_at` | |
| `course_modules` | `id` · `course_id` FK · `title` · `position` | |
| `lessons` | `id` · `module_id` FK · `title` · `type` (`text`/`video`/`resource`) · `content` TEXT · `video_url` · `resource_url` · `duration_seconds` · `position` | |
| `lesson_skills` | `lesson_id` FK · `skill_id` FK · `weight` NUMERIC(3,2) · PK(lesson_id, skill_id) | **The join that makes content serve skills** |
| `enrollments` | `id` · `user_id` FK · `course_id` FK · `status` (`active`/`completed`/`dropped`) · `enrolled_at` · `completed_at` · UNIQUE(user_id, course_id) | |
| `lesson_progress` | `id` · `user_id` FK · `lesson_id` FK · `status` · `seconds_watched` · `completed_at` · UNIQUE(user_id, lesson_id) | |

#### D. Assessment *(Phases 3–4)*

| Table | Columns | Notes |
|---|---|---|
| `misconceptions` | `id` · `skill_id` FK · `name` · `description` · `remediation_hint` | e.g. *"believes HashMap preserves insertion order"* |
| `items` | `id` · `skill_id` FK · `stem` TEXT · `type` (`mcq`/`multi`/`numeric`) · `difficulty_b` NUMERIC · `discrimination_a` NUMERIC · `elo_rating` INT · `times_seen` · `times_correct` · `author_id` FK · `status` · `declared_difficulty` | `declared_difficulty` is the **Bayesian prior**; `difficulty_b`/`elo_rating` self-correct from responses |
| `item_options` | `id` · `item_id` FK · `text` · `is_correct` · `misconception_id` FK NULL · `position` | **Every distractor carries a misconception** — this is the diagnosis mechanism |
| `assessments` | `id` · `user_id` FK · `type` (`diagnostic`/`practice`/`checkpoint`) · `skill_id` FK NULL · `career_role_id` FK NULL · `status` · `score` · `started_at` · `completed_at` | |
| `assessment_items` | `id` · `assessment_id` FK · `item_id` FK · `position` · `served_at` | Exposure control reads this |
| `responses` | `id` · `user_id` FK · `item_id` FK · `assessment_id` FK NULL · `selected_option_id` FK · `is_correct` · `response_time_ms` · `ability_before` · `ability_after` · `misconception_id` FK NULL · `answered_at` | **Append-only.** The audit trail behind every number in the passport |

#### E. Verification *(Phase 6)* — the differentiator

| Table | Columns | Notes |
|---|---|---|
| `projects` | `id` · `title` · `description` · `instructor_id` FK · `difficulty` · `rubric` JSONB · `status` | |
| `project_skills` | `project_id` FK · `skill_id` FK · `weight` · PK(project_id, skill_id) | |
| `submissions` | `id` · `user_id` FK · `project_id` FK · `status` (`draft`/`submitted`/`in_viva`/`verified`/`unverified`) · `content` TEXT · `repo_url` · `submitted_at` | |
| `process_events` | `id` · `submission_id` FK · `event_type` (`draft_saved`/`paste`/`run`/`test`/`idle`/`edit`) · `payload` JSONB · `occurred_at` | **The process ledger.** High-volume, append-only. Index on (submission_id, occurred_at) |
| `ai_usage_declarations` | `id` · `submission_id` FK · `tool_used` · `purpose` · `extent` · `declared_at` | Declared, not banned |
| `viva_sessions` | `id` · `submission_id` FK · `status` · `overall_score` · `verdict` (`verified`/`not_verified`/`inconclusive`) · `started_at` · `completed_at` | |
| `viva_turns` | `id` · `viva_session_id` FK · `position` · `question` TEXT · `question_source` (line ref / artefact anchor) · `answer` TEXT · `evaluation` JSONB · `score` · `asked_at` · `answered_at` | The defence record |
| `instructor_reviews` | `id` · `submission_id` FK · `reviewer_id` FK · `rubric_scores` JSONB · `comments` · `decision` · `reviewed_at` | Human sign-off |
| `evidence` | `id` · `user_id` FK · `skill_id` FK · `evidence_type` (`assessment`/`project`/`viva`/`instructor`) · `source_type` · `source_id` · `weight` · `score` · `verified_by` FK NULL · `verified_at` | **Immutable. The passport is derived from this table and nothing else** |

#### F. Career & Path *(Phases 5, 7)*

| Table | Columns | Notes |
|---|---|---|
| `career_roles` | `id` · `title` · `slug` UNIQUE · `description` · `category` | e.g. Backend Java Developer |
| `role_skill_requirements` | `id` · `career_role_id` FK · `skill_id` FK · `required_mastery` NUMERIC(3,2) · `weight` NUMERIC(3,2) · UNIQUE(role, skill) | The target vector |
| `learner_career_goals` | `id` · `user_id` FK · `career_role_id` FK · `is_primary` · `set_at` | |
| `learning_paths` | `id` · `user_id` FK · `career_role_id` FK · `status` · `version` · `generated_at` | Re-planning creates a new version |
| `path_steps` | `id` · `learning_path_id` FK · `position` · `skill_id` FK · `activity_type` (`lesson`/`practice`/`assessment`/`project`) · `activity_id` · `status` · **`rationale` JSONB** · `completed_at` | **`rationale` powers "Why this?"** — written at generation time, not reconstructed later |
| `passport_snapshots` | `id` · `user_id` FK · `snapshot` JSONB · `readiness_score` · `share_token` UNIQUE NULL · `created_at` | Shareable public view via `share_token` |

#### G. Real-time & Arena *(Phase 8)*

| Table | Columns | Notes |
|---|---|---|
| `arenas` | `id` · `instructor_id` FK · `course_id` FK NULL · `title` · `status` · `join_code` UNIQUE · `started_at` · `ended_at` | `join_code` backs the QR |
| `arena_participants` | `id` · `arena_id` FK · `user_id` FK NULL · `display_name` · `score` · `joined_at` | Nullable user — guests can join by QR |
| `arena_questions` | `id` · `arena_id` FK · `item_id` FK · `position` · `time_limit_seconds` · `published_at` · `closed_at` | |
| `arena_answers` | `id` · `arena_question_id` FK · `participant_id` FK · `selected_option_id` FK · `is_correct` · `answered_at` · `points_awarded` | Speed bonus computed here |
| `confusion_signals` | `id` · `user_id` FK · `skill_id` FK · `failure_count` · `window_start` · `detected_at` · `instructor_notified_at` · `resolved_at` | Fires the live instructor alert |

> **Live leaderboard state lives in Redis sorted sets, not Postgres.** These tables are the durable record; Redis serves the real-time reads.

#### H. Analytics *(Phase 9)*

| Table | Columns | Notes |
|---|---|---|
| `learning_events` | `id` · `user_id` FK · `event_type` · `entity_type` · `entity_id` · `payload` JSONB · `occurred_at` | **Event-sourced spine.** Becomes a Kafka topic in Sprint 6. Partition by month if it grows |
| `risk_scores` | `id` · `user_id` FK · `course_id` FK NULL · `score` · `band` (`low`/`medium`/`high`) · `factors` JSONB · `computed_at` | `factors` explains *why* flagged |
| `daily_activity` | `user_id` FK · `activity_date` · `minutes_active` · `items_answered` · `lessons_completed` · PK(user_id, activity_date) | Rollup — keeps dashboards fast |

#### I. Admin & Platform *(Phase 10)*

| Table | Columns | Notes |
|---|---|---|
| `instructor_applications` | `id` · `user_id` FK · `status` (`pending`/`approved`/`rejected`) · `reviewed_by` FK · `reviewed_at` · `notes` | The approval queue |
| `admin_audit_log` | `id` · `admin_id` FK · `action` · `target_type` · `target_id` · `before` JSONB · `after` JSONB · `created_at` | **Every admin action recorded with before/after** |
| `platform_settings` | `key` PK · `value` JSONB · `updated_by` FK · `updated_at` | |
| `notifications` | `id` · `user_id` FK · `type` · `title` · `body` · `link` · `data` JSONB · `read_at` | Index on (user_id, read_at) |
| `notification_preferences` | `user_id` FK · `type` · `channel` · `enabled` · PK(user_id, type, channel) | |

---

### 10.2 Schema by role — who touches what

| Role | Writes | Reads |
|---|---|---|
| **Learner** | `responses`, `lesson_progress`, `submissions`, `process_events`, `viva_turns` (answers), `learner_career_goals`, `arena_answers`, `enrollments` | Own `learner_skill_state`, `path_steps` + rationale, `evidence`, `passport_snapshots`, published content |
| **Instructor** | `courses`→`lessons`, `items`, `item_options`, `misconceptions`, `projects`, `instructor_reviews`, `arenas`, `arena_questions` | Own courses' analytics, `confusion_signals`, `risk_scores`, cohort `daily_activity`, submission queue |
| **Admin** | `skills`, `skill_prerequisites`, `skill_categories`, `career_roles`, `role_skill_requirements`, `instructor_applications`, `users.status`, `platform_settings` | Everything, plus `admin_audit_log` and `auth_audit_log` |
| **Employer** *(stretch)* | — | `passport_snapshots` via `share_token`, and `evidence` drill-down only for passports shared with them |
| **System** | `learner_skill_state`, `skill_mastery_history`, `evidence`, `risk_scores`, `learning_events`, `confusion_signals`, `notifications` | — |

### 10.3 Critical indexes

```sql
-- hottest path: item selection + ability lookup on every answer
CREATE INDEX idx_lss_user_skill       ON learner_skill_state(user_id, skill_id);
CREATE INDEX idx_items_skill_status   ON items(skill_id, status);
CREATE INDEX idx_responses_user_item  ON responses(user_id, item_id);

-- skill graph traversal (recursive CTE both directions)
CREATE INDEX idx_prereq_skill         ON skill_prerequisites(skill_id);
CREATE INDEX idx_prereq_prereq        ON skill_prerequisites(prerequisite_skill_id);

-- process ledger + evidence
CREATE INDEX idx_process_sub_time     ON process_events(submission_id, occurred_at);
CREATE INDEX idx_evidence_user_skill  ON evidence(user_id, skill_id);

-- analytics + auth
CREATE INDEX idx_events_user_time     ON learning_events(user_id, occurred_at DESC);
CREATE INDEX idx_login_email_time     ON login_attempts(email, attempted_at DESC);
CREATE INDEX idx_notif_user_unread    ON notifications(user_id) WHERE read_at IS NULL;

-- JSONB search on the explainability trace
CREATE INDEX idx_pathstep_rationale   ON path_steps USING GIN (rationale);
```

### 10.4 Design decisions worth defending

| Decision | Reason |
|---|---|
| `responses` and `process_events` are **append-only, never updated** | They are the evidence trail. A mutable audit record is not an audit record |
| Passport is **derived from `evidence`**, never stored as a number a user can influence | This is what makes the claim verifiable rather than self-reported |
| `rationale` written at **generation time** | Reconstructing "why" later would be a plausible-sounding fiction, not a record |
| `declared_difficulty` kept **separate** from `difficulty_b` | Preserves the prior alongside the learned value — this is the cold-start story, visible in the data |
| Cycle detection in **application code**, not the database | Postgres cannot express acyclicity as a constraint; the check runs on every edge insert |
| Leaderboards in **Redis**, durable record in **Postgres** | Sorted sets are built for exactly this; Postgres would be the bottleneck under live load |
| `arena_participants.user_id` **nullable** | Reviewers scanning a QR in the demo are guests, not registered users |

---

## 11. Event Catalogue

| Event | Direction | Consumers |
|---|---|---|
| `presence.updated` | client → server → broadcast | Instructor dashboard |
| `arena.started` / `arena.question.published` | server → participants | Learner devices |
| `arena.answer.submitted` | client → server | Scoring, leaderboard |
| `arena.leaderboard.updated` | server → all | Projector view |
| `confusion.detected` | server-internal → instructor topic | Alert panel |
| `skill.mastery.updated` | server → learner topic | Live passport bars |
| `viva.turn.issued` / `viva.turn.answered` | bidirectional | Viva session |
| `notification.created` | server → user topic | Notification bell |
| `risk.flagged` | server → instructor topic | Intervention queue |

In Sprint 6 these become **Kafka topics**; the realtime service consumes and fans out to WebSocket subscribers.

---

## 12. API Surface

```
POST   /api/auth/register            POST   /api/auth/login
POST   /api/auth/refresh             POST   /api/auth/logout
POST   /api/auth/mfa/totp/enroll     POST   /api/auth/mfa/verify
POST   /api/auth/passkey/register    GET    /api/auth/sessions

GET    /api/skills                   GET    /api/skills/{id}/prerequisites
GET    /api/me/skills                GET    /api/me/passport

GET    /api/careers                  GET    /api/careers/{id}/requirements
POST   /api/me/career-goal           GET    /api/me/gap-analysis

POST   /api/diagnostics/start        POST   /api/diagnostics/{id}/answer
GET    /api/me/path                  GET    /api/me/path/steps/{id}/rationale

GET    /api/courses                  GET    /api/lessons/{id}
POST   /api/practice/next-item       POST   /api/practice/answer

POST   /api/submissions              POST   /api/submissions/{id}/process-events
POST   /api/submissions/{id}/viva    POST   /api/viva/{id}/answer

GET    /api/instructor/alerts        GET    /api/instructor/heatmap
POST   /api/arena/start              POST   /api/arena/{id}/answer

WS     /ws  →  /topic/user/{id} · /topic/course/{id} · /topic/arena/{id}
```

---

## 13. Scope — Sprint 5 vs Sprint 6

### Sprint 5 — build

**Spine (must work):** identity and roles · skill graph · content authoring · item bank · adaptive diagnostic · path generation · basic passport · REST API · React UI.

**Three showpieces (go deep):**
1. **Adaptive engine + explainability** — the intellectual core
2. **Verification: process ledger + AI viva** — the identity
3. **Live arena + confusion detection** — the real-time core

**Stub with honest labels:** employer talent search, skill decay, what-if simulator, peer matching.

> Three working pillars beat five broken ones. Deliberate scoping earns more respect in review than sprawl.

### Sprint 6 — extract

```
                    API Gateway (Spring Cloud Gateway)
                    Eureka Discovery · Config Server
                                  │
      ┌───────────┬───────────┬───┴───────┬───────────┐
      ▼           ▼           ▼           ▼           ▼
  Identity    Content     Assessment    Skill      Career
      │           │           │           │           │
      ▼           ▼           ▼           ▼           ▼
 Verification  Realtime   Analytics  Notification
      └───────────┴───────────┴───────────┘
                    Kafka event bus
            database-per-service · Resilience4j
            Prometheus + Grafana · Zipkin tracing
```

**Why each service deserves to exist** — the question that decides whether Sprint 6 is real or theatre:

| Service | Independent reason |
|---|---|
| Verification | CPU-heavy inference, bursty, needs isolation — a completely different scaling curve |
| Realtime | Stateful connections; scales on concurrent sockets, not request rate |
| Assessment | Read-heavy item selection on every answer — the hottest path in the system |
| Analytics | Batch/stream consumer, tolerates lag, must never slow the learner path |
| Identity | Security boundary; changes rarely, must be independently hardened |

**Critical Sprint 5 discipline:** strict module boundaries from day one — no cross-module table joins, communicate via interfaces and events. This is what makes Sprint 6 an *extraction* rather than a rewrite.

---

## 14. Build Order

Each phase ends in something **runnable and demonstrable**. Never move on with a phase half-finished — a broken foundation costs more later than a slipped schedule.

### Phase 0 — Configuration & Foundation

**Goal:** `docker compose up` starts Postgres and Redis; the Spring Boot app boots, connects, runs migrations, and serves Swagger. The React app loads and calls a health endpoint.

| Area | Tasks |
|---|---|
| Toolchain | Install JDK 21 (Temurin), Maven, IntelliJ IDEA. Start Docker Desktop |
| Backend | Spring Initializr project · package-by-module structure · `application.yml` profiles (local/test/prod) · Actuator health · springdoc Swagger |
| Database | `docker-compose.yml` with Postgres 16 + Redis · Flyway baseline migration `V1__init.sql` · HikariCP tuning |
| Frontend | Vite + React + TypeScript · Tailwind · shadcn/ui init · React Router shell · Axios client · TanStack Query provider |
| Repo | `git init`, `.gitignore`, `.editorconfig`, README, this document |

**Done when:** backend health endpoint green, Swagger loads, frontend renders a themed page in light and dark, Flyway has applied cleanly.

---

### Phase 1 — Identity & Security

**Goal:** a user can register, verify email, log in, refresh a token, enable MFA, and be instantly revoked.

| Area | Tasks |
|---|---|
| Schema | `users`, `roles`, `user_roles`, `refresh_tokens`, `user_devices`, `mfa_totp`, `mfa_recovery_codes`, `webauthn_credentials`, `password_reset_tokens`, `email_verification_tokens`, `auth_audit_log`, `login_attempts` |
| Backend | Argon2id encoder · JWT issue/validate · refresh rotation with reuse detection · Redis denylist · `SecurityFilterChain` · role middleware · `@PreAuthorize` · Bucket4j rate limiting · account lockout · HaveIBeenPwned check · generic auth errors · async email via queue |
| MFA | TOTP enroll/verify (QR) · recovery codes · WebAuthn/passkey register and authenticate |
| Frontend | Register / login / verify / forgot-password flows · MFA enrollment UI · protected route wrapper · Axios refresh interceptor · session & device manager screen |

**Done when:** a suspended user is ejected mid-session, a stolen refresh token is detected on reuse, and login survives a brute-force attempt.

---

### Phase 2 — Skill Graph

**Goal:** the skill DAG exists, is editable by admins, and can answer "what is this learner ready for?"

| Area | Tasks |
|---|---|
| Schema | `skill_categories`, `skills`, `skill_prerequisites`, `learner_skill_state`, `skill_mastery_history` |
| Backend | Skill CRUD · **cycle detection on edge insert** (reject anything creating a loop) · recursive CTE for ancestors/descendants · "ready-to-learn frontier" query · initial `learner_skill_state` provisioning |
| Frontend | **Admin skill-graph editor** — visual node/edge canvas, create skills, draw prerequisites, cycle errors surfaced inline |

**Done when:** a cycle is rejected with a clear error, and the frontier query returns correct results in one round trip.

---

### Phase 3 — Content & Item Bank

**Goal:** instructors can author courses and questions; every item is tagged to a skill and every wrong option to a misconception.

| Area | Tasks |
|---|---|
| Schema | `courses`, `course_modules`, `lessons`, `lesson_skills`, `enrollments`, `lesson_progress`, `items`, `item_options`, `misconceptions` |
| Backend | Course/module/lesson CRUD with ownership policies · publish lifecycle · item bank CRUD · skill tagging · misconception library · instructor-declared difficulty prior |
| Frontend | Course builder · lesson editor · **item authoring with misconception tagging per distractor** · item bank browser with filters |
| Data | **Claude-generated seed content** — skill graph, items, distractors, misconceptions, courses — shipped as Flyway seed migrations |

**Done when:** the seeded item bank covers the demo skill tree, and every distractor carries a misconception.

---

### Phase 4 — Adaptive Engine ⭐

**Goal:** the system estimates ability and mastery from responses, and picks the most informative next question.

| Area | Tasks |
|---|---|
| Schema | `assessments`, `assessment_items`, `responses` |
| Backend | **Elo** update (learner ability ↔ item difficulty, both move) · **IRT 2PL** probability and information function · **BKT** mastery update · item selection by **maximum information gain** · exposure control so the same items don't repeat · termination rule (confidence threshold or item cap) · Bayesian prior seeding for uncalibrated items |
| Frontend | Diagnostic flow · practice flow · immediate feedback · **misconception callout on wrong answers** |
| Tests | Heaviest unit-test coverage in the project — a wrong Elo update is invisible and poisons everything downstream |

**Done when:** two learners answering differently end with visibly different, correct skill profiles, and item difficulties drift toward reality as responses accumulate.

---

### Phase 5 — Career, Path & Explainability ⭐

**Goal:** pick a career goal, get a personalised path, and click "Why this?" on any step.

| Area | Tasks |
|---|---|
| Schema | `career_roles`, `role_skill_requirements`, `learner_career_goals`, `learning_paths`, `path_steps` |
| Backend | Career catalogue · gap analysis (profile vs requirements) · **path generation** — topological walk of the DAG filtered by gaps, ordered by prerequisite depth and role weight · **rationale capture into JSONB at generation time** · re-planning when mastery changes |
| Frontend | Career goal picker · path timeline · **"Why this?" panel rendering the decision trace** · gap visualisation (radar chart) |

**Done when:** the reasoning panel shows real stored data — prerequisites satisfied, diagnostic evidence, role weight, failed items — not a generated sentence.

---

### Phase 6 — Verification: Process Ledger & AI Viva ⭐⭐

**Goal:** the signature feature. Submit work, have the journey recorded, then defend it live.

| Area | Tasks |
|---|---|
| Schema | `projects`, `project_skills`, `submissions`, `process_events`, `ai_usage_declarations`, `viva_sessions`, `viva_turns`, `instructor_reviews`, `evidence` |
| Backend | Project definitions with rubrics · submission flow · **process event ingestion** (draft saves, paste events, runs, time-on-task) · **Ollama integration** generating viva questions *from the submission* · adaptive follow-up logic · answer evaluation and scoring · verdict rules · evidence record creation |
| Frontend | Project workspace with autosave (feeding the ledger) · AI-use declaration form · **viva chat interface** — one question at a time, timed, no going back · process-ledger timeline viewer · instructor review queue |

**Done when:** an AI-pasted submission is *accepted* but fails its viva, and the screen reads **"Submission accepted. Understanding not verified."**

---

### Phase 7 — Skill Passport

**Goal:** a passport where every percentage is clickable and backed by evidence.

| Area | Tasks |
|---|---|
| Schema | `passport_snapshots` |
| Backend | Evidence aggregation per skill · weighted readiness score against a role · snapshot generation · public shareable passport endpoint (token-based, no auth) |
| Frontend | Passport page — skill bars, radar, readiness gauge · **evidence drill-down per skill** · shareable public view |

**Done when:** clicking any skill shows the assessments, projects and viva results that produced that number.

---

### Phase 8 — Real-time ⭐

**Goal:** the live demo moments work — arena, alerts, live bars.

| Area | Tasks |
|---|---|
| Schema | `arenas`, `arena_participants`, `arena_questions`, `arena_answers`, `confusion_signals` |
| Backend | STOMP config · **JWT auth in `CONNECT` frame** via `ChannelInterceptor` · per-topic authorization · Redis denylist check on subscribe · arena lifecycle · scoring with speed bonus · **Redis sorted-set leaderboard** · **confusion detection** (N related failures in a window → instructor event) · live skill-update broadcast |
| Frontend | STOMP client with auto-reconnect + backoff · **QR join screen** · arena player (mobile-first) · projector leaderboard view · instructor arena control · **live alert panel** · live-updating passport bars |

**Done when:** phones join by QR, the leaderboard animates for everyone simultaneously, and an instructor alert fires without a refresh.

---

### Phase 9 — Analytics & Early Intervention

**Goal:** instructors see where the class is struggling and who is about to quit.

| Area | Tasks |
|---|---|
| Schema | `learning_events`, `risk_scores`, `daily_activity` |
| Backend | Event emission across modules · scheduled at-risk scoring (engagement slope, performance trend, failure clustering, inactivity) · **week-1-and-2 weighting** · item analysis (p-value, discrimination) · cohort aggregation |
| Frontend | Instructor dashboard — cohort heatmap, funnel, item analysis · at-risk queue with reasons · learner progress view · **what-if simulator** |

**Done when:** the at-risk list explains *why* each learner is flagged.

---

### Phase 10 — Admin, Polish & Demo

| Area | Tasks |
|---|---|
| Schema | `instructor_applications`, `admin_audit_log`, `platform_settings`, `notifications`, `notification_preferences` |
| Admin | Instructor approval queue · user management (suspend/reactivate/soft-delete) · career role management · platform dashboard · audit log viewer |
| Polish | Dark mode parity · skeleton states everywhere · empty and error states · accessibility pass (axe-core) · responsive pass · toasts |
| Demo | Rehearsed seed dataset · two-learner divergence scenario · pre-staged AI submission · Cloudflare Tunnel for phone access · **full timed rehearsal** |

**Done when:** the eight-minute demo runs start to finish without a stumble, twice.

---

### Dependency order

```
Phase 0  Foundation
   ↓
Phase 1  Identity ──────────────┐
   ↓                            │
Phase 2  Skill Graph            │
   ↓                            │
Phase 3  Content + Item Bank    │
   ↓                            │
Phase 4  Adaptive Engine ⭐     │
   ↓          ↓                 │
Phase 5      Phase 6            │  Phase 8 Real-time
Path +       Verification ⭐⭐   │  (needs 1, 2, 4)
Explain ⭐        ↓             │
   └──────→ Phase 7 Passport ←──┘
                  ↓
            Phase 9 Analytics
                  ↓
            Phase 10 Admin + Demo
```

**If time runs short:** phases 4, 5, 6 and 8 are the project. Phase 9 can shrink to a single dashboard, and phase 7 to a static passport. Never cut 6 — it is the identity of the product.

---

## 15. Testing Strategy

| Layer | Tool | Focus |
|---|---|---|
| Unit | JUnit 5 + Mockito | **The psychometric engine above all** — a wrong Elo update is invisible but poisons everything downstream |
| Repository | `@DataJpaTest` | Queries, especially recursive CTEs |
| Controller | `@WebMvcTest` | Request validation, auth rules |
| Integration | **Testcontainers** | Real Postgres and Redis — not H2 pretending |
| WebSocket | Spring test support | Auth on CONNECT, per-topic authorization |
| End-to-end | Playwright | The rehearsed demo path |
| Accessibility | axe-core | WCAG AA |
| Security | OWASP dependency-check | Known CVEs in CI |

---

## 16. The Demo

> A demo rewards **visible causality and audience participation**, not architectural elegance. Nobody in the room can see Kafka. They can see thirty phones on a leaderboard.

| Time | Beat |
|---|---|
| 0:00 | *"Everyone take out your phone."* QR → reviewers join a live arena, leaderboard on the projector |
| 1:30 | Two learners side by side take the diagnostic, **answer differently, get visibly different paths** |
| 3:00 | Click **"Why this?"** → the reasoning chain appears |
| 4:00 | Wrong answer → system names the **specific misconception**, not the topic |
| 5:00 | Third related failure → **instructor dashboard alerts live** on the second screen |
| 6:00 | Project submitted → process ledger shown → **skill bar moves 45% → 63%** |
| 7:00 | **Passport + readiness** update; employer view finds the candidate |
| 7:45 | One slide: the Sprint 6 service map |

Story arc: **diagnose → personalise → explain → intervene → verify → employ.**

### The showstopper

1. A reviewer submits code **written by AI, deliberately**
2. The system accepts it, then asks about *that specific code*
3. The reviewer cannot answer the follow-ups
4. Screen reads: **"Submission accepted. Understanding not verified."**
5. A real learner then defends theirs, and their skill bar moves with evidence attached

Ninety seconds, solving a problem every person in that room is personally dealing with.

---

## 17. Credibility Rules

**1. Every number must have a formula.**
The first thing a sharp reviewer asks is *"how is 76% computed?"* If the answer is "we averaged some quiz scores," the premise collapses in one question. With Elo/IRT/BKT, "91%" means *P(mastery) = 0.91 given this response history*, and the formula can be shown on screen. **This converts the weakest point into the strongest.**

**2. Cold start has a stated answer.**
Adaptive systems need calibrated content; with no learner data the engine is guessing. Ours seeds instructor-declared difficulty as a Bayesian prior and lets Elo correct it as real responses arrive. Naming your own cold-start problem before the reviewer does reads as maturity.

---

## 18. Constraints

| Constraint | Detail |
|---|---|
| **Budget** | **Zero.** Claude Pro only. No paid APIs, hosting or managed services |
| **Claude Pro ≠ API credits** | Claude Code sessions are included; calling the Claude API from the app bills separately. **Never architect around paid inference** |
| **Machine** | Windows 11. Docker installed. Node v24 present. **JDK/Maven not yet installed — the current blocker** |
| **Scarce resources** | Claude Pro usage limits and available hours — not money |

**Working practices:** commit constantly · keep this document current · batch generative work (question banks, seed data) into dedicated sessions · don't rebuild what already works.

---

## 19. Decision Log

| # | Decision | Rationale |
|---|---|---|
| 1 | `Skill` is first-class, not `Course` | The single choice that separates this from an LMS and gives Sprint 6 real service boundaries |
| 2 | Position as **verifiable learning**, not personalised learning | Personalisation is crowded; verification is an open, documented gap |
| 3 | AI Viva is the signature feature | Oral defence is the accepted answer to AI cheating and doesn't scale — software fixes that |
| 4 | Record process, don't detect AI | Detection was tried industry-wide and abandoned; evidence-of-work is a stronger premise |
| 5 | Real psychometrics (Elo/IRT/BKT) | Makes every number defensible under questioning |
| 6 | Explainability on every recommendation | Cheap to build, kills the black-box objection, aligns with UNESCO human-agency framing |
| 7 | Pre-generate AI content as seed data | Free, faster, higher quality, and cannot fail live |
| 8 | Ollama locally for viva | Zero cost, offline, strong privacy answer |
| 9 | Modular monolith first, strict boundaries | Makes Sprint 6 an extraction, not a rewrite |
| 10 | Spine + three showpieces | Three working pillars beat five broken ones |
| 11 | shadcn/ui | Biggest single lever from "student project" to "product" |
| 12 | Passkeys + TOTP, httpOnly cookies | Strongest available auth, entirely free |
| 13 | Cut video/WebRTC | The only features that genuinely cost money, and never differentiators |

---

## 20. Sources

- [Widespread AI misuse forces higher education to rethink assessment — Phys.org](https://phys.org/news/2026-05-widespread-ai-misuse-higher-rethink.html)
- [AI Detectors Are Out, New Assessments Are In — Inside Higher Ed](https://www.insidehighered.com/news/tech-innovation/artificial-intelligence/2026/08/05/ai-detectors-are-out-new-approaches-are)
- [Designing AI-resilient assessment: a four-pillar framework — Frontiers](https://www.frontiersin.org/journals/artificial-intelligence/articles/10.3389/frai.2026.1841682/full)
- [Beyond Detection: Redesigning Authentic Assessment in an AI-Mediated World — MDPI](https://www.mdpi.com/2227-7102/15/11/1537)
- [The Conversational Exam: A Scalable Assessment Design for the AI Era — arXiv](https://arxiv.org/pdf/2601.10691)
- [Why Only 46% Of Employers Plan To Expand Skills-Based Hiring In 2026 — Forbes](https://www.forbes.com/sites/carolinecastrillon/2025/12/15/why-only-46-of-employers-plan-to-expand-skills-based-hiring-in-2026/)
- [Skill-based hiring in 2026 — Sertifier](https://sertifier.com/blog/skill-based-hiring-2026/)
- [Online Course Completion Statistics 2026 — Skillademia](https://www.skillademia.com/statistics/online-course-completion-statistics/)
- [2026 EdTech Trends: Navigating the Efficacy Reckoning — OpenFieldX](https://openfieldx.com/edtech-trends-2026/)
