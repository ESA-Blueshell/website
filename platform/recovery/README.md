# Emergency recovery: getting the member data back

**For emergencies only.** Use this guide when the Blueshell server is gone or broken beyond repair and the board needs the member data from the off-site backup. It takes the data out of the backup and opens it on your own laptop. It does not bring the website back: the last section says where the repository explains that.

Work through the steps in order. Every command is meant to be copied exactly. If one fails, stop and read the "When something goes wrong" section before trying anything else.

## Before you start

### Who has to be there

| Who | Why | Name (fill in) |
|---|---|---|
| A Scaleway owner | Logs in to Scaleway, makes a temporary key and reads the Kopia password | |
| Three of the five Vault share holders | Together they open Vault, which holds the keys to addresses and bank details | |
| Someone at ease with a terminal | Types the commands; can be one of the above | |

The share holders do not have to be in the same room. Each one types their own share into the terminal, so a video call with screen sharing works.

Without three share holders you can still recover everything except addresses and bank details. Those stay encrypted.

### What you need

- A laptop running macOS or Linux, with full-disk encryption on (FileVault on a Mac). On Windows, use WSL.
- **Docker Desktop**, installed and running: https://www.docker.com/products/docker-desktop/
- About 5 GB of free disk space and an internet connection.
- About two hours.

### Handle the data with care

What you recover is personal data of every member, including bank details. Keep it on the encrypted laptop only. Never upload it to Google Drive, email or chat. Write down when and why you restored it, and delete everything once the emergency is over (step 10).

### A note on the backup itself

This guide was written before the nightly backup job was finished. Folder and file names inside the backup may differ slightly from the ones below. The snapshot list in step 3 shows what is really there.

## Step 1: get into Scaleway

The Scaleway owner does this step.

1. Log in at https://console.scaleway.com with your own login and 2FA.
2. Check that the organization at the top right is the backup organization.
3. **Make a temporary key.** Go to IAM & API keys, then API keys, then Generate API key.
   - Bearer: **Myself (IAM user)**
   - Description: `Emergency restore <today's date>`
   - Expiry: **1 day**
   - Object Storage: **Yes**, preferred project `Website`

   Copy the **access key** and the **secret key** somewhere safe for the next hour. The secret key is shown only once.
4. **Read the Kopia password.** Go to Secret Manager (under Security & Identity), choose region **Amsterdam**, open `kopia-repository-password` and reveal its latest version. Keep it ready too.

## Step 2: set up a working folder

Open the Terminal app. Type `bash` and press Enter, so the commands below behave the same on every computer.

Make a folder and go into it:

```bash
mkdir -p ~/blueshell-recovery && cd ~/blueshell-recovery
mkdir -p restore kopia
```

Download the three files this guide uses into it:

```bash
base=https://raw.githubusercontent.com/ESA-Blueshell/website/main/platform/recovery
for f in docker-compose.yml vault.hcl export-sealed.sh; do curl -fsSLO "$base/$f"; done
chmod +x export-sealed.sh
ls
```

`ls` should list `docker-compose.yml`, `export-sealed.sh`, `kopia`, `restore` and `vault.hcl`. If GitHub is unreachable, the files are in the repository under `platform/recovery`, in any clone or download of it.

If a command you copied from Google Docs complains about strange quote marks, Docs has turned `"` or `'` into curly quotes. Retype them as plain quotes.

## Step 3: connect to the backup

Type the secrets in without them appearing on screen or in your history. Paste each one when asked and press Enter:

```bash
read -rsp "Scaleway access key: " SCW_ACCESS_KEY; echo; export SCW_ACCESS_KEY
read -rsp "Scaleway secret key: " SCW_SECRET_KEY; echo; export SCW_SECRET_KEY
read -rsp "Kopia password: " KOPIA_PASSWORD; echo; export KOPIA_PASSWORD
```

Connect, read-only so nothing in the backup can change:

