# Multi-Source Order Processor

Processes orders that arrive from two external source systems (**A** and **B**), each with
its own JSON structure, applies the required business rules, and produces orders in the
single JSON contract expected by the target system.

- **Source A** – flat JSON (`orderId`, `customerName`, `country`, …)
- **Source B** – nested JSON (`order_number`, `customer.{first_name,last_name,country_code}`, `item.{sku,units,price}`)
- **Target** – `orderReference`, `customer.{customerReference,fullName,country}`, `product`, `totalOrderValue`, `currency`

See [DESIGN.md](DESIGN.md) for the architecture and the reasoning behind the technical
decisions, and [AI_USAGE.md](AI_USAGE.md) for how AI tools were used.

---

## Requirements

- **JDK 21+**
- **Maven 3.9+**

## Build

```bash
mvn clean package
```

This compiles the code, runs all tests, and produces a runnable fat JAR at
`target/order-processor.jar`.

To build without tests: `mvn -DskipTests package`.

> **Note on constrained environments:** if Maven fails with an out-of-memory error, cap
> the heap and run the tests in-process:
> ```bash
> MAVEN_OPTS="-Xmx256m -XX:+UseSerialGC" mvn -DforkCount=0 clean package
> ```

## Run

```bash
java -jar target/order-processor.jar \
  --input  <file-or-directory> [--input ...] \
  --output <delivered.json> \
  [--errors <dead-letters.json>] \
  [--source A|B|auto]
```

| Option          | Meaning                                                                                          |
|-----------------|-------------------------------------------------------------------------------------------------|
| `-i, --input`   | An order JSON file, or a directory of `*.json` files. Repeatable. Each file may contain a single order object or an array of orders. |
| `-o, --output`  | File to write the delivered target-system orders (a JSON array).                                 |
| `-e, --errors`  | File to write dead-lettered orders together with the failure reason (a JSON array). Optional.    |
| `-s, --source`  | Declare the source system for every input. Default: **auto-detect per order** from the JSON shape. |

Exit codes: `0` all orders delivered · `1` completed but ≥1 order dead-lettered · `2` bad arguments.

## Demonstrate processing from both source systems

Sample input for each source is in [`samples/`](samples/):

- [`samples/source-a-orders.json`](samples/source-a-orders.json) – 3 Source A orders (one with an unknown country, to show dead-lettering)
- [`samples/source-b-orders.json`](samples/source-b-orders.json) – 2 Source B orders

Process **both sources in one run** (source auto-detected per order):

```bash
java -jar target/order-processor.jar \
  -i samples/source-a-orders.json \
  -i samples/source-b-orders.json \
  -o out/delivered.json \
  -e out/dead-letters.json
```

Console output:

```
Loaded 5 order(s) from 2 input path(s)

Processing summary
  submitted=5, delivered=4, failed=1, deliveredBySource={SOURCE_A=2, SOURCE_B=2}
  delivered -> .../out/delivered.json
  dead-letters -> .../out/dead-letters.json
  ! [SOURCE_A] Unknown country code 'FR' - no name/currency mapping available
```

## View / verify the target-system output

`out/delivered.json` contains the delivered orders in the target contract, e.g. the
Source A example order becomes:

```json
{
  "orderReference" : "ORD-10001",
  "customer" : {
    "customerReference" : "CUST-501",
    "fullName" : "John Smith",
    "country" : "United States"
  },
  "orderTimestamp" : "2026-09-01T10:30:00",
  "product" : {
    "code" : "P100",
    "quantity" : 2,
    "unitPrice" : 125.50
  },
  "totalOrderValue" : 251.00,
  "currency" : "USD"
}
```

and the Source B example order becomes:

```json
{
  "orderReference" : "ORD-20001",
  "customer" : {
    "customerReference" : "CUST-842",
    "fullName" : "Jane Miller",
    "country" : "Germany"
  },
  "orderTimestamp" : "2026-09-01T11:15:00",
  "product" : {
    "code" : "P200",
    "quantity" : 3,
    "unitPrice" : 80.00
  },
  "totalOrderValue" : 240.00,
  "currency" : "EUR"
}
```

`out/dead-letters.json` keeps the **original payload** plus the reason, so a failed order
can be inspected, fixed and replayed:

```json
[ {
  "rawOrder" : { "orderId" : "ORD-10003", "country" : "FR", "...": "..." },
  "source" : "SOURCE_A",
  "reason" : "Unknown country code 'FR' - no name/currency mapping available"
} ]
```

## Test

```bash
mvn test
```

33 tests covering the source parsers, source detection/routing, country reference lookup,
the business-rule mapping (name join, country/currency enrichment, order-value rounding),
validation, the end-to-end pipeline (mixed batch, dead-lettering, idempotency) and the
CLI. See [DESIGN.md](DESIGN.md#testing) for what is and isn't covered.
