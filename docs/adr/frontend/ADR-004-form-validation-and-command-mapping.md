# ADR-004: Form Validation and Command Mapping

## Status
Accepted

## Context
Backend write APIs are command-oriented with stricter field semantics. Frontend forms must validate and map intentionally to those command contracts. Directly binding generated request types to form state causes weak UX validation, brittle payloads, and hard-to-debug backend errors.

## Decision
Use a three-stage validation and mapping pipeline for mutating features.

### Validation Pipeline
1. UI interaction validation for immediate field feedback (VeeValidate)
2. Schema validation for command payload shape (Zod)
3. Backend error reconciliation (`Problem Details` -> field/global errors)

### Mapping Rules
- Form model types are UI-specific and independent from transport types.
- Each command use case has a dedicated mapper: `form model -> command payload`.
- Normalization (trimming, casing, optional coercion) happens in mapper functions.

## Consequences

### Positive
- Better form UX and fewer invalid submissions
- Stronger alignment with backend command contracts
- Predictable place to adapt backend validation responses

### Negative
- More explicit schema/mapper code per feature
- Requires consistency in form architecture conventions

## Guidelines

### DO
- Define schemas per command use case
- Keep mapper functions pure and unit-testable
- Surface backend field violations at the corresponding form field

### DO NOT
- Bind generated request types directly to forms
- Scatter normalization logic across templates and watchers
- Collapse all backend failures into a single generic error message

## References
- VeeValidate: https://vee-validate.logaretm.com/v4/
- Zod: https://zod.dev/
- RFC 7807 Problem Details: https://datatracker.ietf.org/doc/html/rfc7807
