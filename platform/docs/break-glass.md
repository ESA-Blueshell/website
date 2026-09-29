# Break-glass: unlocking an account or resetting two-factor without an admin

Admins unlock accounts and reset two-factor from the user manager
([account lock](../../docs/flows/account-lock/README.md),
[two-factor](../../docs/flows/two-factor/README.md)). This runbook is for when no admin
can: the last admin has lost both their authenticator app and their backup codes, or
every admin is locked.

It needs cluster access, and it is recorded: the security log names the operator as the
actor and every admin is emailed. Hear from the person themselves first, somewhere other
than the account's own email.

## What it does

| Action | Effect |
|--------|--------|
| `UNLOCK` | Clears the lock and emails the person a password reset. |
| `RESET_TWO_FACTOR` | Deletes the authenticator app's secret, the backup codes and the trusted browsers, ends every sign-in and emails a re-enrolment link. The person signs in with their password and that link, then sets up two-factor again. |

A locked account with two-factor usually needs both, `UNLOCK` first.

## Running it

The api image runs the action and stops when it is given the `break-glass` profile. Run it
inside a live api pod, which already holds the Vault secrets it needs, with a small heap and
no web server so it leaves the pod's own process alone:

```bash
POD=$(kubectl -n default get pods -l app.kubernetes.io/name=api -o jsonpath='{.items[0].metadata.name}')
kubectl -n default exec "$POD" -c api -- /bin/sh -ec '
  export SPRING_CLOUD_VAULT_TOKEN="$(cat /vault/secrets/token)"
  set -a; . /vault/secrets/api.env; set +a
  SPRING_PROFILES_ACTIVE=prod,break-glass exec java -XX:MaxRAMPercentage=20 -jar /app/app.jar \
    --spring.main.web-application-type=none \
    --break-glass.action=RESET_TWO_FACTOR \
    --break-glass.username=<username> \
    --break-glass.reason="<why, and how you heard from them>"
'
```

It exits `0` once the action is written and `1` if it was refused, with the reason in its
last log line. The emails it queues are durable: anything not sent by the time it stops is
sent by the running api within a minute.

## Afterwards

- The person follows the link in their email. An admin whose two-factor was reset has
  their admin role back the moment they finish setting it up again.
- Check the security log of the account in the user manager: the run shows as
  **Break-glass command used, by an operator**.
