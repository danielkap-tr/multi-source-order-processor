# Design

## 1. Architecture

The solution is a small, dependency-light Java library with a thin CLI on top. The core
is a **linear per-order pipeline** built around a single internal model.

```
                 ┌─────────────┐
   Source A JSON ─┤ SourceA     ├─┐
                 │ OrderParser │ │
                 └─────────────┘ │      ┌───────────┐   ┌──────────────────┐   ┌───────────────────┐
                                 ├────► │ Canonical │──►│ CanonicalOrder   │──►│ TargetOrderMapper │──► TargetOrder ──► TargetSystemClient
                 ┌─────────────┐ │      │  Order    │   │   Validator      │   │ (enrichment +     │                    (HTTP / queue / file)
   Source B JSON ─┤ SourceB     ├─┘      └───────────┘   └──────────────────┘   │  value calc)     │
                 │ OrderParser │                                               └───────────────────┘
                 └─────────────┘                                                        │
                        ▲                                              any failure ─────┘
                        │                                                       │
                  SourceRouter                                                  ▼
             (explicit source or                                        DeadLetterSink
              auto-detect by shape)                                (original payload + reason)
```

Key components (package `com.example.orderprocessing`):

| Package        | Responsibility |
|----------------|----------------|
| `model`        | `CanonicalOrder` (internal), `TargetOrder` (output contract). Immutable records. |
| `source`       | One `OrderParser` per source system + `SourceRouter` for explicit or shape-based routing. **The only code that knows a source's wire format.** |
| `enrichment`   | `CountryReferenceService`: country code → full name + currency. |
| `processing`   | `CanonicalOrderValidator`, `TargetOrderMapper` (business rules), `OrderProcessor` (orchestration). |
| `delivery`     | `TargetSystemClient` and `DeadLetterSink` interfaces + in-memory implementations. |
| `io`           | Central Jackson configuration. |
| `cli`          | Batch entry point. |

### Why a canonical model

The central decision is to convert **both** source formats into one internal
`CanonicalOrder` immediately, and have every downstream step (validation, enrichment,
mapping, delivery) work only against that model.

- Business rules are written **once**, not once per source.
- Onboarding a third source system = **one new `OrderParser` + one line in `SourceRouter`**. Nothing else changes.
- The source formats and the target format can evolve independently of each other.

The customer-name normalisation (System A: one field, System B: two fields) is a
*source-specific* concern, so it happens **inside the parsers** — `CanonicalOrder` already
carries a single `customerFullName`.

## 2. How an order is processed (source → target)

1. **Ingest** – the CLI reads order JSON from files/directories. Each file is a single
   order or an array. (In production this is a message consumer — see §7.)
2. **Route + parse** – `SourceRouter` picks the parser: either the explicitly declared
   source, or by inspecting the JSON shape (`orderId` ⇒ A, `order_number` + `customer{}` ⇒ B).
   The parser reads every required field with a clear error message if one is missing or
   malformed, and produces a `CanonicalOrder`. System B's `first_name` / `last_name` are
   joined here.
3. **Validate** – `CanonicalOrderValidator` enforces source-independent business
   invariants: non-blank references/name/product, `quantity > 0`, `unitPrice >= 0`,
   timestamp present. All violations for an order are reported together.
