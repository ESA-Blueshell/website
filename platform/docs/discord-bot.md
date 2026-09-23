# The Discord bot

The api reads the association's Discord server through a bot:
- who is in which voice room, private rooms included;
- the online and member counts;
- the starboard;
- the member list that website accounts link to.

A bot token cannot sit in the frontend, so everything goes through the api (#1344).

There are two bots:
- **Blueshell**, in the association's server, whose token lives in Vault;
- **Blueshell Dev**, in a small test server, whose token developers put in `services/api/.api.env`.

A leaked dev token then never touches the real server.

## What the bot needs

| For | Discord needs |
| --- | --- |
| Who is in voice | the gateway connection with the Guilds and Guild Voice States intents. REST cannot list voice members. |
| Counts, channels, which rooms are private | REST, nothing privileged |
| The starboard's text | the **Message Content** intent |
| The member list | the **Server Members** intent |

In the server it needs **View Channels** and **Read Message History**, and nothing else. It never joins voice, posts, or changes anything. In the invite link below, those two permissions are the number `66560`.

Below 100 servers, Discord grants privileged intents without review.

## 1. Create the application

1. Create a team at <https://discord.com/developers/teams> (for example "ESA Blueshell") and add at least one other board member. The bot then outlives any one person's account.
2. At <https://discord.com/developers/applications>, choose **New Application**, pick the team as owner, and name it **Blueshell** (or **Blueshell Dev** for the dev bot).
3. On **General Information**, note the **Application ID**. It is public.
4. On **Installation**:
   - set **Install Link** to **None**;
   - under **Installation Contexts**, keep **Guild Install** and untick **User Install**.

   A private bot cannot keep Discord's install link, so this comes before the next step.
5. On **Bot**:
   - set the name and avatar;
   - turn **Public Bot** off, so only the team can add it;
   - leave **Requires OAuth2 Code Grant** off;
   - under **Privileged Gateway Intents**, turn on **Server Members Intent** and **Message Content Intent**. Leave **Presence Intent** off: the online count comes without it.
6. Still on **Bot**, choose **Reset Token** and copy it into the password manager. Discord shows it once.

## 2. Add it to the server

Somebody who is in the team and has **Manage Server** in the server opens:

```
https://discord.com/oauth2/authorize?client_id=<APPLICATION_ID>&scope=bot&permissions=66560
```

Then copy the server ID:
1. In Discord, go to **User Settings → Advanced** and turn on **Developer Mode**.
2. Right-click the server icon and choose **Copy Server ID**.

What the bot can see follows the channel permissions, like any member's:
- A room everybody can view but not join is visible to it. The site shows it as locked, with who is inside.
- A room hidden from everybody stays hidden from the bot, and from the site.

## 3. Check it and register the token

`scripts/discord-bot-check.sh` checks everything above in one run:
- the token is a bot token;
- both intents are on;
- the bot is private;
- the bot is in the server. Where it is not, the script prints the exact invite link.

It asks for the token at a hidden prompt, or reads `DISCORD_BOT_TOKEN` or stdin. It never takes the token as an argument, so the token stays out of shell history and `ps`.

**Dev**, which writes `DISCORD_BOT_TOKEN` and `DISCORD_GUILD_ID` into `services/api/.api.env`:

```bash
scripts/discord-bot-check.sh --guild <test-server-id> --dev
docker compose up -d api
```

**Production**, which patches `secret/api` and restarts the api. It needs the `vault` CLI logged in and `kubectl` on the cluster:

```bash
scripts/discord-bot-check.sh --guild <server-id> --vault
```

By hand, it is always `patch`, never `put`. `vault kv put secret/api …` replaces every field in the secret, including the JWT secret and every other integration:

```bash
vault kv patch secret/api discord-bot-token=- discord-guild-id=<server-id>   # the token on stdin
kubectl -n default rollout restart deployment/api
```

The api's Vault Agent template renders both fields into `DISCORD_BOT_TOKEN` and `DISCORD_GUILD_ID` (`platform/cluster/flux/apps/stateless/api/deployment.yaml`).

`./gradlew :services:api:discordLiveTest` with `DISCORD_BOT_TOKEN` set calls Discord once as the bot, as a second check.

## Rotating the token

**Reset Token** on the Bot tab ends the old token at once, so register the new one straight away:
1. Run the check script with `--vault`, or `--dev`.
2. Update the password manager.

A token pushed to GitHub is caught by secret scanning, and Discord revokes it on its own. Rotate it the same way.

## When it is missing or down

The site treats the bot as optional:
- With no token, or with Discord unreachable, the Discord band falls back to Discord's public widget.
- The rest of the site is unaffected.
