---
name: taste-promote
description: "Promote chosen rules from the taste/ folder into the project constitution through /speckit-constitution, then mark them Promoted in taste/."
argument-hint: "Optional: which taste entries to promote (file and heading)"
user-invocable: true
disable-model-invocation: true
---

The user wants to move personal taste rules from `taste/` into
`.specify/memory/constitution.md`, so they become project rules every plan and review is checked
against.

User input: $ARGUMENTS

## 1. Collect candidates

- Read `taste/README.md` and every `taste/*.md` topic file it indexes.
- List every entry that has no `**Promoted**` line: its file, its `###` heading, and its Works
  line in one sentence.
- If there are none, say so and stop.

## 2. Choose entries and wording with the user

- If the user input names entries, use those. Otherwise ask which ones to promote (multi-select).
- Read `.specify/memory/constitution.md`. For each chosen entry, propose:
  - **Target**: an existing principle or section (by number or name), or a new one.
  - **Wording**: one or more MUST/SHOULD bullets in the constitution's style. Make them a
    general project rule: drop "this user" and conversation details, and keep the Works example
    only if it fits the section.
  - **Rationale**: from the entry's Why.
  - **Expected bump**: per the constitution's Governance section. PATCH for wording, MINOR for a
    new or materially expanded principle or section, MAJOR for removing or redefining one.
- Point out any conflict with an existing principle.
- Ask the user to confirm or change each target and wording. Do not continue until they confirm
  (per `CLAUDE.md`: ask before making design decisions).

## 3. Amend through /speckit-constitution

- Invoke the `speckit-constitution` skill with arguments that contain, for each confirmed entry:
  the target, the exact wording, and the rationale. Add: "Source: taste/<file> — <heading>."
- Let it do its full flow: version bump, Last Amended date, Sync Impact Report and dependent
  artifact checks. Do not edit `constitution.md` yourself.
- If it fails, or the user cancels or changes the wording, apply step 4 only to the entries that
  actually landed, with their final wording.

## 4. Mark entries Promoted

Under each promoted entry in its taste file, add one line and keep everything else:

```markdown
- **Promoted**: constitution vX.Y.Z, <principle or section> (YYYY-MM-DD)
```

Use the new version from the constitution footer and today's date.

## 5. Report

- The entries promoted, with their target in the constitution.
- The old version → the new version.
- The taste files changed.
- A reminder: Governance requires the amendment to go in a pull request that states the change
  and its rationale.
