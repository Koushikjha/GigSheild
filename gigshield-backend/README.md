# GigShield Backend — a deep technical walkthrough

This is the Java service at the center of GigShield: a parametric
micro-insurance platform for gig delivery workers. It owns authentication,
policy pricing and purchase, the entire event-driven claims-automation
pipeline (disruption event → ML verification → fraud check → payout), and
the admin/worker dashboards.

This document is deliberately long. The goal isn't just to say *what* each
piece does — the code comments already do that — but *why it's built this
way*: why Spring Boot instead of something lighter, why three different
databases instead of one, why Kafka instead of a direct HTTP call, why the
security model looks the way it does, and where the real engineering
trade-offs and rough edges are. If you're new to this codebase, reading
this top to bottom should leave you able to reason about any change you
need to make, not just copy a pattern you saw somewhere else.

For the product-level picture (what GigShield does, the ML sidecar, the
frontend, the bugs found and fixed from real-world testing, and the full
test evidence) see the root [`README.md`](../README.md). This document is
backend-only, and goes one level deeper on the Java side specifically.

---

## Table of contents

1. [Why Spring Boot / Java at all](#1-why-spring-boot--java-at-all)
2. [How the code is organized, and why](#2-how-the-code-is-organized-and-why)
3. [Three databases, on purpose](#3-three-databases-on-purpose)
4. [Authentication: phone + OTP, not passwords](#4-authentication-phone--otp-not-passwords)
5. [Security architecture](#5-security-architecture)
6. [The event-driven pipeline: why Kafka](#6-the-event-driven-pipeline-why-kafka)
7. [Talking to the ML sidecar](#7-talking-to-the-ml-sidecar)
8. [The claims lifecycle, end to end](#8-the-claims-lifecycle-end-to-end)
9. [Fraud detection and the strike/ban mechanic](#9-fraud-detection-and-the-strikeban-mechanic)
10. [Risk-based pricing](#10-risk-based-pricing)
11. [Payments: collection and payout](#11-payments-collection-and-payout)
12. [Cross-cutting design decisions](#12-cross-cutting-design-decisions)
13. [Configuration and profiles](#13-configuration-and-profiles)
14. [Testing philosophy](#14-testing-philosophy)
15. [The Docker image](#15-the-docker-image)
16. [API surface, with auth requirements](#16-api-surface-with-auth-requirements)
17. [Known rough edges](#17-known-rough-edges)

---

## 1. Why Spring Boot / Java at all

This is a system that moves real (test-mode, but structurally real) money
based on automated decisions — a disruption event gets verified, a fraud
score gets computed, a payout gets requested, all without a human in the
loop for the common case. That combination — financial correctness,
long-lived transactional state, and an event-driven pipeline with several
moving asynchronous parts — is exactly the profile where a mature,
batteries-included framework earns its weight, over something leaner like
Express or a hand-rolled Python service:

- **Strong typing catches a whole class of bugs at compile time.** A
  `ClaimStatus` enum with six values and a `EventType` enum with six more
  mean the compiler rejects an invalid state transition before it ever
  reaches a test, let alone production. In a system where "the payout
  amount got attached to the wrong claim" is a real-money bug, that
  matters more than developer velocity on day one.
- **Spring's dependency injection + `@Transactional` removes an entire
  category of hand-written plumbing.** Every service in this codebase
  (`ClaimService`, `PolicyService`, `FraudService`, `RiskService`, ...) is
  a plain class with `@RequiredArgsConstructor`-generated constructor
  injection and method-level `@Transactional` boundaries. Nobody here is
  manually managing a connection pool, hand-rolling commit/rollback logic,
  or wiring dependencies by hand — Spring does it, consistently, everywhere.
- **Spring Data (JPA + MongoDB + Redis) gives three different persistence
  models a single, consistent programming style** — a repository interface
  with derived query methods (`findByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(...)`
  compiles into a real query without a line of SQL/Mongo query language
  written by hand). See §3 for why three databases are used at all.
- **Spring Kafka + Spring Security are both first-class, well-documented,
  heavily used in production elsewhere.** This isn't a system that needs
  bleeding-edge tooling; it needs boring, reliable, well-understood
  building blocks, because the interesting part of this project is the
  *business logic* (parametric triggers, fraud gating, risk pricing), not
  the plumbing.
- **The JVM's maturity around observability and long-running-process
  stability** matters for a service that's meant to stay up and keep
  consuming Kafka messages reliably, not restart on every deploy.

None of this means Node.js or a Python service *couldn't* do this job —
plenty of production fintech runs on both. It means that for *this*
project, at *this* level of transactional/event-driven complexity, Spring
Boot's opinions match the problem well, and the cost (verbosity, a build
step, a JVM to run) buys real correctness guarantees back.

---

## 2. How the code is organized, and why

The package structure is **package-by-feature**, not package-by-layer:

```
com.gigshield.
├── auth/          (dto, entity, filter, repository, service, sms, util)
├── claim/          (controller, document, dto, enums, kafka, repository, service)
├── config/         (AppConstants + every @Configuration class)
├── dashboard/       (controller, dto, service)
├── event/            (controller, dto, entity, enums, kafka, repository, scheduler, service)
├── fraud/             (dto, entity, repository, service)
├── integration/        (MlServiceClient + its DTOs — the ML sidecar boundary)
├── kafka/                (KafkaTopics, KafkaConsumerConfig, KafkaProducerConfig, KafkaTopicConfig)
├── payment/               (controller, dto, entity, enums, kafka, repository, service)
├── policy/                 (controller, dto, entity, enums, repository, scheduler, service)
├── risk/                     (controller, dto, entity, repository, service)
└── user/                      (controller, dto, entity, enums, mapper, repository, service)
```

Compare that to package-by-layer (`controllers/`, `services/`,
`repositories/`, `dtos/` as the top-level split) — the more common
default, and the one a lot of Spring tutorials teach. Package-by-feature
was chosen instead because:

- **A change to "how claims work" touches one directory, not five.** When
  this session added the `ORDER_CANCELLED` claim type, worker-reported
  claims, and time-aware verification, every file that needed to change
  lived under `claim/` or one hop away (`event/enums/EventType.java`,
  `integration/MlServiceClient.java`). Package-by-layer would have spread
  that same change across `controllers/`, `services/`, `dtos/`,
  `repositories/` with no folder-level signal connecting them.
- **It maps directly to how the product is described.** "Claims",
  "policies", "fraud", "risk" are real domain concepts a product person
  would use in a sentence — the code structure mirrors the domain, not an
  implementation detail (MVC layering) that's invisible to anyone
  reasoning about the business.
- **It scales better as the codebase grows.** A `services/` folder with
  eleven unrelated service classes in it (as this app would have under
  package-by-layer) tells you nothing at a glance; `claim/service/` next
  to `claim/repository/` next to `claim/kafka/` tells you everything about
  what claim-processing depends on, immediately.

The one deliberate exception is `com.gigshield.kafka` and
`com.gigshield.config` — infrastructure that's genuinely cross-cutting
(topic names, consumer/producer factories, security config, Jackson
config) doesn't belong to any one feature, so it gets its own
infrastructure-level package rather than being awkwardly homed under
whichever feature happened to need it first.

Within each feature package, the *sub*-package split — `controller/`,
`service/`, `repository/`, `dto/`, `entity/` (or `document/` for MongoDB)
— **is** layered, and that's intentional too: it's a much smaller, much
more local layering than a project-wide one, and it keeps the
well-understood MVC-ish separation of "HTTP concern" vs "business logic"
vs "persistence" vs "wire format" without losing the feature-level
grouping that makes the codebase navigable.

---

## 3. Three databases, on purpose

GigShield runs MySQL, MongoDB, *and* Redis simultaneously. This is
**polyglot persistence** — using each database for what it's actually
good at, rather than forcing every kind of data through one engine because
it's simpler to operate one database. Concretely:

### MySQL (relational, ACID) — `User`, `Policy`, `RiskProfile`, `PaymentRecord`, `FraudRecord`, `OtpRecord`, `RefreshToken`

These entities have a **fixed, well-understood shape**, and several of
them have **real relational integrity requirements**: a `Policy` belongs
to exactly one `User`; a `PaymentRecord` references a `claimId`/`policyId`
and must never be double-inserted for the same claim (`idx_payment_claim`
+ the `findByClaimId(...)` idempotency check in `PaymentService`); a
`RiskProfile` has exactly one row per user
(`idx_risk_user`, unique — see §17 for the real bug this constraint
surfaced and how it was fixed). This is precisely the shape relational
databases are built for: normalized rows, foreign-key-shaped
relationships, and constraints the database itself enforces rather than
the application hoping to get right every time. Money-adjacent state
(`PaymentRecord`, `Policy.premiumPaid`) especially benefits from ACID
transactions and a schema the database refuses to violate.

### MongoDB (document, flexible schema) — `Claim`, `DisruptionEvent`

Claims are the opposite shape: the same `Claim` document type covers six
very different `EventType`s (`RAIN`, `AQI`, `CURFEW`, `TRAFFIC`, `WAR`,
`ORDER_CANCELLED`), each with its own payout-cap logic and — for the
newest type — an `eventOccurredAt` field that only matters for one of the
six types. New event types have already been added twice in this
project's history, each time changing what a "claim" conceptually needs
to carry. A relational schema would mean a migration (and probably a
nullable-column pile-up) every time; a document store lets the shape
evolve claim-by-claim without a schema migration blocking a feature. Claims
are also fundamentally **append-heavy, read-by-ID or read-by-user**
records — exactly the access pattern MongoDB is efficient at, and exactly
the pattern that doesn't benefit much from relational joins (a claim is
read on its own, occasionally joined conceptually to a policy, but never
queried via a complex multi-table join in this codebase).

### Redis (in-memory, ephemeral, fast) — risk-score cache, ban keys, session data

Three genuinely different uses, all sharing one property: **none of this
data needs to survive a restart, and all of it needs to be read on nearly
every request.**

- **Risk-score cache** (`risk:score:<phone>`, `RiskService`,
  `RISK_SCORE_CACHE_TTL_SEC` = 1 hour): avoids calling the ML sidecar on
  every policy-pricing page load. A cache miss costs an HTTP round-trip to
  Python; a cache hit costs a Redis GET. This is the textbook cache use
  case — see §17 for why this cache *not* being invalidated on a profile
  update was a real bug.
- **Ban keys** (`ban:<phone>`, set by `UserService.recordFraudStrike`,
  checked by `JwtFilter.isBanned`): this is deliberately checked on
  **every authenticated request**, before the JWT itself is even fully
  validated, so a banned user is rejected at the edge with an O(1) Redis
  lookup rather than a database round-trip. Doing this against MySQL on
  every request would work, but would put a `SELECT` in the hot path of
  literally every API call for no benefit — Redis is built exactly for
  this.
- **Trigger-check cache** (`TRIGGER_CACHE_TTL_SEC` = 15 min, referenced in
  `AppConstants` for the scheduled city-wide polling path) — same
  reasoning as the risk-score cache, shorter TTL because weather changes
  faster than "how risky is this city on average."

The trade-off this buys: three moving parts to operate instead of one,
and the classic dual-write problem (an entity's canonical state lives in
MySQL/Mongo, and Redis is *only* ever a cache or ephemeral key — never the
system of record for anything). That discipline is maintained
consistently: every Redis key in this codebase is either a cache
(evictable, rebuildable from the source of truth) or a TTL-bound flag
(the ban key), never the only copy of something that matters.

---

## 4. Authentication: phone + OTP, not passwords

Look at `User.java` and you'll find `getPassword()` returns `null`, and
the entity itself implements `UserDetails` directly — there's no separate
`UserPrincipal` wrapper class. Why:

- **The target user is a delivery worker on a shared or budget phone,
  signing up in the field.** A password they have to remember (and that
  this system would have to store securely, rate-limit against brute
  force, and support "forgot password" flows for) is friction and risk
  for a persona where "I have my phone, and that's how I already log into
  everything" is the natural mental model. Phone + OTP matches how this
  exact demographic already authenticates into food-delivery and payment
  apps.
- **It removes an entire risk surface.** No password hash to protect, no
  password-reuse risk, no "weak password" problem, no password-reset email
  flow to secure. What replaces it is `OtpService`: a 6-digit OTP
  (`SecureRandom`, not `Math.random()` — cryptographically unpredictable),
  a configurable expiry (`otp.expiry-minutes`, default 5), and a
  **brute-force guard** — `maxAttempts` (default 3) wrong tries and the
  OTP is invalidated outright, not just left to keep being guessable.
  Every previous *unverified* OTP for a phone number is explicitly
  invalidated the moment a new one is requested
  (`otpRepository.invalidateAllForPhone(phone)`), so a stale OTP from ten
  minutes ago can't be replayed alongside a fresh one.
- **`SmsClient` is a clean seam for a real SMS provider.** In dev/mock
  mode (`gigshield.sms.mock: true`, the Docker Compose default) it just
  logs the OTP — genuinely useful for local development and this
  project's testing, since there's no real phone number needed to
  exercise the whole auth flow. In "production" mode it throws loudly if
  `provider-url`/`api-key` aren't configured rather than silently no-op'ing
  — a fail-loud choice, not fail-silent, because a broken OTP send should
  never *look* like it worked.

### `AuthService`, end to end

1. `POST /api/v1/auth/send-otp` → `OtpService.generateAndSend` → OTP
   stored (hashed? no — plain, but single-use, short-TTL, and rate-limited
   by attempt count; see §17 for a note on this) + sent via `SmsClient`.
2. `POST /api/v1/auth/verify-otp` → `OtpService.verify` (throws on
   wrong/expired/exhausted) → `UserService.findOrCreate` (existing user =
   login; brand-new phone number + registration fields present in the
   request = registration, in the same call — there's no separate "sign
   up" endpoint) → checks `user.isAccountNonLocked()` (banned users are
   rejected even with a correct OTP) → issues a JWT access token + a
   persisted refresh token.
3. `POST /api/v1/auth/refresh` exchanges a stored, non-revoked,
   non-expired `RefreshToken` for a fresh token pair. Refresh tokens are
   **persisted in MySQL, not JWTs themselves** — a deliberate choice, since
   it means a refresh token can actually be revoked (`revokeAllByUsername`,
   called on logout) instead of just expiring on its own schedule. A
   self-contained JWT refresh token can't be revoked without an external
   blocklist; this sidesteps that by not making the refresh token
   self-contained at all — it's just an opaque `UUID` looked up in a table.

---

## 5. Security architecture

`SecurityConfig` sets the whole model in one place:

```java
.sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
```

**Fully stateless.** No `HttpSession`, no server-side session store for
auth state (Redis is used for the *ban* key and the risk-score cache, not
for session/auth state — despite `spring-session-data-redis` being a
dependency; see §17). Every request carries its own JWT, validated fresh
each time by `JwtFilter`. This is the standard, correct choice for a
system meant to run multiple backend replicas behind a load balancer —
stateless auth means any replica can handle any request, with nothing to
stick a user to a specific instance.

**Route-level authorization is declared centrally, not scattered across
`@PreAuthorize` annotations on individual controller methods** (though
`@EnableMethodSecurity` is on, so that option exists too if needed):

```java
.requestMatchers(HttpMethod.POST, "/api/v1/auth/send-otp", "/api/v1/auth/verify-otp", "/api/v1/auth/refresh").permitAll()
.requestMatchers(HttpMethod.POST, "/api/v1/payments/webhook").permitAll()
.requestMatchers("/actuator/health").permitAll()
.requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
.requestMatchers(HttpMethod.POST, "/api/v1/events").hasRole("ADMIN")
...
.anyRequest().authenticated()
```

Three deliberate public exceptions, each for a different reason: the auth
endpoints obviously can't require auth to *get* auth; the Razorpay webhook
is called by Razorpay's own servers, not an authenticated user, and is
secured separately by HMAC signature verification
(`PaymentService.verifyWebhookSignature`) rather than a JWT; and
`/actuator/health` has to be reachable **unauthenticated** because it's
what Docker Compose's `HEALTHCHECK` directive polls to decide whether the
container is up — a health check that itself requires a JWT would be
unusable by the orchestrator checking it.

`hasRole("ADMIN")` works because `UserRole` enum values are literally
named `ROLE_WORKER` / `ROLE_ADMIN` — Spring Security's `hasRole(X)` is
sugar for "does this principal have the authority `ROLE_X`", so the
enum's naming convention and the authorization check have to agree, and
`User.getAuthorities()` returning
`List.of(new SimpleGrantedAuthority(role.name()))` is where that
connection is made.

### `JwtFilter` — the request-scoped bouncer

Runs once per request (`OncePerRequestFilter`), before Spring Security's
own authentication machinery gets a chance to run
(`addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)`).
Three things happen here, in order, and the order matters:

1. **No `Authorization` header, or it's not a Bearer token?** Pass through
   unauthenticated — `anyRequest().authenticated()` in `SecurityConfig`
   will reject it downstream if the endpoint actually needs auth. This
   filter doesn't reject requests itself for missing auth; it just doesn't
   *grant* it.
2. **Ban check — before the JWT is even validated for real.** A single
   `redisTemplate.hasKey("ban:" + phone)` lookup. If the key exists, the
   request is rejected immediately with 403, and — critically — the filter
   chain does **not** continue (`return;`, not `chain.doFilter(...)`).
   This is what makes a ban actually *effective* the instant it's issued:
   a banned worker's still-valid, still-unexpired JWT stops working on
   their very next request, without needing to wait for token expiry or
   revoke every outstanding token individually.
3. **Redis failure handling is fail-open, deliberately, with a loud log.**
   ```java
   } catch (Exception e) {
       log.error("Redis ban-check failed for user {}. Failing open: {}", phone, e.getMessage());
       return false;
   }
   ```
   If Redis itself is unreachable, the ban check can't be performed — and
   rather than treating "I can't tell if this user is banned" as "assume
   banned" (which would lock out the entire user base the moment Redis
   has a blip), it assumes *not* banned and lets the request through, with
   a loud `log.error` so this is visible to whoever's operating the
   system. This is a genuine, defensible trade-off — a temporary window
   where a banned user *could* slip through during a Redis outage, traded
   against never taking down the whole product because of a cache outage.
   It's the same fail-safe philosophy that shows up in the ML integration
   (§7) and the config layering (§13): a dependency being down degrades
   the system's *strictness*, never its *availability*.

### CORS

`corsConfigurationSource()` allows all origins (`setAllowedOriginPatterns(List.of("*"))`)
with credentials enabled. This is a dev/demo-appropriate default — worth
tightening to an explicit origin allowlist before this is ever exposed
publicly with real user data behind it; see §17.

### `PasswordEncoderConfig` exists, but nothing in this codebase calls it

A `BCryptPasswordEncoder(12)` bean is defined and available for injection,
but since there's no password anywhere in the current auth flow, nothing
currently uses it. It's left in place as a ready seam for a future
credential type (an admin console with real password login, for
instance) rather than removed — cheap to keep, and removing it would mean
re-adding it correctly (strength factor, bean wiring) whenever it's
actually needed.

---

## 6. The event-driven pipeline: why Kafka

The single most consequential architectural decision in this backend is
routing disruption events and payouts through Kafka instead of handling
them with direct, synchronous service calls. Concretely, here's what a
**non**-Kafka version of this system would look like: `EventService`
creates a `DisruptionEvent`, then, in the same HTTP request, loops over
every active policy in that city, calls the ML sidecar once per policy (or
once total and hopes the result stays valid), creates a `Claim` for each
one, and calls `PaymentService` directly for every auto-approved claim —
all inside the request that created the event.

That falls over in a few concrete ways this design avoids:

- **Fan-out doesn't block the request that triggered it.** One `RAIN`
  event over Delhi might affect hundreds of active policies. Iterating
  that synchronously inside an HTTP request (or the scheduled poll that
  creates the event) means the caller waits on however long it takes to
  verify, fraud-check, and create claims for every single one — and if it
  times out or the process crashes partway through, some workers get
  claims and others silently don't, with no record of where it stopped.
  Publishing one message to `gigshield.events.disruption` and letting
  `DisruptionEventListener` consume it asynchronously decouples "an event
  was recorded" from "every affected claim was processed."
- **The claims-automation and payment-automation stages are independently
  scalable and independently retryable.** `DisruptionEventListener`
  (group `gigshield-claims-automation`) and `PayoutRequestListener` (group
  `gigshield-payments`) are separate consumer groups on separate topics —
  either can be scaled to more container replicas without touching the
  other, and either can fail and retry without taking the other down.
- **`gigshield.payments.payout-completed` has two independent consumers on
  purpose** — `ClaimStatusSyncListener` (updates the claim to `PAID`) and
  `PayoutAuditListener` (an audit/notification stand-in). This is the
  actual value of pub/sub over a queue: both consumers get *every* event,
  neither competes with the other for it, and a third consumer (a real SMS
  notification service, a ledger export) can be added later without
  touching either existing one or the code that published the event in
  the first place. `PayoutAuditListener`'s own doc comment says exactly
  this — it's explicitly a stand-in for services that don't exist yet.
- **Kafka gives durability and replay that an in-memory event bus
  wouldn't.** If the claims-automation consumer is down for five minutes
  (a deploy, a crash-restart), events published during that window aren't
  lost — they sit on the topic and get processed the moment the consumer
  comes back, because Kafka is a durable log, not a fire-and-forget
  pub/sub.

### The reliability details that make this actually safe, not just "using Kafka"

Using Kafka doesn't automatically give you correctness — a lot of the real
engineering here is in `KafkaProducerConfig` and `KafkaConsumerConfig`:

**Producer side** (`KafkaProducerConfig`):
```java
config.put(ProducerConfig.ACKS_CONFIG, "all");
config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
config.put(ProducerConfig.RETRIES_CONFIG, 5);
```
`acks=all` means a publish isn't considered successful until every
in-sync replica has the message — the strongest durability guarantee
Kafka offers, appropriate here because a lost `payout-requested` message
means a worker who earned a payout silently never gets it.
`enable.idempotence=true` plus retries means a retried publish (because
of a transient broker hiccup) can't accidentally create a *duplicate*
message on the topic — the producer and broker cooperate to detect and
drop the duplicate, so "retry on failure" doesn't turn into "double-pay
a worker on a network blip."

**Consumer side** (`KafkaConsumerConfig`):
```java
config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
```
Auto-commit is off, and the ack mode is `RECORD` — the consumer offset
only advances after a message is *actually processed*, not on a timer.
If the consumer crashes mid-processing, the message is redelivered on
restart rather than silently skipped. This is also exactly why the
idempotency guards documented in the root README (§8, Scenario 4) exist
at the consuming end: **at-least-once delivery is a guarantee that a
message won't be lost, not a guarantee it won't be delivered twice** — the
correctness burden shifts to the consumer, and `PaymentService.initiateClaimPayout`'s
"does a `PaymentRecord` already exist for this claim?" check, and
`ClaimStatusSyncListener`'s "is this claim already terminal?" check, are
exactly that burden being paid.

**Failed messages don't block the partition or vanish.**
```java
ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
backOff.setMaxElapsedTime(15_000L);
DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
    (record, ex) -> new TopicPartition(record.topic() + ".DLT", record.partition()));
```
A message that fails processing is retried three times with exponential
backoff (1s, 2s, 4s — capped at 15s total), and if it still fails, it's
routed to a `<topic>.DLT` dead-letter topic instead of being dropped or
endlessly blocking that partition (which would also block every message
behind it, since Kafka only guarantees ordering *within* a partition and
a stuck message would starve everything queued after it). A bad ML
payload or a transient database outage degrades to "this one event needs
manual attention," never "the whole pipeline stops."

**Topics are declared in code, not by hand on the broker**
(`KafkaTopicConfig`, three `NewTopic` beans, `partitions(3).replicas(1)`).
Spring Boot's `KafkaAdmin` applies these idempotently on startup — a fresh
`docker compose up` needs zero manual "create the topic" step, and the
topic configuration (partition count) lives in version control next to
the code that depends on it, not in someone's shell history.

---

## 7. Talking to the ML sidecar

`integration/MlServiceClient.java` is the entire boundary between this
Java service and the Python ML sidecar — three methods
(`getRiskScore`, `getTriggerCheck`, `checkFraud`), each a `RestTemplate`
call to a fixed path (`/risk-score`, `/trigger-check`, `/fraud-check`).

**Why `RestTemplate` and not `WebClient`** (Spring's newer, reactive HTTP
client): every call into the ML sidecar happens from inside code that's
already running on a worker thread doing blocking I/O anyway — a
`@KafkaListener` method, or a request-handling controller method backed
by Spring MVC's traditional thread-per-request model. There's no reactive
pipeline anywhere else in this codebase for `WebClient` to compose with;
adopting it here would mean either blocking on the reactive call anyway
(losing the whole point of using it) or restructuring the calling code to
be reactive too, for a service that has no other reason to be.
`RestTemplate` is simpler, is what the rest of the codebase already uses,
and is entirely adequate for a small number of synchronous calls to one
internal service — using `WebClient` here would be reaching for a more
complex tool without a corresponding benefit.

**Timeouts are explicit and short** (`RestTemplateConfig`,
`connect-timeout-ms: 3000`, `read-timeout-ms: 5000` by default). This
matters specifically because ML calls happen inside Kafka listener
threads — a hung HTTP call to a slow/down ML sidecar would hold that
consumer thread indefinitely, backing up the partition behind it (see
§6's note on why a stuck message is worse than a failed one). A bounded
timeout guarantees the call fails fast enough for the retry/DLT machinery
in §6 to actually kick in, rather than the consumer just... never coming
back.

**Every ML call site expects failure and has a defined fallback.** This
is the same fail-safe philosophy as `JwtFilter`'s Redis handling (§5): an
ML sidecar outage degrades the system's *behavior* (more conservative
decisions), never its *availability*. Concretely: if `MlServiceClient`
can't reach the sidecar, `DisruptionEventVerificationService.verify(...)`
treats the event as **not genuine** (fail closed — no unverified payouts
go out) rather than either crashing the whole pipeline or, worse,
defaulting to "assume genuine" just to keep things moving. That
asymmetry — fail closed on money-moving decisions, fail open on
availability-only decisions like the ban check — is a deliberate,
consistent pattern across this codebase, not an accident of two different
engineers making two different choices.

---

## 8. The claims lifecycle, end to end

```
PENDING_FRAUD_CHECK
   │
   ├─ fraudScore < 40  → AUTO_APPROVED ──┐
   │                                      │
   └─ fraudScore >= 40 → FLAGGED_FOR_REVIEW
                              │                 ▼
                     admin reviews:      PayoutEventProducer.requestPayout
                              │                 │
                    ┌─────────┴─────────┐       ▼
              ADMIN_APPROVED      ADMIN_REJECTED   PaymentService.initiateClaimPayout
                     │                    │              │
                     ▼                    ▼              ▼
              (same payout path)   UserService     PayoutEventProducer.publishCompleted
                                   .recordFraudStrike        │
                                                              ▼
                                                     ClaimStatusSyncListener
                                                              │
                                                              ▼
                                                        PAID or FAILED
```

There are **two ways a `Claim` gets created**, and the distinction is
architecturally significant, not just a UX detail:

**Automated (RAIN / AQI / CURFEW / TRAFFIC / WAR):** a `DisruptionEvent`
is created (by `EventTriggerScheduler`'s periodic city sweep, or an admin
via `POST /api/v1/events`), published to Kafka, and
`DisruptionEventListener` fans it out into one `Claim` per active policy
in that city — but only *after*
`DisruptionEventVerificationService.verify(...)` independently
corroborates the event against real ML trigger-check + risk-score data
(see the root README §4 for the full genuineness-gate writeup). This path
is entirely worker-passive: nobody files anything, a claim just appears
if a genuine city-wide disruption is detected.

**Worker-reported (ORDER_CANCELLED only):** `ClaimService.reportCancelledOrder(...)`,
called from `POST /api/v1/claims/cancelled-order`. This is deliberately
*not* run through the automated pipeline at all — see the root README §5
for the full reasoning (a single cancelled order is a one-worker event no
scheduler could ever detect, and automating it would fraud-flag workers
for routine, mundane cancellations outside their control). It's walled
off from the automated path at three separate layers
(`EventService.createEvent`, `DisruptionEventListener.onDisruptionEvent`,
`ClaimService.processParametricClaim`), each independently rejecting
`ORDER_CANCELLED` — defense in depth, so a bug in any one guard doesn't
open the door.

Both paths **converge on the same fraud-check → auto-approve/flag →
payout-cap logic** once a `Claim` exists — `ClaimService` doesn't have two
parallel implementations of "decide whether this claim gets paid," just
two different ways of deciding "should this `Claim` object get created in
the first place."

`ClaimService.calculatePayout(...)` is a `switch` over `EventType`, each
arm capped independently (`WAR_PAYOUT_CAP_INR`, `TRAFFIC_PAYOUT_CAP_INR`,
`ORDER_CANCELLED_PAYOUT_CAP_INR`, or the tier's `maxPayoutAmount` for
weather events) — every payout is bounded twice, once by the event-type
cap and once by the policy's own `maxPayoutAmount` (itself derived from
the worker's declared weekly income × the tier's payout ratio), so a
single claim can never exceed either the event's own severity-scaled cap
or what the worker's policy actually covers.

---

## 9. Fraud detection and the strike/ban mechanic

`FraudService.evaluate(...)` is called for **every** claim, automated or
worker-reported, with one bypass: `WAR` events skip the ML fraud model
entirely (flat `AUTO_APPROVE`, per product spec — the reasoning being that
a war/curfew-level event isn't something an individual worker could
plausibly be fabricating or gaming).

Two things worth calling out specifically:

**Idempotency by `claimId`, checked first, before anything else runs:**
```java
if (fraudRepository.existsByClaimId(claimId)) {
    log.warn("Duplicate fraud check attempted for claimId={}", claimId);
    ...
    return dup; // AUTO_APPROVE, fraudScore=0 — does NOT re-call the ML sidecar
}
```
This matters because of the at-least-once Kafka delivery semantics
covered in §6 — if the same claim somehow gets fraud-evaluated twice
(a redelivered message, a retry), the second call is a safe no-op rather
than a second real evaluation that could disagree with the first, create
two `FraudRecord`s, or — worse — issue a second strike for what's really
the same underlying claim.

**Every evaluation is durably audited** (`FraudRecord`, one row per claim,
regardless of outcome) — `userId`, `claimId`, `fraudScore`,
`recommendation`, `eventType`, and a `strikeIssued` flag that starts
`false`. That flag is what makes `recordFraudStrike(...)` — called only
when an admin actually rejects a `FLAGGED_FOR_REVIEW` claim — idempotent
too: it looks up the specific `FraudRecord` for that claim, and only
issues a strike (and flips `strikeIssued` to `true`) if one hasn't already
been issued for it. A claim can't accidentally cost a worker two strikes
because an admin's rejection click got processed twice.

**The strike/ban mechanic itself lives in `UserService.recordFraudStrike`,**
not `FraudService` — a worker's ban status is a property of the *user*,
not of any one claim, so it belongs on `UserService`, called *by*
`FraudService` once a strike is confirmed:

```java
userRepository.incrementFraudStrike(userId);
if (user.getFraudStrikeCount() >= AppConstants.FRAUD_STRIKE_BAN_COUNT) {
    userRepository.updateStatus(userId, UserStatus.BANNED);
    redisTemplate.opsForValue().set("ban:" + user.getPhone(), "1", JWT_EXPIRY_MS, MILLISECONDS);
}
```

Two things here worth noticing: the strike count only increases on an
**admin-confirmed** rejection — never automatically from a high fraud
score alone (a `FLAGGED_FOR_REVIEW` claim that an admin later approves
costs nothing) — so a worker is never punished for a claim that merely
*looked* suspicious to the model, only for a pattern of claims a human
reviewer actually rejected three times. And the Redis ban key is set with
the *same* TTL as a JWT's expiry (`JWT_EXPIRY_MS`) — a deliberate, if
slightly unusual, choice: it means the ban key expires around the same
time any JWT issued before the ban would have expired anyway, so there's
no meaningful window where a stale key lingers uselessly past the point
where it could matter. (In practice `UserStatus.BANNED` in MySQL is the
permanent record — the Redis key is the fast-path enforcement layer
described in §5, and MySQL is checked at any point Redis might be
unavailable or the key has expired, e.g. `User.isAccountNonLocked()` at
login.)

---

## 10. Risk-based pricing

`PolicyService.resolvePremium(tier, riskScore)` prices every policy by
scaling the tier's flat base premium (`PREMIUM_STANDARD_INR` = ₹15,
`PREMIUM_GOLD_INR` = ₹22, `PREMIUM_PREMIUM_INR` = ₹30) by a multiplier
derived **linearly** from the place's live ML risk score (0–100):

```java
double multiplier = RISK_PREMIUM_MULTIPLIER_FLOOR
                   + (RISK_PREMIUM_MULTIPLIER_CEIL - RISK_PREMIUM_MULTIPLIER_FLOOR) * (clampedRisk / 100.0);
return Math.round(base * multiplier);
```

A 0-risk location pays `RISK_PREMIUM_MULTIPLIER_FLOOR` (0.80×) the base
price; a 100-risk location pays `RISK_PREMIUM_MULTIPLIER_CEIL` (1.35×).
This is intentionally a **static, linear, product-tunable formula** —
not itself a machine-learned pricing model — because pricing is the one
place in this system where predictability and explainability to a
regulator or a worker ("why did my premium go up?") matter more than a
marginal gain in sophistication. The *risk score* feeding into it comes
from the ML sidecar; the *pricing curve* on top of that score is a
product decision, encoded as two named constants
(`RISK_PREMIUM_MULTIPLIER_FLOOR`/`CEIL`) that can be tuned without
touching any ML model.

`RiskService.getRiskScoreForUser(phone)` is the one call site both
`PolicyService.purchasePolicy` and `PolicyService.getAllTierInfo` share —
so the pricing screen and the actual purchase always price against the
*same* risk lookup, cached once in Redis (§3) rather than hitting the ML
sidecar redundantly for every tier shown on screen. See the root README
§9 for the real-world race condition and cache-staleness bugs this cache
introduced, and how they were fixed — `upsertRiskProfile`'s
catch-and-retry-on-`DataIntegrityViolationException` pattern in
particular is worth reading if you're touching any other "find or create"
code in this codebase, since the same race is possible anywhere a unique
constraint backs a lazily-created row.

---

## 11. Payments: collection and payout

Two entirely separate flows, both going through `PaymentService`, both
recorded in the same `PaymentRecord` table (`PaymentType.PREMIUM_COLLECTION`
vs `PaymentType.CLAIM_PAYOUT` distinguishing them):

**Premium collection** is synchronous and worker-initiated: buying a
policy creates a Razorpay order (`createPremiumOrder`), the frontend
completes checkout against Razorpay directly, and Razorpay calls back via
webhook (`POST /api/v1/payments/webhook`, one of the three `permitAll()`
routes in §5) to confirm `payment.captured` or `payment.failed`. The
webhook handler verifies Razorpay's HMAC signature
(`Utils.verifyWebhookSignature`) before trusting anything in the payload
— this is the *only* auth on that endpoint, since Razorpay's servers
obviously don't carry a GigShield JWT, and it's a correctly strong one:
without a valid signature, the handler throws a `SecurityException` before
touching any data.

**Claim payout** is asynchronous and system-initiated, entirely through
Kafka (§6, §8) — `PaymentService.initiateClaimPayout`, called only from
`PayoutRequestListener`, never directly from `ClaimService`. That
indirection is the whole point of the Kafka boundary: `ClaimService`
doesn't know or care *how* a payout gets executed, it just publishes "this
claim needs ₹X paid to this user" and moves on.

Notice what `initiateClaimPayout` currently does *not* do:
```java
// TODO: POST to Razorpay /v1/payouts when fund_account_id is registered
// In test mode: log only
log.info("[PAYOUT] ₹{} queued for claimId={} userId={}", amountInr, claimId, userId);
record.setStatus(PaymentStatus.SUCCESS);
```
It's honestly marked as not yet wired to a real payout API — every payout
in the current codebase is a logged, always-`SUCCESS` `PaymentRecord`,
not an actual bank transfer. This is a real, acknowledged gap, not a
hidden one; see §17.

---

## 12. Cross-cutting design decisions

**`AppConstants` centralizes every tunable business number** — payout
caps, fraud thresholds, premium ranges, cache TTLs, the report window for
a cancelled-order claim, all in one `final` class with no instances and a
private constructor. The alternative (magic numbers scattered across
`ClaimService`, `FraudService`, `PolicyService`, wherever they're needed)
would mean "what's the fraud auto-approve threshold" requires grepping
the whole codebase instead of opening one file. Every constant here also
carries a doc comment explaining *why* that number, not just what it is —
worth reading in full if you're about to tune pricing or fraud
sensitivity.

**Constructor injection everywhere, via Lombok's `@RequiredArgsConstructor`
on every `private final` field** — never field injection
(`@Autowired` on a field directly). This buys three things: a service
class becomes trivially unit-testable with `new ClaimService(mockA, mockB, ...)`,
no Spring context required (exactly how every `*Test.java` file in this
codebase is written — see §14); a missing dependency fails at
**application startup**, not at the first request that happens to hit the
null field; and every dependency a class has is visible in one place (its
constructor signature) rather than scattered across field declarations.

**`GlobalExceptionHandler` gives every error a consistent shape** —
`IllegalArgumentException` → 400, `IllegalStateException` → 409 (a
surprisingly precise choice: "conflict" genuinely describes things like
"an active policy already exists for this week" or a duplicate
cancelled-order report far better than a generic 400 would),
`MethodArgumentNotValidException` → 400 with a field-by-field validation
error map, `AuthenticationException`/`AccessDeniedException` → 401/403,
anything else → 500 with the real exception logged server-side but a
generic message returned to the client. That last part matters — combined
with `server.error.include-stacktrace: never` in `application.yaml`, an
unhandled exception's internals (SQL, file paths, internal class names)
never leak into an API response, only into the server's own logs.

**Two independently-configured Jackson `ObjectMapper`s exist** —
`JacksonConfig`'s bean (used everywhere via injection, including
`RiskService`'s cache serialization) and a second one built inline inside
`RedisConfig.redisTemplate()`. Both register `JavaTimeModule` and disable
timestamp serialization the same way, so they behave identically today —
but it's genuine duplication (two independent `new ObjectMapper()` calls
with hand-copied configuration) rather than one shared bean, and if either
is ever changed without the other, they'd silently drift apart. Worth
consolidating into one call site if you're touching either file — see
§17.

---

## 13. Configuration and profiles

Three layered YAML files, loaded in this order:

1. **`application.yaml`** — the base config. Every environment-dependent
   value is `${ENV_VAR:default}` — e.g. `${DB_URL:jdbc:mysql://localhost:3306/...}`
   — so the app runs locally with zero environment setup, but every value
   is overridable in any real deployment. `spring.jpa.hibernate.ddl-auto`
   defaults to **`validate`** here — deliberately the safest option: the
   app refuses to start if its entity model doesn't match the actual
   database schema, rather than silently altering a production schema out
   from under a real dataset.
2. **`application-dev.yml`** (active via `SPRING_PROFILES_ACTIVE=dev`,
   which is exactly what `docker-compose.yml` sets) — overrides
   `ddl-auto` to **`update`** (auto-create/alter tables — convenient for
   local development and this project's own testing, actively dangerous
   in production, which is exactly why it's confined to the `dev` profile
   and not the base config), turns on SQL logging, and sets `sms.mock:
   true` so OTPs print to the console log instead of needing a real SMS
   provider.
3. **`application-secrets.yml`** — imported via
   `spring.config.import: optional:application-secrets.yml`. The
   `optional:` prefix means the app **doesn't fail to start** if this file
   is absent — a deliberate fail-open on a *config-loading* concern
   (distinct from the fail-open/fail-closed *business logic* decisions in
   §5/§7), because a missing optional file shouldn't be fatal the way a
   missing required one should be. This file currently ships committed
   with placeholder dev credentials (`DB` / `Mysqldb#1` for MySQL,
   `dummy`/`dummy4` for Razorpay) precisely so a first-time clone can run
   `docker compose up --build` with zero setup — see the `.gitignore` note
   on why this file is intentionally tracked, not ignored, and why real
   production secrets should never be added to it directly.

`gigshield.jwt.secret` in the base config has **no default**
(`${JWT_SECRET}`, no `:fallback`) — Spring Boot will fail to start without
it set, which is the one secret in this system where "fail loudly at
startup" is unambiguously correct: an app that silently ran with a
predictable or empty JWT signing key would be a much worse outcome than
an app that refuses to boot.

---

## 14. Testing philosophy

Every test file in `src/test/` is a Mockito unit test
(`@ExtendWith(MockitoExtension.class)`), constructing the class under test
directly with mocked dependencies — **no** `@SpringBootTest`, no real
database, no real Kafka broker, anywhere in the actual test suite:

- `AuthServiceTest`, `PolicyServiceTest`, `FraudServiceTest`,
  `DisruptionEventListenerTest`, `DisruptionEventVerificationServiceTest`,
  `ClaimServiceTest` — each tests one service class's logic in isolation,
  verifying interactions with its (mocked) collaborators rather than
  standing up real infrastructure.

The one exception, `GigshieldBackendApplicationTests`, is a bare
`@SpringBootTest` `contextLoads()` smoke test — and it's the one test that
genuinely needs real MySQL/MongoDB/Redis/Kafka to pass, since loading the
full Spring context means every `@Configuration` class actually connecting
to its real dependency. The CI pipeline (`.github/workflows/ci.yml`)
deliberately excludes it from the fast unit-test job for exactly that
reason, and instead relies on the full `docker compose up --wait`
integration job to prove the app boots against real infrastructure — a
strictly more realistic version of the same check, just running later in
the pipeline and against the actual multi-container stack instead of an
ad-hoc set of test-only service containers.

This is a conscious trade-off worth naming honestly: there are currently
**no integration tests** that verify a controller's full HTTP request/response
cycle, no tests against a real (or embedded/Testcontainers) database
verifying repository query correctness, and no test that exercises a
message actually flowing through a real Kafka broker. Everything at that
level is either covered by the `docker compose` boot check (does the
whole system come up and report healthy) or by the ML sidecar's own
Python-side pipeline simulation (root README §8) re-implementing the
Java control flow to test business logic end to end without needing the
Java side compiled at all. That's a reasonable coverage strategy for where
this project is today, but it does mean a subtle bug in, say, a JPA query
method's derived-query semantics wouldn't be caught by anything in this
test suite specifically — see §17.

---

## 15. The Docker image

Multi-stage build, two stages:

```dockerfile
FROM maven:3.9-eclipse-temurin-17 AS build
...
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:17-jre-jammy
...
COPY --from=build /build/target/gigshield-backend-*.jar app.jar
```

**Why multi-stage:** the build stage needs the full Maven distribution and
every compile-time dependency; the runtime stage needs none of that —
just a JRE (not even a full JDK) and the one fat jar Maven produced. The
final image never contains Maven, the build cache, or any source file,
which keeps it meaningfully smaller and reduces what's available to an
attacker if the running container is ever compromised (no build toolchain
to abuse, no source code to read).

**Why `-DskipTests` in the Docker build specifically, given the whole
point of §14's test suite:** tests already ran (and gated the build) in
CI before the image build step ever starts — see
`.github/workflows/ci.yml`, where the `backend` job runs the full test
suite and only the separate `compose-integration` job (which depends on
it passing) builds the Docker image at all. Running the same tests again
inside the image build would just slow down every `docker compose up
--build` for no new information — the image build's job is to package
already-verified code, not re-verify it.

**Non-root at runtime:**
```dockerfile
RUN useradd --create-home --shell /usr/sbin/nologin gigshield
USER gigshield
```
The application process runs as an unprivileged, shell-less user, not
root — standard container-security hardening, so a vulnerability in the
running application doesn't hand an attacker root inside the container by
default.

**`HEALTHCHECK` hits `/actuator/health`** — the same Spring Boot Actuator
endpoint that's `permitAll()`'d in `SecurityConfig` for exactly this
reason (§5) — every 15s, with a 45s `start_period` grace window for JVM
startup + Spring context initialization before health checks start
counting against it, and this is what `docker-compose.yml`'s
`depends_on: backend: condition: service_healthy` and the CI pipeline's
`docker compose up --wait` both key off of.

---

## 16. API surface, with auth requirements

| Method | Path | Auth |
|---|---|---|
| POST | `/api/v1/auth/send-otp` | public |
| POST | `/api/v1/auth/verify-otp` | public |
| POST | `/api/v1/auth/refresh` | public |
| POST | `/api/v1/payments/webhook` | Razorpay HMAC signature (not JWT) |
| GET | `/actuator/health` | public |
| * | `/api/v1/admin/**` | `ROLE_ADMIN` |
| POST | `/api/v1/events` | `ROLE_ADMIN` |
| PATCH | `/api/v1/events/*/resolve` | `ROLE_ADMIN` |
| GET | `/api/v1/claims/flagged` | `ROLE_ADMIN` |
| POST | `/api/v1/claims/*/review` | `ROLE_ADMIN` |
| GET | `/api/v1/dashboard/admin` | `ROLE_ADMIN` |
| everything else under `/api/v1/**` | any authenticated user (`ROLE_WORKER` or `ROLE_ADMIN`) |

This includes, among the "any authenticated user" routes: policy purchase
and tier info, risk score lookup, claims listing, the worker-reported
cancelled-order endpoint, the worker dashboard, and profile updates. See
the root README §10 for the full endpoint-by-endpoint description of what
each one does at the product level; this table is specifically about
*who's allowed to call it*.

---

## 17. Known rough edges

Named honestly, not swept under the rug — these are real, current gaps in
this codebase, roughly in order of how much they'd matter before this
went anywhere beyond a demo/hackathon deployment:

- **Claim payouts aren't wired to a real money-movement API yet**
  (§11) — `PaymentService.initiateClaimPayout` logs and marks every
  payout `SUCCESS` unconditionally; there's no real Razorpay Payouts API
  call, no real failure path exercised in production code (the `FAILED`
  branch exists and is tested, but nothing in this codebase can currently
  *produce* one). This is the single biggest gap between "this pipeline is
  architecturally sound" and "this pipeline actually pays people."
- **OTPs are stored in plaintext in `OtpRecord`** (MySQL), not hashed.
  They're short-TTL (5 minutes), single-use, and attempt-limited, which
  meaningfully bounds the practical risk, but a hash (even something
  cheap like SHA-256, since brute-force resistance is already handled by
  the attempt limit) would mean a database read/leak doesn't hand out
  currently-valid OTPs directly.
- **CORS allows every origin** (§5) — fine for local development and this
  project's current demo/testing scope, not something to carry into a
  real deployment with real user data without tightening to an explicit
  origin allowlist.
- **`spring-session-data-redis` is a declared dependency but nothing uses
  it** — auth is fully stateless via JWT (§5), so there's no
  `HttpSession` for Spring Session to back with Redis anywhere in this
  codebase. Either a currently-unused dependency worth removing, or a
  signal that some future feature (an admin web console using traditional
  session auth, perhaps) was anticipated but never built.
- **Two independently-configured Jackson `ObjectMapper`s** (§12) — small,
  currently harmless, but genuine duplication worth consolidating.
- **No integration tests below the full `docker compose` level** (§14) —
  a real gap in test depth between "Mockito verified this method calls
  its collaborators correctly" and "this actually works against a real
  database/broker," currently bridged only by the compose-integration CI
  job and the ML sidecar's Python-side pipeline simulation, neither of
  which is a JVM-level integration test.
- **The unique-constraint race condition pattern seen in
  `RiskService.upsertRiskProfile`** (§3, §10 — full writeup in the root
  README §9) is a pattern that could recur anywhere else in this codebase
  that does a "find or create" against a uniquely-constrained row without
  the same catch-and-retry treatment. Worth auditing for other
  `findByX(...).orElse(build new)` call sites if you're hardening this
  further — `RiskProfile` is the one that was actually hit in practice,
  not necessarily the only one capable of it.

None of these are hidden defects discovered by someone else — they're the
honest state of a project built and iterated on quickly, documented here
specifically so the next person working on this backend inherits an
accurate map of where the real edges are, not a README that only
describes the happy path.
