---
name: impl
description: "The user's go-ahead to write code: implements the proposal agreed in this conversation, or the change given as arguments."
argument-hint: "Optional: what to implement, if not the last agreed proposal"
user-invocable: true
disable-model-invocation: true
---

The user typed `/impl`, so you may now write and change code for this one request.

What to implement: $ARGUMENTS

If that is empty, implement the proposal most recently agreed in this conversation. If no
proposal was agreed, or it is unclear which one is meant, ask instead of guessing.

- Stay within that scope. If the work turns out to need something the proposal did not cover
  (a schema change, reversing a decision in `specs/`, another module), stop and ask first.
- This permission covers this request only. Once it is done, the rule in `CLAUDE.md` applies
  again: no more code changes until the next `/impl`.
- When finished, list the files you changed and the tests you ran, with their results.
