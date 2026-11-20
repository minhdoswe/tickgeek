# Taste

What works and what doesn't for this user: rules collected from their corrections. Claude reads
the matching files before any piece of work and adds an entry every time the user corrects it
(see `CLAUDE.md`, "Follow the user's taste rules").

## Index: which file to read for which work

| File | Read it when |
|---|---|
| `test-comment.md` | Writing or changing comments or Javadoc (tests included), or explaining code or a decision |

Work that spans several topics needs every matching file. Each new topic file gets its own row
here. Name it after the topic, e.g. `naming.md` or `api.md`.

## Entry format

```markdown
### <Short rule name>
- Doesn't work: what Claude did that the user corrected, with a real example.
- Works: what to do instead, with an example.
- Why: the reason the user gave, or the problem the bad version caused.
- Applies to: (optional) where the rule reaches beyond its file's topic.
```

## Promotion to the constitution

`/taste-promote` lets the user pick entries and amends `.specify/memory/constitution.md`
through `/speckit-constitution`. Promoted entries stay here, with this line added under them:

```markdown
- **Promoted**: constitution vX.Y.Z, <principle or section> (YYYY-MM-DD)
```

The examples stay useful after promotion, but the rule now lives in two places. When a promoted
rule changes, change the constitution through `/speckit-constitution` as well.
