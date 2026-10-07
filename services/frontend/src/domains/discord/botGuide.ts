import type {BotStanding} from "./adapters/bot"

/** The tabs every page of the Discord section carries. */
export const DISCORD_TABS = [
  {label: "Roles", to: "/management/platforms/discord"},
  {label: "Channels", to: "/management/platforms/discord/channels"},
  {label: "Bot", to: "/management/platforms/discord/bot"},
  {label: "Settings", to: "/management/platforms/discord/settings"},
]

const listed = (names: string[]): string =>
  names.length <= 1 ? names.join("") : `${names.slice(0, -1).join(", ")} and ${names.at(-1)}`

/**
 * What to do on Discord about everything the bot cannot do, in the order it is done there. Nothing
 * where the bot holds every permission, sees every channel and stands above every role.
 */
export function botSteps(standing: BotStanding): string[] {
  const lacking = standing.permissions.filter((one) => !one.granted).map((one) => one.name)
  const role = standing.botRole ? `@${standing.botRole.name}` : "the bot's role"
  const steps: string[] = []
  if (lacking.length > 0) {
    steps.push(
      "In Discord, open the server's menu and pick Server Settings, then Roles.",
      `Pick ${role}, then the Permissions tab.`,
      `Turn on ${listed(lacking)}, then save the changes.`,
    )
  }
  if (standing.above.length > 0) {
    steps.push(`In Server Settings, Roles, drag ${role} above every role the site adds to people. It is now below ${listed(standing.above.map((one) => `@${one.name}`))}, and the bot adds only the roles below its own.`)
  }
  if (standing.hidden.length > 0) {
    steps.push(`For a channel the site keeps that the bot cannot see or change, open the settings of the channel or of its category, pick Permissions, add ${role} and allow View Channel and Manage Permissions. A channel synced to its category follows it.`)
  }
  return steps
}
