# 2026 1.1.1 Policy Alignment and Privacy Lifecycle Hardening

Period: 2026-02 onward (post-1.1.0 patch cycle)  
Versioning status: upcoming `1.1.1`

## Primary Focus

`1.1.1` is a compliance-focused patch release. Its primary objective is to align application behavior, consent flows,
and operational retention controls with the updated Privacy Policy and Cookie Policy.

## Policy Updates and Product Alignment

- Privacy Policy and Cookie Policy content were updated and redistributed in policy artifacts.
- Privacy policy wording (EN/NL) was aligned to the implemented lifecycle semantics:
    - 90-day account restoration window after deletion.
    - irreversible anonymization/removal behavior after the restore window.
- Cookie policy references were aligned to active policy metadata across user-facing surfaces.
- Site behavior was explicitly aligned to policy expectations:
    - cookie consent remains on fixed storage key `esa-blueshell.nl:cookiesAccepted`,
    - consent value now carries policy-version context so policy updates re-prompt consent,
    - privacy-policy agreement is required in both public signup paths.

## Privacy Retention and Deletion Lifecycle Delivery

- Completed execution of the privacy-retention and user-deletion lifecycle plan:
    - recoverable delete state with 90-day restore window,
    - restore endpoint and management UX support,
    - irreversible anonymization flow for expired deleted accounts,
    - historical identity projection support for deleted/anonymized users,
    - privacy-safe contact deletion/suppression behavior in contact-sync flows,
    - one-year job execution retention purge automation.
- Operationalized maintenance controls:
    - scheduled anonymization and purge jobs,
    - lifecycle and retention metrics for backlog/runs/failures/duration,
    - runbook guidance for restore operations and failed-job handling.

## Cookie Consent and Signup Consent Hardening

- Completed execution of the cookie policy link and consent versioning plan:
    - centralized cookie policy metadata for link management,
    - active-policy link wiring in snackbar and documents UI,
    - value-based policy versioning for cookie consent payloads,
    - required privacy consent for `/account/create` and `/membership/signup`,
    - improved checkbox structure/copy for legal clarity and consistency.

## API, Frontend, and Contract Alignment

- Updated API/frontend contract coverage around lifecycle features:
    - restore and historical identity endpoints reflected in generated client contracts,
    - frontend lifecycle integration updated to use generated API surface.
- Expanded regression safety for policy/lifecycle behavior:
    - backend integration/system coverage for delete/restore/anonymization flows,
    - frontend unit and e2e coverage for consent and management lifecycle paths.

## Outcome

`1.1.1` formalizes policy-to-product alignment as an implemented capability, not only documentation. The release
delivers enforceable consent behavior, policy-aligned account lifecycle handling, and operational controls needed for
reliable ongoing compliance.
