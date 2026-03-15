import {describe, expect, it, vi} from "vitest"

vi.mock("@/assets/svgs/discord.svg?component", () => ({
  default: {name: "DiscordSvg", template: "<svg />"},
}))
vi.mock("@/assets/svgs/account-multiple-edit.svg?component", () => ({
  default: {name: "AccountMultipleEditSvg", template: "<svg />"},
}))

import {customIconSet, customAliases} from "@/plugins/icons/custom"

describe("custom icon set", () => {
  it("returns a component for known icon 'discord'", () => {
    const result = customIconSet.component({icon: "discord", tag: "i"})
    expect(result).not.toBeNull()
  })

  it("returns a component for known icon 'account-multiple-edit'", () => {
    const result = customIconSet.component({icon: "account-multiple-edit", tag: "i"})
    expect(result).not.toBeNull()
  })

  it("returns null for unknown icon names", () => {
    const warnSpy = vi.spyOn(console, "warn").mockImplementation(() => {})
    const result = customIconSet.component({icon: "nonexistent", tag: "i"})
    expect(result).toBeNull()
    warnSpy.mockRestore()
  })

  it("exposes correct custom aliases", () => {
    expect(customAliases.discord).toBe("custom:discord")
    expect(customAliases.accountMultipleEdit).toBe("custom:account-multiple-edit")
  })
})
