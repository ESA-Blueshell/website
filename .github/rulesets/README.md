# Rulesets

What protects the branches, in a form you can read in a review rather than
only in Settings.

**These files are a record, not the source of truth.** GitHub does not apply
them from the repository; the live rulesets are what enforces. Editing a file
here changes nothing until someone imports it. Changing a ruleset in the UI
does not update the file either, so a change belongs in both places.

They are in GitHub's own import format. To apply one:

**Settings → Rules → Rulesets → New ruleset → Import a ruleset**, and pick the
file. Or, for an existing ruleset:

```bash
gh api repos/ESA-Blueshell/website/rulesets/<id> --method PUT \
  --input .github/rulesets/<name>.json
```

To export what is live, so a file can be checked against it:

```bash
gh api repos/ESA-Blueshell/website/rulesets/<id> \
  | jq '{name, target, enforcement, bypass_actors, conditions, rules}'
```

`bypass_actors` carries numeric ids that mean nothing on their own:

| File | `actor_id` | What it is |
| --- | --- | --- |
| `main.json` | `null`, `OrganizationAdmin` | any org owner |
| `main.json` | `5`, `RepositoryRole` | the repository's admin role |
| `release-branch.json` | `5003309`, `Integration` | the `blueshell-release` GitHub App |

`Code Quality Copilot review for default branch` also exists and is disabled,
so it enforces nothing and is not recorded here.
