# Ship checklist

Use this before every **ship** (commit + push to a shared branch). The agent rule **Ship command (Planted)** in `.cursor/rules/ship.mdc` requires this unless the user says **ship code only**.

## Documentation

- [ ] **[README.md](../README.md)** — Updated if repo-wide setup, prerequisites, **LAN**, **ship** workflow, or top-level architecture changed.
- [ ] **[frontend/README.md](../frontend/README.md)** — Updated if dev/LAN/Next env, scripts, or UX-relevant behavior changed.
- [ ] **Backend** — If operators need new ports, profiles, env vars, or migration notes, document in README or add/update `backend/README.md`.

## Cursor / agent context

- [ ] **[.cursor/rules/](../.cursor/rules/)** — Any specialist or project rule updated when ownership, stack, DoD, or workflows changed.
- [ ] **[frontend/AGENTS.md](../frontend/AGENTS.md)** — Updated if Next.js / frontend agent conventions changed (and **frontend/CLAUDE.md** if it should stay in sync).

## Quality gates (align with project DoD)

- [ ] Builds / tests relevant to the change pass.
- [ ] No `.env`, keys, or machine-local paths committed.

## Git

- [ ] Commit message is clear (full sentences, real scope).
- [ ] `git status` clean for the intended release (or intentional leftovers called out).