```bash
docker compose run --rm -e KOPIA_PASSWORD kopia repository connect s3 \
  --bucket=esa-blueshell-backups \
  --endpoint=s3.nl-ams.scw.cloud \
  --region=nl-ams \
  --access-key="$SCW_ACCESS_KEY" \
  --secret-access-key="$SCW_SECRET_KEY" \
  --readonly
```

List what the backup holds:

```bash
docker compose run --rm -e KOPIA_PASSWORD kopia snapshot list --all
```

You see one block per thing that is backed up: the database (MariaDB), Vault, the uploaded files and a few smaller ones. Each block lists snapshots by date, newest last, each with an ID at the start of its line.

**Want an older state?** If the newest backups are damaged, for example because an attacker wrote rubbish over them, reconnect at a moment before that happened. Add `--point-in-time=2026-10-01T00:00:00Z` (your date and time, in UTC) to the connect command above.

## Step 4: download the database and Vault

For the newest **MariaDB** snapshot and the newest **Vault** snapshot, copy their IDs from the list and run:

```bash
docker compose run --rm -e KOPIA_PASSWORD kopia snapshot restore <MARIADB-ID> /restore/mariadb
docker compose run --rm -e KOPIA_PASSWORD kopia snapshot restore <VAULT-ID> /restore/vault
```

Take both from the same night. Vault and the database must match, or some addresses and bank details will not open.

Check what arrived:

```bash
ls -R restore
```

You should see a database dump in `restore/mariadb` (a file ending in `.sql` or `.sql.gz`) and a Vault snapshot in `restore/vault` (a file ending in `.snap`). Note the exact file names; the next steps use `blueshell.sql.gz` and `vault.snap` as examples.

The uploaded files (profile pictures, event banners, board documents) can be restored the same way into `/restore/storage`, if you need them.

## Step 5: start the recovery stack

```bash
docker compose up -d db vault adminer
docker compose ps
```

All three should say `running`. Give the database half a minute to start.

## Step 6: load the database

```bash
docker compose exec -T db mariadb -uroot -precovery -e "CREATE DATABASE IF NOT EXISTS blueshell"
gunzip -c restore/mariadb/blueshell.sql.gz | docker compose exec -T db mariadb -uroot -precovery blueshell
```

If the dump ends in plain `.sql`, use `cat` instead of `gunzip -c`.

Check it worked; this prints the number of accounts:

```bash
docker compose exec -T db mariadb -uroot -precovery -N blueshell -e "SELECT COUNT(*) FROM users"
```

**Most member data is now readable**: names, usernames, email addresses, memberships, events and more. Open http://localhost:8081 in your browser to look through it. Log in with system `MySQL`, server `db`, username `root`, password `recovery`, database `blueshell`. Adminer can export any table to CSV (Export, then format CSV).

Addresses and bank details show as `vault:v1:…`. They are encrypted, and steps 7 and 8 open them.

## Step 7: bring Vault back

**7a. Start a temporary Vault and load the backup into it.**

```bash
docker compose exec vault vault operator init -key-shares=1 -key-threshold=1
```

This prints an `Unseal Key 1` and an `Initial Root Token`. They are throwaway values for this laptop only.

```bash
docker compose exec vault vault operator unseal
```

Paste the temporary unseal key when asked. Then load the backup, replacing `<TEMPORARY-ROOT-TOKEN>`:

```bash
docker compose exec -e VAULT_TOKEN=<TEMPORARY-ROOT-TOKEN> vault vault operator raft snapshot restore -force /restore/vault/vault.snap
```

From here on, Blueshell's own Vault is in charge, and the temporary key and token are worthless.

**7b. Unseal it with the real shares.**

```bash
docker compose exec vault vault status
```

If it says `Sealed false`, go on to 7c. If it says `Sealed true`, three share holders each run this once, and type their own share when asked:

```bash
docker compose exec vault vault operator unseal
```

After the third share it says `Sealed false`.

**7c. Make a root token from the shares.**

Production's own root token has been revoked, so three share holders make a new one together. Start the process:

```bash
docker compose exec vault vault operator generate-root -init
```

This prints a **Nonce** and an **OTP**. Write both down.

