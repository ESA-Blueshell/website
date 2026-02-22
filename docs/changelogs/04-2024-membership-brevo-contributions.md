# 2024 Membership, Brevo, and Contributions

Period: 2024-01 to 2024-12  
Versioning status: no formal release versions yet

## Implemented functionality
- Frontend framework modernization progressed with Vuetify 3 upgrade steps and supporting UI refactors.
- Event workflows were heavily improved:
  - event creation/editing refactors,
  - improved event list details,
  - event-sharing link behavior,
  - better week/start-day/timezone behavior.
- Calendar integration continued to improve, including service separation and timezone consistency fixes.
- Guest/event email behavior expanded (including guest signup email handling).
- Membership signup became a real end-to-end product flow:
  - backend create-member capabilities,
  - frontend membership signup page,
  - signature/date/city onboarding requirements,
  - validation fixes to block invalid submits.
- Addressing and committee data flows improved (including dynamic committee fetching rather than hardcoded content).
- Brevo integration became operationally relevant:
  - service wiring for Brevo,
  - API/config support,
  - event signup and membership-related integration behavior.
- Contribution management features were introduced and expanded:
  - contribution period and contribution APIs,
  - dynamic contribution UI component,
  - contribution/brevo management in member-management contexts.
- Password recovery/reset UX gained stronger validation-before-submit behavior.

## Outcome
- The product reached a much more complete membership-management and contribution-management state, while external integrations (Google Calendar and Brevo) became central to regular workflows.
