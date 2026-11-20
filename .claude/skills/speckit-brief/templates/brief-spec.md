# Spec Brief: {Feature name}

The 20% of [spec.md](spec.md) you need. It covers **what** we build, not how.
IDs (`FR-xxx`, `SC-xxx`) lead to the full text.

> **Before you read:** write 3 lines on how *you* think {the feature} should work.
> Compare after. Where you differ is the part worth learning.

## 1. One breath

<!-- 2–3 sentences. Who does what, and what they get. -->
{Summary}

## 2. User flow

<!-- 4–6 numbered steps of the main path, each with its FR IDs. Branches as sub-bullets.
     Then one line of side paths. -->
1. {Step}. `FR-xxx`

Side paths: {…}. `FR-xxx`

## 3. Rules (MUST)

<!-- About 8–14 rows. Only rules whose breakage causes a real bug or hole. Merge duplicates. -->
| ID | Rule | Why |
|---|---|---|
| FR-xxx | {rule} | {why, a few words} |

## 4. Out of scope

<!-- One line listing what is not built. -->
{…}

## 5. Decisions made: check you agree

<!-- Defaults and choices a human should confirm. Put open [NEEDS CLARIFICATION] items first,
     under "**Open:**". -->
| Decision | Value | Change if… |
|---|---|---|
| {decision} | {value} | {when to revisit} |

## 6. Quiz (answer before opening the answers)

<!-- 3–5 concrete "what happens if" questions. -->
1. {Question}

<details><summary>Answers</summary>

1. {Answer}. (`FR-xxx`)

</details>
