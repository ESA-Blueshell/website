import {describe, expect, it} from "vitest"
import {DISCORD_TABS, botSteps, type BotStanding} from "@/domains/discord"

const standing = (fields: Partial<BotStanding> = {}): BotStanding => ({
  connected: true, manageRoles: true, manageChannels: true, botRole: {id: "904", name: "Blueshell bot"}, above: [], claimed: [],
  permissions: [{name: "View Channels", neededFor: "Read the channels", granted: true}], hidden: [], ...fields,
})

describe("what to change on Discord for the bot", () => {
  it("is nothing while the bot may do everything", () => {
    expect(botSteps(standing())).toEqual([])
    expect(DISCORD_TABS.map((one) => one.label)).toEqual(["Roles", "Channels", "Bot"])
  })

  it("walks to the bot's role and names the permissions to turn on", () => {
    const steps = botSteps(standing({permissions: [
      {name: "View Channels", neededFor: "Read", granted: true},
      {name: "Manage Roles", neededFor: "Make a role", granted: false},
      {name: "Manage Channels", neededFor: "Make a channel", granted: false},
      {name: "Create Invite", neededFor: "Make the invite", granted: false},
    ]}))

    expect(steps).toEqual([
      "In Discord, open the server's menu and pick Server Settings, then Roles.",
      "Pick @Blueshell bot, then the Permissions tab.",
      "Turn on Manage Roles, Manage Channels and Create Invite, then save the changes.",
    ])
  })

  it("says to move the bot's role up and to let it into a channel, where that is what is in the way", () => {
    const steps = botSteps(standing({botRole: null, above: [{id: "905", name: "Admin"}], hidden: ["mods"]}))

    expect(steps).toHaveLength(2)
    expect(steps[0]).toContain("drag the bot's role above every role the site adds to people. It is now below @Admin")
    expect(steps[1]).toContain("add the bot's role and allow View Channel")
    expect(botSteps(standing({permissions: [{name: "Send Messages", neededFor: "Post", granted: false}]}))[2]).toBe("Turn on Send Messages, then save the changes.")
  })
})