Each of three share holders then runs this, with the nonce filled in, and types their share when asked. It is fine if these are not the same three people as in 7b:

```bash
docker compose exec vault vault operator generate-root -nonce=<NONCE>
```

The third one prints an **Encoded Token**. Decode it with the OTP:

```bash
docker compose exec vault vault operator generate-root -decode=<ENCODED-TOKEN> -otp=<OTP>
```

The result starts with `hvs.`. That is the root token: it can do anything in Vault, so treat it like the key to everything. Keep it for this session only:

```bash
read -rsp "Root token: " VAULT_TOKEN; echo; export VAULT_TOKEN
```

## Step 8: open the addresses and bank details

```bash
./export-sealed.sh
```

This writes two files into `export/`:

- `addresses.tsv`: each member's user ID and their address.
- `bank-details.tsv`: each membership with a direct debit, with its IBAN, account holder and the address the mandate was signed under.

Open them in a spreadsheet program, or import them into the spreadsheet at File, Import, separator Tab. Join them to the rest of the member data on the user ID, using the `users` table from step 6.

A value that says `UNOPENABLE` belongs to a member whose details could not be opened, usually because the Vault and database snapshots are from different nights. Repeat steps 4 to 8 with a matching pair.

## Step 9: put the data to use

What happens next depends on the emergency. Typical uses:

- Contacting members: the `users` table holds email addresses.
- Collecting contributions: `bank-details.tsv` together with the `memberships` table.
- Rebuilding the website: give the folder to the SiteCie, and see "Bringing the website back" below.

## Step 10: clean up

When the emergency is over:

1. Revoke the root token:

   ```bash
   docker compose exec -e VAULT_TOKEN="$VAULT_TOKEN" vault vault token revoke -self
   ```

2. Stop everything and delete all data on the laptop:

   ```bash
   docker compose down -v
   cd ~ && rm -rf ~/blueshell-recovery
   ```

3. In Scaleway, delete the temporary API key (IAM & API keys, API keys).
4. Delete any spreadsheets you made from the export, and empty the bin.
5. Write down what you restored, when and why, for the board's records.

The share holders' shares are not used up: Vault never stored them, so nothing changes for them.

## When something goes wrong

- **`docker: command not found` or "Cannot connect to the Docker daemon"**: Docker Desktop is not installed or not running. Start it and wait until it says it is running.
- **Kopia says the password is wrong**: check you copied the latest version from Secret Manager, without spaces at the ends.
- **Kopia says access is denied**: the key is from the wrong organization, or has expired. Make a new one (step 1).
- **After 7a, `vault status` shows no leader, or every command times out**: stop and start Vault with `docker compose restart vault`, then unseal again as in 7b.
- **The unseal progress resets**: Vault restarted in between. Start 7b again with three shares.
- **A share is refused**: it may come from a different Vault, for example a set replaced by a rekey. Ask the holder for the share that was valid on the night of the backup.
- **Everything shows `UNOPENABLE`**: the Vault and database snapshots do not match. Redo steps 4 to 8 with a Vault snapshot from the same night as the dump.

## Bringing the website back

Getting the website itself running again is a job for the SiteCie, on a new server. The repository explains every step: https://github.com/ESA-Blueshell/website

Start with these, all under `platform/docs/`:

- `bringup-v2.md`: installing a new server from nothing, from the operating system to the running site. The server's IP address is written into the setup, so a new server means changing it there and in DNS.
- `nix-flake.md` and `flux-bootstrap.md`: the details behind the server installation and the deployment tool it uses.
- `vault-bootstrap.md`: setting up Vault. **When recovering, do not initialise a new Vault and seed fresh secrets.** Load the backup into it as in step 7 above, then unseal with the existing shares. Everything the site needs is in it.
- `bringup-v2.md`, section "Migrate data": loading the database dump. Use the dump from step 4.
- `runbook.md`, section "User uploads": putting the uploaded files back.
- `scaleway-backup.md`: how the backup itself is set up and who holds which key.
