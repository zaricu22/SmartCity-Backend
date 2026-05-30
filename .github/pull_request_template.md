## Summary
<!-- One paragraph: what changed and why. -->

Closes #<!-- issue number -->

---

## Type of Change
- [ ] `type: bug` — fix
- [ ] `type: feature` — new functionality
- [ ] `type: refactor` — no functional change
- [ ] `type: chore` — dependency update, tooling, cleanup
- [ ] `type: docs` — documentation only
- [ ] `type: release` — version tag, deployment

## Area
- [ ] `area: backend`
- [ ] `area: frontend`
- [ ] `area: database`
- [ ] `area: devops`
- [ ] `area: security`

---

## DDD / Architecture Checklist
- [ ] Change is confined to the correct bounded context
- [ ] No domain logic leaked into application/infrastructure layer
- [ ] Domain events raised where appropriate

## Quality Checklist
- [ ] Tested locally
- [ ] New/updated tests included
- [ ] No lint or compiler warnings
- [ ] No secrets committed

## Backend *(delete if frontend PR)*
- [ ] DB migrations are backward-compatible
- [ ] API contract unchanged or versioned

## Frontend *(delete if backend PR)*
- [ ] Renders correctly on mobile and desktop
- [ ] Change detection strategy respected (OnPush / signals)

---

## Screenshots / Notes