---
name: "speckit-brief"
description: "Write the short 80/20 review briefs for the current feature: brief-spec.md (what, from spec.md) and brief-design.md (how, from research/data-model/contracts/plan). Also runs an interactive quiz on a brief. Runs automatically after speckit-specify, speckit-clarify and speckit-plan via .specify/extensions.yml."
argument-hint: "[spec | design | quiz [spec|design]] (empty = write whichever brief is missing or stale)"
user-invocable: true
disable-model-invocation: false
---

## User Input

```text
$ARGUMENTS
```

## Purpose

The speckit docs are written for the AI that implements them. They are too long for a human to
review well, so the human skims and approves (cognitive surrender). A brief is the human's
version: about 20% of the words, holding 80% of what matters. It is built to make review
**active**: the reader predicts first, checks decisions, and answers a quiz.

The full docs stay as they are. The brief never replaces them. It points into them by ID.

## 1. Find the feature

Run `.specify/scripts/bash/check-prerequisites.sh --json --paths-only` from the repo root and
read `FEATURE_DIR`. If it fails, read `.specify/feature.json` (`feature_directory`). If neither
works, stop and tell the user no active feature was found.

## 2. Pick the mode

| Argument | Do |
|---|---|
| `spec` | Write `brief-spec.md` |
| `design` | Write `brief-design.md` |
| `quiz`, `quiz spec`, `quiz design` | Run the quiz (section 6). Bare `quiz` uses `brief-design.md` if it exists, else `brief-spec.md` |
| empty (this is how hooks call it) | Write each brief that is **missing or stale**, in order spec then design |

**Stale** means a source file is newer than the brief (compare with `stat -c %Y`):
- `brief-spec.md` sources: `spec.md`.
- `brief-design.md` sources: `plan.md`, `research.md`, `data-model.md`, `contracts/*`, and
  `brief-spec.md`.

Skip `brief-design.md` when `plan.md` does not exist yet. If nothing is missing or stale, say so in
one line and stop.

When a brief already exists and is being rewritten, keep any lines the user added by hand
(text not in the template) unless the source change makes them wrong.

## 3. Read the sources fully

Read every source file in full before writing. Do not write from skimming. That is the very
failure this skill exists to prevent.

- Spec brief: `spec.md` (including Clarifications, Edge Cases, Assumptions).
- Design brief: `plan.md`, `research.md`, `data-model.md`, every file in `contracts/`,
  `brief-spec.md`, and `tasks.md` if present (for planned test names).

## 4. Write the brief

Start from the template in this skill's folder:
- `templates/brief-spec.md` → `FEATURE_DIR/brief-spec.md`
- `templates/brief-design.md` → `FEATURE_DIR/brief-design.md`

Keep every section heading, in order. Replace all `{…}` placeholders and drop the guidance
comments. A finished example lives in `specs/001-email-code-auth/` if one is useful.

### Budget

| Brief | Target | Hard cap |
|---|---|---|
| brief-spec.md | ≤ 25% of `spec.md` words, about 1 page | 700 words |
| brief-design.md | ≤ 20% of the design sources' words | 900 words |

Check with `wc -w` after writing. If you're over the cap, cut. Never squeeze by making lines cryptic.

### What makes the 20%

Keep:
- **Rules that would cause a real bug, security hole or wrong behaviour if broken.** These
  are the MUST rules, the invariants, the concurrency and privacy guarantees.
- **Decisions with a trade-off**, and the trade-off accepted.
- **Anything surprising**, e.g. a key chosen differently from the obvious one.

Drop:
- Restated background, template boilerplate, the full list of every acceptance scenario,
  field-by-field DTOs, things obvious from a name.
- Any rule that only repeats another one. Merge them under one line with both IDs.

### Line rules

- One idea per line or table row. Every rule or choice carries its source ID (`FR-xxx`,
  `SC-xxx`, `Rxx`) and a short **why**.
- Use the real IDs from the sources. Never invent IDs. If a needed point has no ID, cite the
  section name, e.g. `(Edge cases)`.
- Plain, short words. Name tables, endpoints, classes and config keys exactly.
- **Design brief, code and test names:** for each class, method or test you name, check
  whether it exists in the repo (Grep/Glob). Existing → use the real name. Not built yet → use
  the planned name from `plan.md`/`tasks.md` and add `(planned)`. Never present a guess as
  real.

### Decisions section

List only things a human should actively agree to: defaults chosen by the AI, accepted
trade-offs, scope cuts. Ask each one as a question or as a "Change if…" row. If the spec still
has `[NEEDS CLARIFICATION]` markers, put them first under **Open**.

### Quiz section

3–5 "what happens if…" questions with short answers in a `<details>` block. Good questions:
- test a rule through a concrete scenario (two tabs, attacker, timeout, failure);
- have one clear answer found in the sources, cited by ID;
- can't be answered by just repeating a heading.

## 5. Report

In chat, give one line per brief written: its path, its word count against the source word
count, and the number of open decisions. Do not paste the brief into chat. End with:

> Before reading: write your 3-line guess. After: run `/speckit-brief quiz`.

## 6. Quiz mode (interactive)

Builds the reader's own understanding. It's not a test of the AI.

1. Read the chosen brief and its sources.
2. Ask **one question at a time**: the brief's quiz questions first, then 1–2 new ones from
   the sources that the brief doesn't cover. Don't show the answer before the user replies.
3. After each reply, say right, partly right, or wrong, in 1–2 lines, citing the ID. When the
   user is wrong, point them to the brief line to re-read.
4. If the user's answer is reasonable but the sources don't settle the question, call it a
   **spec gap**: the sources are missing something, not the user. Note it.
5. At the end: score, the IDs to re-read, and a list of spec gaps. For each gap, offer to fix it
   with `/speckit-clarify` (spec) or by updating `research.md` (design). Don't change the
   sources without the user agreeing.

Stop when the user says stop.