4. **Enrich + map** – `TargetOrderMapper`:
   - resolves the country code to **full country name** and **currency** via
     `CountryReferenceService`;
   - computes **`totalOrderValue = unitPrice × quantity`**, rounded **HALF_UP to 2
     decimals**, from the full-precision unit price (so repeated rounding can't accumulate);
   - assembles the `TargetOrder` record.
5. **Deliver** – `TargetSystemClient.deliver(targetOrder)`. Implementations are expected to
   be idempotent on `orderReference`.
6. **Failure** – any exception in steps 2–5 for a single order is caught by
   `OrderProcessor`, which sends `{original payload, source, reason}` to the
   `DeadLetterSink` and moves on. **One bad order never fails the batch.**

## 3. Important technical decisions

| Decision | Why |
|----------|-----|
| **Canonical internal model** | Isolates the number of sources from the business logic — see §1. |
| **`BigDecimal` for all money**, never `double` | Exact decimal arithmetic; no `0.1 + 0.2` surprises. Rounding policy is explicit (`HALF_UP`, scale 2). |
| **Compute total from full-precision unit price, then round once** | Avoids compounding a rounded unit price across a large quantity. |
| **`LocalDateTime` for timestamps, passed through unchanged** | Both sample inputs and the target example have no zone/offset. Re-serialised in the same `2026-09-01T10:30:00` form. Assumption documented in §6. |
| **Parsers own field extraction & shape detection** | New source ⇒ new parser only. `SourceRouter.canParse` keeps detection next to the format knowledge. |
| **Explicit source preferred; auto-detect as fallback** | In production the source is known from the transport (topic/queue/path). Auto-detect exists so a mixed file can be replayed and for convenience. |
| **Dead-letter with original payload, not just a log line** | Failed orders must be recoverable and replayable without data loss. |
| **Per-order isolation, `RuntimeException`-based** | ~50k orders/day from unpredictable sources: a malformed order is *expected*, not exceptional. The batch must be resilient. |
| **Country/currency as a small injectable service** | Today an in-memory table; swappable for a reference-data service + cache without touching the pipeline. |
| **Interfaces for both sinks** (`TargetSystemClient`, `DeadLetterSink`) | The pipeline is transport-agnostic; tests use in-memory doubles; production injects HTTP/queue clients. |
| **Records + `System.Logger`, only Jackson as a runtime dep** | Small, readable, fast to build, easy to drop into any framework (Spring, Quarkus, plain `main`). |

## 4. Alternatives considered

- **Map each source directly to the target format** (no canonical model). Rejected: every
  business rule and every future target change would have to be duplicated per source, and
  it gets worse with each new source system.
- **JSON Schema / a schema registry per source, with generated POJOs.** Reasonable at
  larger scale; too heavy for two known shapes and a 4-hour budget. Noted as an evolution
  path (§7).
- **Jackson polymorphic deserialization (`@JsonTypeInfo`) to pick the source type.** The
  two payloads have no discriminator field; shape-sniffing in one place (`SourceRouter`)
  is clearer and easier to test.
- **A streaming framework (Kafka Streams / Flink / Spring Cloud Stream) now.** Right answer
  for production, wrong answer for the assignment — it would bury the business logic in
  infrastructure. The core is deliberately framework-free so it can be embedded in any of
  them (§7).
- **Fail the whole batch on the first bad order.** Unacceptable for multi-source,
  unpredictable input at this volume.
- **`double` / `float` for money.** Never for currency.
- **Bean Validation (`jakarta.validation`) annotations.** Nice, but adds a dependency and
  an implementation for a handful of checks; a plain validator is smaller and clearer here.

## 5. Assumptions

1. **Timestamps are wall-clock local times without a zone** (as in every example) and the
   target wants them re-emitted unchanged. If they are really UTC instants, switch
   `CanonicalOrder.orderTimestamp` to `OffsetDateTime`/`Instant` — a one-field change.
2. **One order = one product line.** Both example structures have a single product; the
   model reflects that. Multi-line orders would need a `List<LineItem>` and a defined
   `totalOrderValue` semantic (sum of lines).
3. **`orderReference` is globally unique across both sources** and is the idempotency key
   for delivery. The samples use non-overlapping prefixes (`ORD-1xxxx` / `ORD-2xxxx`).
4. **The country reference table is authoritative and complete for valid orders.** An
   unknown code is a data problem → dead-letter, not a silent pass-through.
5. **Currency is derived from country**, per the reference table. Real e-commerce currency
   can depend on the store/pricing context, not the ship-to country (open question §6).
6. **`unitPrice` in the target is the source unit price** (rounded for display to 2 dp),
   not a recalculated value.
7. **Source A `country` and Source B `country_code` are the same kind of 2-letter code.**
   Lookup is case-insensitive.
8. **No ordering or exactly-once guarantee is required** beyond idempotent delivery.
9. **Input character set is UTF-8.**

## 6. Questions I'd want answered before a production build

1. **Transport** – how do orders actually arrive? Kafka topic per source? One shared
   queue? HTTP push? Batch file drop? This determines the ingestion adapter and the
   delivery/ack semantics.
2. **Delivery semantics** – does the target need at-least-once (with idempotency) or
   exactly-once? Is ordering per customer/product significant?
3. **Timestamps** – zone/offset semantics; does the target want them normalised to UTC?
4. **Currency** – is "currency = f(ship-to country)" correct, or does it come from the
   order / pricing context / a currency field the sources will add later?
5. **Rounding** – is `HALF_UP` to 2 decimals the business rule for *every* currency
   (some are 0- or 3-decimal)? Where is the canonical rounding policy owned?
6. **Reference data** – who owns the country/currency mapping, how is it distributed, how
   often does it change, and what's the SLA if a new country shows up before the table is
   updated?
7. **Bad-order policy** – dead-letter and alert? Auto-retry with backoff for transient
   failures vs. park permanently for data errors? Who monitors and replays the DLQ?
8. **Volume & latency** – 50k/day is tiny on average (~0.6/s) but "throughout the day"
   with an unknown distribution implies spikes. What's the peak, and the end-to-end
   latency target?
9. **Duplicates** – can a source re-send the same `orderId`? Is `orderReference` truly
   unique across A and B, or do we need a `(source, id)` composite key?
10. **Schema evolution** – how will we be told when a source changes its payload? Is there
    a schema registry / contract test we can hook into?
11. **PII / compliance** – customer names and IDs: retention, encryption at rest, what may
    be logged, what may sit in a dead-letter store and for how long.
12. **Partial data** – is an order with a missing optional field droppable, or must it be
    delivered with a default / flagged for manual fix?

## 7. Evolution

**If requirements change (more rules, more target fields):**
The canonical model absorbs it. New enrichment steps become small services injected into
(or composed before) `TargetOrderMapper`; the pipeline shape doesn't change. If rules grow
a lot, split `TargetOrderMapper` into an ordered list of `OrderEnricher`s.

**If the number of source systems grows:**
Add an `OrderParser` per source and register it — `O(1)` change to existing code. If
sources become numerous or externally owned, move to **explicit source declaration only**
(drop shape-sniffing), introduce a **schema registry + contract tests** per source, and
consider generating the parser DTOs from each source's schema. Consider a plugin/SPI
mechanism so a source can be added without recompiling the core.

**If volume grows (10× – 1000×):**
- Wrap the same `OrderProcessor` in a **message consumer** (Kafka/SQS/Pub-Sub). One topic
  or queue per source ⇒ source is known, no detection needed.
- Scale **horizontally**: the processor is stateless; partition by `orderReference` (or
  `customerId`) for ordering where needed.
- Make delivery **async with retry + backoff**; separate a **retry** dead-letter (transient)
  from a **parking** dead-letter (data errors) with an operator replay tool.
- Add **idempotency storage** (e.g. a processed-reference set with TTL) if the target
  can't dedupe itself.
- **Observability**: per-source throughput/error-rate metrics (the code already tracks
  `deliveredBySource`), tracing from ingest to delivery, alerting on DLQ growth and on a
  source's error rate crossing a threshold.
- **Reference data**: load the country/currency table from a service with a local cache and
  scheduled refresh; fail closed (dead-letter) on a genuine miss.
- Only reach for a **stream-processing framework** (Kafka Streams / Flink) if we actually
  need windowed aggregation, joins or stateful enrichment — the business logic stays in
  the framework-free core either way.

## Testing

`mvn test` — 33 tests. What's covered and why:

| Area | Tests | Rationale |
|------|-------|-----------|
| **Source A parser** | example payload, unit price as JSON string, missing field, non-numeric quantity, shape detection | The wire contract for source A; the most likely place for input surprises. |
| **Source B parser** | example payload + name join, missing last name, no name at all, missing `item` object | Nested structure + the name-normalisation rule. |
| **Source routing** | auto-detect A, auto-detect B, explicit source, no-match | The dispatch logic that makes multi-source work. |
| **Country reference** | all three codes, case-insensitivity, unknown code throws | Enrichment correctness + the failure path. |
| **Target mapping** | **the exact documented target example**, HALF_UP rounding, scale-is-2, unknown country rejected | The business rules: name, country/currency, order-value calculation. |
| **Validation** | valid order, zero/negative quantity, negative price, multiple errors at once | Business invariants. |
| **Pipeline (end-to-end)** | mixed A+B batch delivered, unknown country dead-lettered without failing the batch, malformed order keeps its payload, idempotent delivery | The resilience and isolation guarantees. |
| **Target JSON serialization** | field names/nesting/order match the contract, money in plain 2-dp decimal, timestamp as `ISO_LOCAL_DATE_TIME` | The output contract as bytes. |
| **CLI** | both sources in one run → correct delivered + dead-letter files + exit code; directory input | The delivered artefact users actually run. |

**Not covered** (conscious trade-offs for the time budget): concurrency/thread-safety of a
parallel run, performance/load, malformed-JSON-at-file-level beyond the happy path,
property-based tests for the money maths, and anything transport-specific (there is no
transport yet).
