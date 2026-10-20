---
name: domain-modeling
description: Build and sharpen the domain model. Use when discussing terminology, editing docs/domain-model.md, or recording an ADR.
---

# Domain Modeling

Adapted from Matt Pocock's domain-modeling skill for this repository's documentation layout.

Actively sharpen the domain vocabulary during design: challenge conflicting terms, test relationships with concrete examples, and record settled terms while the discussion is fresh.

## Repository layout

The canonical vocabulary is the table in `docs/domain-model.md`; durable decisions live in `adr/`. These paths are relative to the repository root. Use the existing document instead of creating a root glossary or another ADR directory.

Keep term definitions concise. Preserve the separate sections for relationships and code mappings. Follow [GLOSSARY-FORMAT.md](./GLOSSARY-FORMAT.md) for vocabulary and [ADR-FORMAT.md](./ADR-FORMAT.md) for decisions.

## During design

- Compare the user's terminology with the current definitions. If the same word means different things, explain the ambiguity and settle the intended meaning.
- Propose a precise term for fuzzy language and use concrete scenarios to expose ambiguous relationships.
- Cross-check stated behavior against the relevant code. Surface contradictions rather than silently changing the domain to match an assumption.
- Write each settled term into the vocabulary table immediately. Keep implementation details in the separate mapping sections or the relevant functional guide.

## Offer ADRs sparingly

Offer an ADR when all three conditions hold: the decision is costly to reverse, a future reader would find it surprising without context, and it resolves a real trade-off. Capture the chosen option, reason and relevant consequences. Reversible local choices do not need an ADR.
