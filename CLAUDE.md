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

## Load the Postgres skill for database work

- Before proposing, reviewing or writing anything that touches Postgres, load the
  `supabase-postgres-best-practices` skill (`.claude/skills/`) and follow its rules. That covers
  Flyway migrations, entity or column changes, repository methods (derived names, `@Query`,
  native SQL), transactions and locking, and datasource or Hikari config.
- Name the rule file (e.g. `references/lock-skip-locked.md`) when a proposal or review relies
  on it.
- Schema changes go in a new `V<n>__*.sql` migration. Never edit a migration that has already
  been applied.
