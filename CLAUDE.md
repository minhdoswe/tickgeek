# Working rules for Claude

## Write code only after the user types /impl

- Create, edit or delete code (source, tests, migrations, build files, app config) only when
  the user invokes `/impl` (defined in `.claude/skills/impl/SKILL.md`). Nothing else counts as
  permission except /speckit-implement.
- Without `/impl`, reply with an explanation or a proposed design, then stop. End a proposal by
  saying it can be implemented with `/impl`.
- Permission from `/impl` covers that one request only. Afterwards this rule applies again.
- When proposing, call out anything that reverses a recorded decision (`specs/*/research.md`,
  `plan.md`, the constitution) or changes the database schema, so the user can decide on it
  before typing `/impl`.
- Files that are not code (docs, specs, `CLAUDE.md`, `.claude/`) may be changed when the user
  asks for that change directly.

## Ask before making design decisions

- When fixing review findings, or when a speckit step (specify, clarify, plan, tasks) faces a
  choice with more than one valid option, ask the user: give the options, their trade-offs and
  a recommendation. Do not pick a default yourself.
- This covers every finding whose fix changes behaviour, performance, the database schema or a
  recorded decision, not only the ones that look like design choices.
- Mentioning a decision in a summary after making it does not count as asking.

## Say "I don't know" when you don't know

- If you are unsure of a fact (an API, a library's behaviour, a GitHub or CI setting, why
  something failed), say so plainly instead of guessing. "I don't know" is an acceptable answer.
- Separate what you checked (code you read, output you ran) from what you are inferring, and
  say which is which.
- When you can't verify something, say what would confirm it (a log to read, a command to
  run, a doc to check) rather than presenting a guess as fact.

## Explanations

- Answer the WHY/WHAT (purpose, role in the system) before the HOW, unless I ask for mechanics.
- Start at normal developer level. If I say "I don't get it", simplify ONCE for that answer,
  then go back to normal depth. Don't stay in ELI5 mode.
- Don't invent hypothetical bug scenarios. Only claim a bug if you can point to the exact code
  path, and say how confident you are.
- Keep Javadoc/comments short. Explain purpose, and don't paste long spec citations unless
  asked.
