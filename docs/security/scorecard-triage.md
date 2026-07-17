# Scorecard Triage: Process & Settings Findings

This document records the triage of OpenSSF Scorecard findings that are about repository process and settings rather than code changes. Each finding below includes its current status, recommended action, and whether it requires repo-admin settings changes or code implementation.

## Findings

### 1. BranchProtectionID (High) — Branch Protection

**Current Status:** Branch protection is not maximal on the default branch (`main`).

**Recommended Action:**
- Enable branch protection rule on `main` requiring:
  - At least one approving review before merge
  - Dismiss stale reviews when new commits are pushed
  - Require status checks to pass before merge (CI/build must succeed)

**Type:** Repo-admin setting (via GitHub Settings → Branches → Branch protection rules)

**Impact:** Addresses both `BranchProtectionID` (high) and improves `CodeReviewID` (high) by enforcing review workflow.

---

### 2. CodeReviewID (High) — Code Review Enforcement

**Current Status:** 0/24 recent changesets show an approved review.

**Recommended Action:**
This finding will be largely addressed by the branch protection rule enforcement described above (BranchProtectionID). Once `main` requires approving reviews, new contributions will automatically show review approval in commit history.

For existing commits: no retroactive action needed; this metric will improve as branch protection is enforced on new PRs.

**Type:** Repo-admin setting (depends on branch protection rule from BranchProtectionID).

**Impact:** Enforcing review on `main` will clear this finding over time.

---

### 3. FuzzingID (Medium) — Fuzzing Integration

**Current Status:** No fuzzing integration detected.

**Recommended Action:**
For a small-team esports-club project, continuous fuzzing is likely disproportionate. Options:
- **Accept the finding:** Document rationale and dismiss if fuzzing is not a priority.
- **Schedule for follow-up:** If fuzzing becomes relevant (e.g., after security audit), open a follow-up issue to evaluate integrations like libFuzzer or OSS-Fuzz.

**Type:** Code change (if pursued; fuzzing harnesses and CI integration).

**Current Decision:** Defer; reassess only if security concerns warrant it.

---

### 4. CIIBestPracticesID (Low) — OpenSSF Best Practices Badge

**Current Status:** No OpenSSF Best Practices badge detected.

**Recommended Action:**
This badge requires a formal self-assessment and documentation of practices (security policy, changelog, contribution guidelines, etc.). For a small team:
- **Accept the finding:** The overhead of formal badge application may not justify the value for a student project.
- **Schedule for follow-up:** If the project grows or pursues broader adoption, consider applying for the badge later.

**Type:** Repo-admin / documentation (application to the OpenSSF program).

**Current Decision:** Defer; revisit if the project scope or visibility expands.

---

## Summary

| Finding | Type | Decision | Priority |
|---------|------|----------|----------|
| BranchProtectionID (high) | Repo-admin | Enable branch protection + review rule on `main` | **HIGH — do now** |
| CodeReviewID (high) | Repo-admin | Enforce via branch protection (above) | **HIGH — do now** |
| FuzzingID (medium) | Code / CI | Defer; reassess later | Low |
| CIIBestPracticesID (low) | Repo-admin / docs | Defer; revisit if scope expands | Low |

---

## Next Steps

1. Enable branch protection rule on `main` (requires repo admin access).
2. Document the decision to defer FuzzingID and CIIBestPracticesID in this file.
3. Schedule periodic review (quarterly or annually) to reassess low-priority findings as the project evolves.
