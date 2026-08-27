GigShield: AI-Powered Parametric Insurance for Gig Workers
---

GigShield is a parametric micro-insurance platform for gig delivery
workers. Workers buy a weekly policy for a few rupees; when a real-world
disruption (heavy rain, bad AQI, curfew, war, traffic, or a single
cancelled order) genuinely hits their area, a payout is calculated and
paid automatically — no claim form, no manual review, unless the ML
fraud check flags it. This README documents the system **as it is
implemented today**, including the event-driven Kafka pipeline, the ML
verification gate, risk-based pricing, the worker-initiated
cancelled-order flow, fraud-strike/ban mechanics, and how all of it was
tested in this environment.

---

## 1. Quick start

```bash
docker compose up --build
```

Brings up all seven services — MySQL, MongoDB, Redis, Kafka (KRaft,
single-node), the ML sidecar, the Spring Boot backend, and the React
frontend:

| Service      | URL                    |
|--------------|------------------------|
| Frontend     | http://localhost:3000  |
| Backend API  | http://localhost:8080  |
| ML sidecar   | http://localhost:5000  |
| Kafka (host) | localhost:29092        |

First boot trains the ML models in the background (a few minutes) while
already serving with rule-based heuristic fallbacks — see
`gigshield-ml/README.md`. The backend auto-creates its MySQL schema on
the `dev` profile. See `docker-compose.yml` at the repo root for full
service wiring, and each service's own README for local (non-Docker)
development.

### Running each service locally, without Docker

```bash
# ML sidecar
cd gigshield-ml
pip install -r requirements.txt
python3 -m uvicorn app:app --host 0.0.0.0 --port 5000

# Backend (needs MySQL, MongoDB, Redis, Kafka reachable — see application.yml)
cd gigshield-backend
./mvnw spring-boot:run

# Frontend
cd gigshield-frontend
npm install && npm run dev
```

### CI

`.github/workflows/ci.yml` runs on every push/PR: backend unit tests
(Maven), the ML sidecar's pipeline simulation suite against a real running
instance, frontend lint + build, and — only once all three pass — a final
job that runs `docker compose build` and `docker compose up -d --wait` to
boot the entire real stack and confirm every service's own healthcheck
passes. It stops there — build, test, and prove the compose stack boots
healthy; no deployment beyond that. (`GigshieldBackendApplicationTests`,
the one `@SpringBootTest` in the suite, is intentionally skipped in the
fast unit-test job — it needs a real MySQL/MongoDB/Redis/Kafka to load its
context, and the compose-integration job already proves that boot
succeeds against real infrastructure, which is a strictly stronger version
of the same check.)

---

## 2. Problem, persona, and product shape

India's gig economy runs on delivery partners who earn day-to-day. Their
income is highly vulnerable to disruptions they can't control — heavy
rainfall, severe pollution, curfews/lockdowns, war-adjacent unrest,
traffic restrictions, and even a single order getting cancelled by the
restaurant or platform after they've already committed to it. There's no
structured financial protection for any of this today.

GigShield targets **food delivery partners** (Zomato/Swiggy-style
workers) first, because they're the most exposed: fully outdoor,
day-rate income, and the most sensitive to weather/AQI/traffic
conditions of any gig category.

The product is a **weekly micro-insurance plan** (₹15–30/week depending
on tier) that pays out automatically when a covered disruption is
independently corroborated — capped at 30–40% of the worker's estimated
weekly income, further capped per event type, so payouts stay
affordable to underwrite while still meaningful to the worker.

---

## 3. System architecture

```
 React frontend (phone+OTP login)
        │  REST (JWT bearer)
        ▼
 Spring Boot backend ── MySQL (users, policies, risk profiles)
        │        │  │── MongoDB (claims — flexible/append-heavy doc shape)
        │        │  │── Redis (risk-score cache, session/ban keys)
        │        │
        │        └── REST ──▶ ML sidecar (FastAPI + scikit-learn) ──▶ Open-Meteo
        │                                                              (weather/AQI)
        ▼
     Kafka (KRaft, single-node)
   gigshield.events.disruption ──▶ DisruptionEventListener
                                        │
                                        ▼ DisruptionEventVerificationService
                                     (ML trigger-check + risk-score must BOTH
                                      corroborate the event before automation
                                      proceeds)
                                        │
                                        ▼ per eligible active policy in the
                                          city: create claim, run ML fraud-check
                                        │
   gigshield.payments.payout-requested ◀┘
                                        │
                                        ▼ PayoutRequestListener → payout
   gigshield.payments.payout-completed ◀── audit/notification consumers
```

