# OpenAPI specs

Central location for every OpenAPI document consumed by this repo.
Consumed by the OpenAPI client generators (`:libs:clients:*` via
`openapi-client-conventions`) and by the frontend `@hey-api/openapi-ts`
generators.

| File            | Source                                                                               | Consumer                            |
| --------------- | ------------------------------------------------------------------------------------ | ----------------------------------- |
| `brevo.yml`     | `https://api.brevo.com/v3/swagger_definition_v3.yml`                                 | `libs/clients/brevo`                |
| `discord.json`  | `https://raw.githubusercontent.com/discord/discord-api-spec/refs/heads/main/specs/openapi.json` | `services/frontend` discord client  |
| `hornet.json`   | Vendored Hornet bot API spec                                                         | (reserved for future client)        |

`scripts/generate_openapi.sh` regenerates `brevo.yml` and `discord.json`
from upstream and refreshes the generated client code.
