# Design Brief: {Feature name}

The 20% of [research.md](research.md), [data-model.md](data-model.md),
[contracts/](contracts/) and [plan.md](plan.md). It covers **how** we build it. Read
[brief-spec.md](brief-spec.md) first. IDs: `Rxx` = research decision, `FR-xxx` = spec rule.

> **Before you read:** for each spec rule, guess how you'd enforce it (DB? lock? config?).
> Compare after.

## 1. Shape

<!-- 2–3 lines: the parts (frontend, backend, DB, external services) and how they talk. -->
{…} `Rxx`

## 2. Flow → code

<!-- One row per user-flow step or endpoint. Code = Class.method plus a short "→" chain of
     what it does. Real names if they exist, else planned names marked (planned). -->
Backend root: `{path}`

| Step | Endpoint | Code | IDs |
|---|---|---|---|
| {step} | `{METHOD /path}` | `{Class.method}`: {a → b → c} | Rxx |

## 3. Tables

<!-- New or changed tables only. One row each. -->
| Table | Holds | Key point |
|---|---|---|
| `{table}` | {what} | {constraint, states, or lifetime} |

## 4. Big choices

<!-- Decisions with a real alternative. "none" is a fine trade-off when true. -->
| Choice | Instead of | Trade-off we accept | ID |
|---|---|---|---|
| {choice} | {alternative} | {cost} | Rxx |

## 5. Risks → tests that guard them

<!-- The risky boundaries (concurrency, security, migration, external calls) and the test
     covering each. Real test names if they exist, else (planned). Write "none yet" if a
     risk has no test; that is a finding. -->
Tests are in `{path}`.

| Risk | Test |
|---|---|
| {what could go wrong} | `{TestClass.method}` |

## 6. Decisions: check you agree

<!-- 2–4 questions about accepted trade-offs or defaults. -->
1. {Question}? (`Rxx`)

## 7. Quiz

<!-- 3–5 "why / what happens if" questions about the design. -->
1. {Question}

<details><summary>Answers</summary>

1. {Answer} (`Rxx`).

</details>