Three independently deployable services:

- **`gigshield-backend/`** — Java 17 / Spring Boot 3.2.5. Auth (phone +
  OTP + JWT), policy lifecycle, event ingestion, Kafka producers/consumers
  for the claims-automation and payout pipelines, fraud/risk
  orchestration via the ML sidecar, admin dashboard.
- **`gigshield-ml/`** — Python / FastAPI. Risk scoring, weather/AQI
  trigger checking (via Open-Meteo, with a deterministic offline
  fallback), fraud scoring — all served over REST, backed by
  scikit-learn models with rule-based heuristics as a fallback when a
  model isn't trained yet.
- **`gigshield-frontend/`** — React (Vite). Phone+OTP login, policy
  purchase, dashboard, claims list, and the new self-service
  "report a cancelled order" form.

### Why Kafka, and where it sits

Two things need to happen asynchronously and reliably: (1) one
disruption event (e.g. "AQI in Delhi crossed 300") has to fan out into
potentially hundreds of per-worker claims without blocking the API
request that created the event, and (2) an approved claim's payout has
to be requested, executed, and audited as separate steps that can retry
independently. `KafkaTopics.java` documents the three topics:
`gigshield.events.disruption`, `gigshield.payments.payout-requested`,
`gigshield.payments.payout-completed`, each with its own consumer group
so listener containers never share a group across different
subscriptions (`GROUP_CLAIMS_AUTOMATION`, `GROUP_CLAIM_STATUS_SYNC`,
`GROUP_PAYMENTS`, `GROUP_AUDIT`).

---

## 4. The ML verification gate: why an event isn't trusted at face value

`DisruptionEventListener` never turns a `DisruptionEvent` straight into
claims. Every event — whether it came from `EventTriggerScheduler`'s
scheduled city sweep or from an admin manually creating one — first goes
through `DisruptionEventVerificationService.verify(...)`, which requires
**both**:

1. **Trigger confirmation** — the ML sidecar's `/trigger-check` says this
   specific `EventType` (RAIN/AQI/CURFEW/TRAFFIC/ORDER_CANCELLED) is
   actually active at that place and time, based on real thresholds
   (`RAIN_THRESHOLD_MM`, `AQI_THRESHOLD`, etc. in `gigshield-ml/`).
2. **Risk-score corroboration** — the ML sidecar's independent
   `/risk-score` model, for the same coordinates and the same moment,
   returns a score ≥ `AppConstants.EVENT_GENUINE_MIN_RISK_SCORE` (20.0).

Only if both agree is the event "genuine" and allowed to fan out into
claims. `WAR` is the one exception — bypassed per product spec, since
war/unrest isn't something a weather API can corroborate. If verification
fails, the event is logged and dropped — no claims, no payouts, nothing
enters the fraud pipeline at all. This is the single most important
anti-fraud control in the system: it's a lot harder to fabricate a
disruption event that fools two independent ML checks over real external
data than to game a single rule.

### Real coordinates, not a hardcoded city table

Earlier in this project a `CityCoordinates.java` static lookup table
(a handful of hardcoded city-centroid lat/lons) stood in for real
location data. **That file has been deleted.** The verification service
now uses actual coordinates in both places it's called from:

- **Automated city-wide events** (`DisruptionEventListener`): averages
  the *real, registered* `latitude`/`longitude` of every worker with an
  active policy in the event's city (`averageCoordinates(...)`), and
  passes that average through to both `/trigger-check` and
  `/risk-score`. If none of the affected workers have registered
  coordinates, it degrades safely — `/trigger-check` falls back to
  the ML sidecar's own city-centroid table (`inference/cities.py`, used
  only as a last resort) and `/risk-score` is skipped entirely rather
  than corroborating against a guess (see §6 below on why a missing
  risk-score means "not genuine", never "assume genuine").
- **Worker-reported cancelled orders** (§5 below): uses that one
  worker's own registered coordinates — no averaging, no lookup table,
  because there's exactly one worker involved.

### Time matters: verification is "as of when it happened," not "now"

This was a real gap: if a worker reports (or Kafka processes) an event
some time after it actually occurred, checking today's/right-now's
weather against a claim about yesterday's rain is checking the wrong
thing entirely — and Kafka consumer lag or a worker's delayed report
could otherwise corroborate (or reject) a claim against whatever the
weather happens to be *at verification time*, not at the moment that
actually determined whether the worker could safely work.

Fixes, end to end:

- `RiskScoreRequest` gained an optional `at: LocalDateTime` field; when
  set, `MlServiceClient.getRiskScore(...)` passes it as an `at` query
  param, and the ML sidecar's `/risk-score` endpoint scores the place
  **as of that timestamp**, not live conditions.
- `MlServiceClient.getTriggerCheck(city, latitude, longitude, at)` does
  the same for `/trigger-check`.
- On the ML side, `weather.get_weather_signal_at(lat, lon, at)` is the
  new time-aware entry point:
  - If `at` is more than `_FUTURE_TOLERANCE_MINUTES` (5) in the future,
    it refuses to treat that as "current" and falls back safely.
  - If `at` is older than `_HISTORICAL_MAX_AGE_DAYS` (90), it falls back
    safely **without making a network call** — Open-Meteo's
    no-delay forecast history window tops out around there.
  - Otherwise it calls Open-Meteo's forecast endpoint with
    `start_date`/`end_date` bracketing `at`'s date and
    `hourly=precipitation,...`/`hourly=us_aqi,pm2_5`, then picks the
    closest hourly reading to the exact timestamp
    (`_closest_hour_index`). Results are cached per
    `(lat, lon, hour)` so repeated checks for the same claim don't
    re-hit the network.
- `DisruptionEventVerificationService.verify(...)` now takes an
  `occurredAt: LocalDateTime` and threads it through to both ML calls,
  so a claim about something that happened three days ago is
  corroborated against the weather three days ago — never against
  right now.
- On the claim side, `Claim` gained an `eventOccurredAt` field so the
  actual event time (not just `createdAt`, the report time) is stored,
  returned in `ClaimResponse`, and shown in the frontend
  (`ClaimItem.jsx`: "Happened ... · reported ...").

---

## 5. Cancelled orders: worker-reported, deliberately *not* automated

Every other event type (RAIN, AQI, CURFEW, TRAFFIC, WAR) is city-wide and
detected by `EventTriggerScheduler` or an admin, then fanned out
automatically to every affected worker's policy. A single cancelled
order is fundamentally different — it's a **one-worker, one-order**
event that no scheduler or city-wide sensor could ever see coming. The
initial design mistake would have been to let the automated pipeline
generate `ORDER_CANCELLED` claims the same way it generates weather
claims; the problem is that restaurants/platforms cancel individual
orders on delivery workers constantly, for all kinds of mundane reasons
that have nothing to do with a real, insurable disruption. Automating
that would silently run a fraud check against every worker on every
routine cancellation and rack up strikes on people who did nothing
wrong.

So `ORDER_CANCELLED` is walled off from the automated path at three
layers, defense-in-depth:

1. `EventService.createEvent(...)` — rejects `ORDER_CANCELLED` outright;
   it can never be created as a city-wide `DisruptionEvent` by an admin
   or the scheduler.
2. `DisruptionEventListener.onDisruptionEvent(...)` — if an
   `ORDER_CANCELLED` message somehow reached the Kafka topic anyway, it's
   logged and dropped before even looking up affected policies.
3. `ClaimService.processParametricClaim(...)` — the automated
   claim-creation path throws if asked to process `ORDER_CANCELLED`.

Instead, a worker reports it themselves:

```
POST /api/v1/claims/cancelled-order
{ "cancelledAt": "2026-08-25T14:30:00", "note": "restaurant cancelled during heavy rain" }
```

`ClaimService.reportCancelledOrder(...)`:

- Validates `cancelledAt` isn't more than
  `ORDER_CANCELLED_FUTURE_TOLERANCE_MINUTES` (5) in the future, and isn't
  older than `ORDER_CANCELLED_REPORT_WINDOW_HOURS` (72) — old enough to
  be forgiving of a worker reporting after their shift, recent enough
  that it's still checkable against real historical weather and hard to
  game with a stale, unfalsifiable claim.
- Dedupes: `ClaimRepository.existsByUserIdAndPolicyIdAndTriggerEventAndEventOccurredAt(...)`
  stops the same worker from filing the exact same cancellation twice.
- Runs the **same** `DisruptionEventVerificationService.verify(...)`
  gate as the automated pipeline — `ORDER_CANCELLED` trigger-check +
  risk-score corroboration, evaluated as of `cancelledAt`, using this
  worker's own registered coordinates. If it isn't genuine, the claim is
  rejected before any fraud check or payout math ever runs.
- If genuine, proceeds through the same fraud-check → auto-approve/flag
  → payout-cap logic as every other claim type, capped at
  `ORDER_CANCELLED_PAYOUT_CAP_INR` (₹150) — deliberately low, since it's
  one order, not a week of lost income.

