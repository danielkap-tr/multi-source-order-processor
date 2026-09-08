# AI Usage

I used an AI coding assistant (Claude, via the Claude Code CLI) throughout this
assignment: to draft the initial project structure and implementation, to argue through
design trade-offs, and to generate the first pass of the tests and docs. I reviewed,
ran, and adjusted everything that follows; I'm responsible for the final content.

Below are the meaningful places where AI changed the outcome.

---

## 1. Core architecture: canonical model vs. per-source mapping

**Problem.** Two source formats today, "not fixed" distribution, and an explicit hint that
the number of source systems could grow. How should the code be structured so that adding
a source doesn't mean rewriting the business rules?

**AI recommendation.** Introduce a single internal `CanonicalOrder` model. Each source
gets one `OrderParser` that converts its wire format into the canonical model; validation,
country/currency enrichment, order-value calculation and target mapping all operate on the
canonical model only. Adding a source = one new parser + one registration line.

**Decision: accepted.** This is the standard anti-corruption-layer pattern and it directly
answers the "number of source systems increased" question in the brief. I pushed one
refinement: the customer-name normalisation (A has one name field, B has two) belongs
*inside the parsers*, so `CanonicalOrder` already exposes a single `customerFullName` and
no downstream code has to care which source it came from.

---

## 2. `totalOrderValue` rounding strategy

**Problem.** The rule is `quantity × unit price`. Money must be exact and the target
example shows 2 decimal places (`251.00`). When and how do we round?

**AI recommendation (first pass).** Round the unit price to 2 decimals, then multiply by
quantity.

**Decision: modified.** With a rounded unit price and a large quantity, the rounding error
is multiplied too (e.g. a unit price of `12.345` over 10 units: `12.35 × 10 = 123.50`
vs. the truer `123.45`). I changed `TargetOrderMapper` to compute the total from the
**full-precision** unit price and round **once**, `HALF_UP`, to scale 2. The displayed
`unitPrice` is rounded separately, for presentation only. I added a test
(`total_order_value_is_rounded_half_up_to_two_decimals`) that pins this behaviour, and
listed "is HALF_UP/2dp the rule for every currency?" as an open question in DESIGN.md.

---

## 3. Money losing its decimals in the JSON output (`251.00` → `251`)

**Problem.** The first CLI run wrote `"totalOrderValue": 251` and `"unitPrice": 80`, not
`251.00` / `80.00`, even though the values were `BigDecimal`s with scale 2.

**AI recommendation.** It first diagnosed the cause correctly — the CLI serialised via
`ObjectMapper.valueToTree(...)`, and Jackson's `DecimalNode` strips trailing zeros
(`JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES`, on by default). It then suggested
disabling that feature on the mapper.

**Decision: rejected the suggested fix, kept the diagnosis.** Disabling the node feature
via `ObjectMapper` didn't compile cleanly (it's a datatype feature, not a
`SerializationFeature`) and, more importantly, the `valueToTree` round-trip was
unnecessary in the first place. I removed it and serialised the value objects directly
with `WRITE_BIGDECIMAL_AS_PLAIN`. Output is now `251.00` and a
`TargetOrderJsonTest` assertion locks the on-the-wire format.

---

## 4. How to detect which source a JSON payload came from

**Problem.** For a mixed input file, decide per order whether it's Source A or B.

**AI options presented.** (a) Jackson polymorphic deserialization with
`@JsonTypeInfo`/`@JsonSubTypes`; (b) a dedicated component that inspects the JSON shape and
picks a parser.

**Decision: chose (b), rejected (a).** The two payloads share no discriminator field, so
`@JsonTypeInfo` would need a custom type resolver anyway, and the type-selection logic
would be scattered across annotations. I put shape detection in `SourceRouter` /
`OrderParser.canParse`, right next to the per-source format knowledge, where it's trivial
to unit-test (`SourceRouterTest`). AI also correctly noted that in production the source is
usually known from the transport (topic/queue), so explicit routing is the primary path
and detection is a fallback — that framing went into DESIGN.md.

---

## 5. Error handling for ~50k unpredictable orders/day

**Problem.** What happens when a single order is malformed or has an unknown country code?

**AI recommendation.** Unchecked (`RuntimeException`) failures per order, caught by the
orchestrator, with the order routed to a dead-letter sink so one bad record never fails
the batch.

**Decision: accepted, with an addition.** I agreed on unchecked exceptions (a bad order is
*expected* at this volume, not an exceptional condition) and per-order isolation. I added
the requirement that the dead-letter record keep the **original raw payload** plus the
reason and detected source — not just a log line — so failures are inspectable and
replayable without data loss. `OrderProcessorTest` verifies both the isolation and that
the payload is preserved.

---

## 6. A bug AI introduced that I caught

Worth recording as an example of *validating* AI output. The assistant wrote
`import java.util.System.Logger;` in `OrderProcessor` — `System` is in `java.lang`, not
`java.util`, so the build failed immediately. Fixed by using the fully-qualified
`System.Logger` / `System.Logger.Level`. The compiler caught it; it's a reminder that
AI-generated imports and API names need to actually be run, not just read.

---

## Where AI did most of the mechanical work (reviewed, not blindly accepted)

- First draft of all `OrderParser` implementations and the `JsonFields` helper.
- First draft of the 33 tests; I adjusted assertions (e.g. comparing `BigDecimal` by
  `compareTo`, not `equals`, to avoid scale-sensitivity) and added the idempotency and
  mixed-batch cases.
- First draft of this README/DESIGN structure, which I edited for accuracy against the
  final code.
- Boilerplate: `pom.xml`, `.gitignore`, sample JSON files.
