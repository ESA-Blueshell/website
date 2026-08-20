# Flow Documentation Index

Flow docs describe **how a multi-step user-facing process works right now**: the
states an actor passes through, the tokens or sessions that carry them, the
invariants that hold throughout, and the endpoints involved.

They exist because this information is otherwise spread across a Vue page, a
handful of controllers, a token table and an event listener, and no single file
tells you what the process is supposed to guarantee.

## Flows

| Flow | Summary |
|------|---------|
| [Account creation](account-creation/README.md) | How anybody gets a guest account; everything correctable until the address is confirmed |
| [Signing in](sign-in/README.md) | The gate: who gets a session, and what a refusal gives away |
| [Membership signup](membership-signup/README.md) | Account creation plus an application; the membership commits on whichever fact lands last |

Account creation is the base flow and membership signup builds on it, so the two
are read in that order and neither restates the other.

## What a flow doc is, and is not

**Is** — a present-tense description of the system as it stands. If the code
changes, the doc changes in the same commit.

**Is not** — a change narrative. Flow docs never contrast against a previous
design, never say "instead of" or "previously", and never carry migration notes.
Design rationale belongs in an [ADR](../adr/ADR-INDEX.md), which records why a
decision was taken and what was rejected; a flow doc only records what the system
does.

## Template

Every flow doc uses these sections, in this order. Drop a section only when the
flow genuinely has nothing to put in it.

1. **Scope** — what the flow covers and, explicitly, what it does not. Name the
   adjacent flows a reader might confuse it with.
2. **Actors and entry points** — who starts the flow and from which URL or event.
3. **States** — the states the subject can be in, what each one permits, and how
   it is represented in the database. A state diagram belongs here.
4. **Invariants** — stated as things that *cannot* happen. These are the claims
   the tests defend, so each one should be traceable to a scenario.
5. **The journey** — the happy path as a diagram plus a short numbered walkthrough.
6. **Alternative orderings** — where events can arrive in more than one order,
   one diagram per order. Silence here is a claim that order cannot vary.
7. **Credentials** — every token or session the flow issues: purpose, whether it
   is transmitted out of band, where the client holds it, TTL, use semantics,
   exactly what it authorises, and what retires it.
8. **Endpoints** — path, method, authorisation, request and response shape, rate
   limit.
9. **Failure and recovery** — expiry, abandonment, duplicate submission, and the
   route back in when the client loses its state.
10. **Where the code lives** — a file map into `services/api` and
    `services/frontend`. This is the section that rots fastest; keep it short and
    point at owners rather than every file.
11. **Testing** — which suites cover the flow and how scenario names map to tests.

A `.feature` file sits next to the doc when the flow has behaviour worth stating
as scenarios. It is a specification, not an executable suite — the repo runs
Playwright and JUnit, not Cucumber — so scenario names are mirrored by test names
so the correspondence can be checked by eye.

## Diagrams

Diagrams are ```` ```mermaid ```` fences inline in the markdown, next to the prose
that explains them. GitHub and the JetBrains markdown preview both render them
natively, there is only ever one copy to keep current, and a diagram can be edited
in the same pass as the sentence it illustrates.

Two rules keep them readable everywhere, both learned the hard way.

**Do not pin a theme.** No `%%{init}%%` directive, no `themeVariables`. Renderers
theme a diagram to the reader's light or dark preference; pinning a palette fights
whichever one the reader chose and produces dark text on a dark ground for half the
audience. Left alone, the renderer keeps the whole diagram internally consistent.

**Keep every label short and on one line.** No `<br/>`. A label carrying three facts
separated by line breaks is the thing that renders as truncated in some viewers and
as run-together words in others, and neither is controllable from a markdown file.
When a step needs three facts, give it three nodes — which reads better anyway,
because a box with one statement in it is a box you can point at in review.

**Avoid `sequenceDiagram`.** Its message labels are the only text Mermaid draws
straight onto the canvas with no shape behind them, so in a viewer that renders a
light-themed diagram onto a dark page they come out dark on dark and vanish. Every
other diagram type puts its text on a fill and survives. Express an exchange as a
flowchart whose labels name the participants — `form → api · POST /signup` — which
keeps the ordering and the who-calls-what, on a fill.

These rules are all the same rule: a markdown file has no stylesheet, so anything
that depends on the renderer behaving a particular way will eventually meet a
renderer that does not.

## Related documentation

- [ADR umbrella index](../adr/ADR-INDEX.md)
- [CLAUDE.md](../../CLAUDE.md)