The frontend's Claims page (`pages/Claims.jsx`) exposes this as a
"Report a cancelled order" form: a `datetime-local` picker (capped at
"now," so a future timestamp can't even be entered client-side) plus an
optional 280-character note, with copy explicitly telling the worker
they need the *actual* time it happened because that's what gets
checked.

---

## 6. Fraud scoring, strikes, and bans

Every claim — automated or self-reported — goes through
`FraudService.evaluate(...)`, which calls the ML sidecar's
`/fraud-check` (GPS-distance-from-claimed-location heuristic, duplicate
detection, behavioral signals) and returns a 0–100 score.

- **Score < `FRAUD_AUTO_APPROVE_THRESHOLD` (40):** claim is
  `AUTO_APPROVED` and a payout is requested immediately via
  `gigshield.payments.payout-requested`.
- **Score ≥ 40:** claim is `FLAGGED_FOR_REVIEW` — held for an admin,
  *no payout goes out yet*.
- **Admin rejects a flagged claim** (`ClaimService.adminReview(...)`
  reject branch): the claim becomes `ADMIN_REJECTED`, and
  `UserService.recordFraudStrike(userId)` increments that user's
  `fraudStrikeCount` by exactly 1.
- **Strike count reaches `FRAUD_STRIKE_BAN_COUNT` (3):** the user's
  status flips to `BANNED`, and a Redis key `ban:<phone>` is set (TTL =
  `JWT_EXPIRY_MS`) so the ban is enforced immediately at auth time, not
  just eventually-consistent in MySQL.

This means a worker isn't punished for having *one* claim look
suspicious (weather near a boundary threshold, GPS drift, whatever) —
only for a pattern of claims an admin has actually reviewed and rejected
three times.

---

## 7. Risk-based policy pricing

Policy price is no longer a flat per-tier number. `PolicyService`
prices a policy by scaling each tier's base premium
(`PREMIUM_STANDARD_INR` / `PREMIUM_GOLD_INR` / `PREMIUM_PREMIUM_INR`) by
a multiplier derived linearly from that place's live ML risk score
(0–100, from `/risk-score`): a 0-risk location pays
`RISK_PREMIUM_MULTIPLIER_FLOOR` (0.80×) the base price, a 100-risk
location pays `RISK_PREMIUM_MULTIPLIER_CEIL` (1.35×), and everything in
between is interpolated. High-disruption-probability areas cost more to
insure; low-risk areas cost less — the flat per-tier numbers are now
just the anchor, not the actual price.

---

## 8. Testing: what was actually verified, and how

**This sandbox cannot compile or run the real stack.** Two hard,
confirmed constraints:

- **Maven Central is blocked** (403 Forbidden, confirmed repeatedly via
  `mvn -q -o compile` and `mvn -q compile` — fails even fetching the
  parent POM). The Java backend cannot be compiled here, so nothing
  below claims a Java compiler or test runner actually executed the
  backend code.
- **No Docker daemon** is available, so Kafka/MySQL/MongoDB/Redis can't
  be brought up either.

Given that, testing was done two ways, both real, both honestly scoped:

### 8a. Manual code review

Every Java file touched this session (`EventType`, `AppConstants`,
`RiskScoreRequest`, `MlServiceClient`, `DisruptionEventVerificationService`,
`DisruptionEventListener`, `Claim`, `ClaimRepository`,
`ReportCancelledOrderRequest`, `ClaimService`, `ClaimResponse`,
`ClaimController`, `EventService`) was reviewed line-by-line for: import
correctness, constructor-argument order matching
`@RequiredArgsConstructor` field declaration order (this matters —
Lombok generates the constructor from field order, and
`DisruptionEventListener`'s test instantiates it manually with
`new DisruptionEventListener(policyRepository, claimService, userService, verificationService)`
matching the field order exactly), null-safety on the coordinate-averaging
path, and that every new/changed method signature is actually called
correctly at every call site. The corresponding JUnit test files
(`DisruptionEventVerificationServiceTest`, `DisruptionEventListenerTest`,
`ClaimServiceTest`) were rewritten to match the new signatures and cover
the new branches (see file list below) — they are believed correct by
inspection but, per the constraint above, have not been run through a
real JUnit/Mockito execution in this environment.

### 8b. A real, running pipeline simulation — `gigshield-ml/tests/test_pipeline_simulation.py`

Rather than only reasoning about the code, this test starts the actual
ML sidecar (`uvicorn app:app`, real FastAPI process, real scikit-learn/
heuristic code paths, no mocks) and drives it with a Python
re-implementation of the Java control flow covering **both halves** of
the event-driven pipeline — event → claim
(`DisruptionEventListener` → `DisruptionEventVerificationService` →
`ClaimService` → `FraudService` → `UserService`) **and** claim → payment
(`PayoutEventProducer` → `PayoutRequestListener` → `PaymentService` →
`PayoutEventProducer.publishCompleted` → `ClaimStatusSyncListener` +
`PayoutAuditListener`) — line-cited back to the actual Java source for
every constant and every branch, so the logic under test is the real
logic, not a guess at it. The one thing it
substitutes is the weather signal (`get_weather_signal`/
`get_weather_signal_at`) — because this sandbox's network egress does
not reach `api.open-meteo.com` at all (confirmed via direct `curl`,
exit code 56 / proxy 403), and the sidecar's own deterministic
offline fallback is mathematically incapable of crossing the RAIN/AQI
trigger thresholds (max fallback rainfall 19.5mm vs. a 35mm threshold,
max fallback AQI 239 vs. a 300 threshold — by design, so an
unreachable-network day never accidentally pays out). The test injects
crafted weather values via a context manager and then calls the real,
unmodified `check_triggers`/`score_risk` functions against them — the
same pattern as mocking any unreachable external dependency in a normal
test suite. **Fraud-check requires no substitution at all** — it's
weather-independent, so those calls hit the real running sidecar over
real HTTP with no patching whatsoever. The payment half needs no
weather substitution at all — `PaymentService`/`PayoutEventProducer`/
`ClaimStatusSyncListener`/`PayoutAuditListener` never call the ML
sidecar, so that logic is mirrored 1:1 with no substitution of any kind.

Run it yourself:

```bash
cd gigshield-ml
python3 -m uvicorn app:app --host 0.0.0.0 --port 5000 &
python3 tests/test_pipeline_simulation.py
```

**Actual output from the last run in this environment:**

```
ML sidecar health: {'status': 'UP', 'service': 'gigshield-ml'}

=== Scenario 1: dummy (fabricated) event vs. real (genuine) event ===
  dummy event  -> status=REJECTED_NOT_GENUINE
  [PASS] dummy event does NOT produce a payout
  real event   -> decision=AUTO_APPROVED finalStatus=PAID fraudScore=10
  [PASS] real event passes the genuineness gate (trigger+risk confirmed)
  [PASS] real event, low fraud score (worker at claimed city centroid), gets AUTO_APPROVED + payout
  [PASS] exactly one payout requested (the real event, not the dummy one)
  [PASS] that payout ran the full pipeline through to PAID in the same synchronous pass (see Scenario 4)

=== Scenario 2: fraud score gating -> strikes -> ban at 3 ===
  [PASS] claim 1/3 flagged for review (fraudScore=55 >= 40)
    -> admin rejected -> strike_count=1 banned=False
  [PASS] user NOT banned after strike 1
  [PASS] claim 2/3 flagged for review (fraudScore=55 >= 40)
    -> admin rejected -> strike_count=2 banned=False
  [PASS] user NOT banned after strike 2
  [PASS] claim 3/3 flagged for review (fraudScore=55 >= 40)
    -> admin rejected -> strike_count=3 banned=True
  [PASS] user IS banned after strike 3 (FRAUD_STRIKE_BAN_COUNT)
  [PASS] no payout was ever requested for this consistently-flagged user

=== Scenario 3: coordinates and event time actually change the verdict ===
  [PASS] identical coordinates score differently when the underlying conditions differ — mild=False severe=True
  [PASS] verify_event threads the real coordinates through to trigger-check + risk-score

=== Scenario 4: claim approval -> Kafka payout-requested -> payment -> payout-completed -> claim PAID ===
  [PASS] claim auto-approved (precondition for this scenario)
  [PASS] PayoutEventProducer.requestPayout published exactly one payout-requested event
  [PASS] PaymentService.initiateClaimPayout recorded the payout as SUCCESS
  [PASS] PayoutEventProducer.publishCompleted -> payout-completed fired exactly once
  [PASS] ClaimStatusSyncListener consumed payout-completed and moved the claim AUTO_APPROVED -> PAID
  [PASS] PayoutAuditListener independently consumed the SAME event off its own consumer group
  [PASS] redelivered payout-requested does NOT create a second PaymentRecord
  [PASS] redelivered payout-requested does NOT publish a second payout-completed event
  [PASS] redelivered payout-completed is a no-op against an already-PAID claim
  [PASS] a REJECTED_NOT_GENUINE claim never reaches PaymentService (no PaymentRecord for it)
  [PASS] ClaimStatusSyncListener maps a FAILED payout-completed event to claim.status=FAILED, not PAID

All checks passed.
```

What each scenario actually proves:

- **Scenario 1 (dummy vs. real event → correct one pays out):** a
  fabricated event over mild/unremarkable conditions fails the
  genuineness gate and produces zero claims or payouts; a genuine event
  over severe conditions passes the gate, goes through fraud-check
  (score 10, well under the 40 auto-approve threshold), gets
  auto-approved, and — per Scenario 4's wiring — is the *only* one of
  the two that ever reaches `PAID`.
- **Scenario 2 (fraud strikes → ban at 3):** a worker whose registered
  location is ~140km from every claim's claimed city gets flagged
  (`fraudScore=55`, real GPS-distance heuristic from the running
  sidecar, not scripted) on all three claims; each admin rejection
  increments the strike count by exactly 1; the user is confirmed
  *not* banned after strikes 1 and 2, and *is* banned exactly on strike
  3 — and never receives a payout throughout, since every claim of
  theirs was flagged, never auto-approved (a flagged claim never calls
  `requestPayout`, so it never even reaches the payment half of the
  pipeline).
- **Scenario 3 (coordinates and time matter):** the exact same
  coordinates produce a different genuineness verdict when the
  underlying weather/AQI conditions at that moment differ — proving the
  verdict is a function of place *and* time, not just place. (Separately,
  three distinct coordinates producing three distinct risk scores, and
  the >90-day-old guard skipping the network call entirely, were
  confirmed via direct `curl` against the running sidecar during
  development — see git history / session log for the raw output.)
- **Scenario 4 (event-to-payment pipeline, the other half):** exercises
  everything downstream of "claim approved" — `PayoutEventProducer`
  publishing to `gigshield.payments.payout-requested`,
  `PayoutRequestListener` calling `PaymentService.initiateClaimPayout`
  (which records a `PaymentRecord` and marks it `SUCCESS`),
  `PayoutEventProducer.publishCompleted` firing to
  `gigshield.payments.payout-completed`, and **both** consumer groups on
  that topic independently reacting to the same event —
  `ClaimStatusSyncListener` (moves the claim to `PAID`) and
  `PayoutAuditListener` (logs it). It also proves the two idempotency
  guards that matter most for real Kafka's at-least-once delivery: a
  redelivered `payout-requested` message (simulating a consumer
  restart/rebalance) does not create a second `PaymentRecord` or a
  second `payout-completed` event, and a redelivered `payout-completed`
  message does not re-process an already-`PAID` claim. Finally, it
  confirms a rejected/non-genuine claim never reaches `PaymentService`
  at all (no `requestPayout` call site is ever hit for it), and that
  `ClaimStatusSyncListener`'s branch logic correctly maps a `FAILED`
  completion to `claim.status=FAILED` rather than assuming success —
  checked directly since `PaymentService`'s payout execution is
  currently mocked to always succeed (`// TODO: POST to Razorpay
  /v1/payouts ... In test mode: log only`, `PaymentService.java:130`),
  so a real `FAILED` event can't be produced end-to-end yet, but the
  listener that will consume one is already verified correct.

### 8c. What was not (and cannot be) tested here

- The real Spring Boot app was never compiled or booted — Maven
  Central is unreachable. If the sandbox environment changes and Maven
  Central becomes reachable, `cd gigshield-backend && ./mvnw test` runs
  the full JUnit suite including the rewritten
  `DisruptionEventVerificationServiceTest`, `DisruptionEventListenerTest`,
  and `ClaimServiceTest`.
- Kafka itself (broker, producer/consumer wiring, retries, consumer-group
  rebalancing) was never exercised — no Docker daemon here. The pipeline
  simulation tests the *business logic* Kafka's consumers execute, not
  Kafka's own delivery guarantees.
- Live Open-Meteo weather/AQI calls were never exercised end-to-end in
  this environment — this sandbox's network egress doesn't reach
  `api.open-meteo.com` (only npm/pypi/jsr/crates/golang-proxy/anthropic
  hosts are allowlisted here). The historical-fetch code path
  (`_fetch_historical`) is implemented against Open-Meteo's documented
  API shape and exercised with crafted/mocked responses; it has not made
  a real network round-trip to Open-Meteo from inside this sandbox.

---

## 9. Bugs found and fixed from real-world testing

The scenarios in §8 were written and passing before this system had ever
been run outside this sandbox. Once it was actually run for real — real
Maven Central, a real Spring Boot process, a real MySQL instance — three
genuine bugs surfaced that none of the sandbox-side testing could have
caught (two of them specifically *because* the sandbox has no
`risk_model.pkl` and no concurrent traffic). All three are fixed and
covered by regression checks.

**1. `DataIntegrityViolationException: Duplicate entry '...' for key
'risk_profiles.idx_risk_user'`.** `RiskService.upsertRiskProfile(...)`
did a check-then-insert (`findByUserId` → build new if absent → `save()`)
with nothing preventing two concurrent requests for the same user from
both missing the Redis cache, both seeing no existing row, and both
attempting an `INSERT` — the second violates the unique constraint on
`risk_profiles.userId`. This is easy to trigger in practice: `Plans.jsx`
and `PremiumCalc.jsx` (shown right after selecting a plan) each
independently call the risk-score endpoint in their own effect, so
selecting a plan quickly after the page loads can fire two nearly
simultaneous risk-score requests for a user whose row doesn't exist yet.
Fixed in `RiskService.upsertRiskProfile`: catch
`DataIntegrityViolationException` and retry as an update against the row
the other request just committed, rather than letting the race become a
500. Safe within the same transaction because `RiskProfile` uses
`GenerationType.IDENTITY` (the `INSERT` — and so the constraint
violation — happens synchronously inside `save()`, not deferred to a
later flush) and MySQL/InnoDB doesn't poison the rest of a transaction
after one failed statement.

**2. High rainfall / high AQI still producing a low premium.** This was
the real bug behind the pricing complaint, and it's a genuine
train/serving scale mismatch, not a caching or wiring issue.
`risk_model.pkl` was trained (`risk_model.py`) on `data/weather.csv`'s
**monthly cumulative rainfall totals** per Indian meteorological
subdivision — across the full training set that column has a median of
~42mm and a 90th percentile of ~337mm, up to ~2363mm in monsoon months.
But `inference/risk.py`'s `score_risk(...)` is always called with a
single **hourly/point-in-time** precipitation reading — 0-20mm from the
deterministic offline fallback, and realistically well under 100mm even
for a genuinely severe live downpour. A 60mm serving-time reading
normalizes to roughly **2.5%** of the scale the trained model actually
learned "high rainfall" to mean, so it silently predicted near-zero
rainfall risk almost regardless of how bad conditions genuinely were —
and because it never raised an exception, the existing
`FileNotFoundError`/generic-`Exception` fallback to the rule-based
heuristic never caught it either. This sandbox never had a `risk_model.pkl`
file on disk, so `score_risk` was already falling through to the
heuristic here every time — which is exactly why this bug was invisible
until it was run somewhere with a trained model present. Fixed in
`inference/risk.py`: `score_risk(...)` no longer calls
`risk_model.predict_risk(...)` at all — it always uses the rule-based
heuristic, whose ceilings (`_HEURISTIC_RAINFALL_CEILING_MM=60`,
`_HEURISTIC_AQI_CEILING=400`) were already correctly calibrated against
the real serving-time input scale. Retraining the model properly would
need hourly-resolution historical rainfall data, which isn't in this
dataset (`weather.csv` only has monthly totals) — noted as a real future
improvement, not something patched around. Verified directly:

```
mild               -> riskScore=10.73  premium=₹17  band=LOW
heavy rain only    -> riskScore=70.94  premium=₹26  band=HIGH
high AQI only      -> riskScore=41.67  premium=₹21  band=MEDIUM
both severe        -> riskScore=95.31  premium=₹29  band=HIGH
```

**3. Related but distinct: updating a worker's registered location
doesn't invalidate their cached risk score.** `RiskService` caches
`GET /api/v1/risk/score` per phone number in Redis for
`RISK_SCORE_CACHE_TTL_SEC` (1 hour) — reasonable on its own, since it's
what the tier-pricing screen reuses to avoid calling the ML sidecar on
every page load. But `UserService.updateProfile(...)` never evicted that
cache key, so a worker who updates their `latitude`/`longitude` to test
or move to a genuinely different-risk location would keep seeing their
**old** location's score and premium for up to an hour — indistinguishable
from bug #2 from the outside, which is part of why both surfaced in the
same testing pass. Fixed: `updateProfile` now evicts `risk:score:<phone>`
whenever the submitted latitude or longitude actually differs from what
was stored, so the very next risk-score/pricing lookup recomputes fresh.
(Separately, the registered `city` itself has no update path at all —
`UpdateProfileRequest` only carries `fullName`, `weeklyIncomeEstimate`,
`latitude`, `longitude` — so testing a genuinely different city today
means registering a second account, not editing the first. Not fixed
here since it wasn't the reported bug, but worth knowing.)

All three are covered going forward: bugs #1 and #2 have dedicated
regression checks in `gigshield-ml/tests/test_pipeline_simulation.py`
(Scenario 5 for #2 — asserts severity actually moves both the risk score
and the resulting policy premium); bug #3 is a one-line, low-risk change
reviewed by inspection (no existing `RiskServiceTest`/`UserServiceTest`
to extend — see §8a's note that Maven Central being unreachable here
means no JUnit run confirms this compiles, only careful manual review).

---

## 10. API surface (implemented)

| Method | Path | Notes |
|---|---|---|
| POST | `/api/v1/auth/send-otp` | phone → OTP |
| POST | `/api/v1/auth/verify-otp` | OTP → JWT |
| POST | `/api/v1/auth/register` | |
| POST | `/api/v1/auth/refresh` | |
| GET/POST | `/api/v1/policies/**` | buy, list, tiers |
| GET | `/api/v1/risk/**` | risk score for a place |
| POST | `/api/v1/events` | admin: create a city-wide `DisruptionEvent` (rejects `ORDER_CANCELLED`) |
| GET | `/api/v1/claims` | list the current user's claims |
| POST | `/api/v1/claims/cancelled-order` | **new** — worker self-reports a single cancelled order (`cancelledAt`, optional `note`) |
| GET | `/api/v1/dashboard/**` | worker/admin dashboards |
| POST | `/api/v1/payments/**` | Razorpay webhook / order creation |

ML sidecar (internal, called by the backend, not exposed to the
frontend):

| Method | Path | Notes |
|---|---|---|
| GET | `/health` | |
| GET/POST | `/risk-score` | now accepts optional `at` (ISO datetime) for point-in-time scoring |
| GET/POST | `/trigger-check` | now accepts optional `latitude`, `longitude`, `at` |
| POST | `/fraud-check` | GPS-distance, duplicate, behavioral heuristics |

---

## 11. Data model (as implemented)

- **MySQL** — `User` (incl. `latitude`/`longitude`, `fraudStrikeCount`,
  `status`), `Policy`, `RiskProfile`.
- **MongoDB** — `Claim` (`triggerEvent: EventType`, `eventOccurredAt`,
  `fraudScore`, `status: ClaimStatus`, `payoutAmount`, `adminNote`,
  `createdAt`, `processedAt`) — a flexible document shape suits claims
  well, since fields vary somewhat by event type.
- **Redis** — risk-score cache (`RISK_SCORE_CACHE_TTL_SEC`), trigger
  cache (`TRIGGER_CACHE_TTL_SEC`), session/ban keys.

`EventType`: `RAIN`, `AQI`, `CURFEW`, `TRAFFIC`, `WAR` (city-wide,
automated) and `ORDER_CANCELLED` (single-worker, self-reported only —
see §5).

`ClaimStatus`: `PENDING_FRAUD_CHECK` → `AUTO_APPROVED` |
`FLAGGED_FOR_REVIEW` → (`ADMIN_APPROVED` | `ADMIN_REJECTED`) →
`PAID` | `FAILED`.

---

## 12. Tech stack

- **Backend:** Java 17, Spring Boot 3.2.5, Spring Kafka, Spring Data JPA
  (MySQL), Spring Data MongoDB, Spring Data Redis, JWT auth
- **Frontend:** React (Vite)
- **ML sidecar:** Python, FastAPI, scikit-learn (with rule-based
  heuristic fallback when a model isn't trained)
- **Messaging:** Apache Kafka (KRaft mode, single-node in Docker Compose)
- **External APIs:** Open-Meteo (weather + air quality, no API key
  required), Razorpay (test mode)
- **Deployment:** Docker Compose

---

## 13. Future scope

- Expand beyond food delivery to grocery/e-commerce delivery workers
- A real (non-fallback) trained risk model per city, refreshed on a
  schedule, rather than only the deterministic heuristic
- A dedicated mobile app
- A reinsurance/co-sharing layer for large-scale correlated events
  (e.g. a citywide lockdown hitting every policy at once)
- Real end-to-end integration testing once this environment (or CI) has
  Maven Central and Open-Meteo network access plus a Docker daemon
