import type {Page} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard} from "./mocks"

// The bot answers here, unlike the shared mocks, so every Discord list has something in it.
async function withTheBot(page: Page) {
  await installApiMocks(page)
  await page.route("**/api/discord/members?**", route => route.fulfill({json: [
    {id: "123456789012345611", name: "Sam Rivers", username: "extrabyte", avatar: "https://cdn.discordapp.com/embed/avatars/0.png"},
  ]}))
  await page.route("**/api/discord/roles", route => route.fulfill({json: [
    {id: "223456789012345901", name: "Board", colour: 0xE91E63},
    {id: "223456789012345902", name: "Gamers"},
  ]}))
  await page.route("**/api/discord/mentions?**", route => route.fulfill({json: {users: [], channels: [], roles: [
    {id: "223456789012345901", name: "Board", colour: 0xE91E63},
    {id: "223456789012345902", name: "Gamers"},
  ]}}))
  await page.route("**/api/discord/channels", route => route.fulfill({json: [
    {id: "323456789012345602", name: "events-info", category: "Events"},
    {id: "323456789012345603", name: "general"},
  ]}))
  await loginAsBoard(page.context())
  await page.goto("/events/create")
}

async function typeInDescription(page: Page, text: string) {
  await page.getByLabel("Description*").click()
  await page.keyboard.type(text, {delay: 30})
}

const listed = (page: Page) => page.locator(".cm-tooltip-autocomplete li")

test.describe("a description's Discord names", () => {
  test("lists a member found by their username, though their name does not hold it", async ({page}) => {
    await withTheBot(page)

    await typeInDescription(page, "hi @ext")

    await expect(listed(page)).toHaveCount(1)
    await expect(listed(page).first()).toContainText("Sam Rivers")
    await expect(listed(page).first()).toContainText("extrabyte")
    await expect(listed(page).first().locator("img")).toHaveAttribute("src", /avatars\/0\.png$/u)
  })

  test("names roles in the colour their pills take, the mention's own where a role has none, with no picture", async ({page}) => {
    await withTheBot(page)
    await typeInDescription(page, "<@&223456789012345901> <@&223456789012345902> ok\n")
    const pill = (name: string) => page.locator(".cm-content").getByText(name, {exact: true})
    await expect(pill("@Board")).toBeVisible()
    const pillColour = async (name: string) => pill(name).evaluate(one => getComputedStyle(one).color)
    const [boardPill, gamersPill] = [await pillColour("@Board"), await pillColour("@Gamers")]
    expect(boardPill).not.toBe(gamersPill)

    await page.keyboard.type("hi @", {delay: 30})
    await page.keyboard.press("Control+Space")

    const row = (name: string) => listed(page).filter({hasText: name}).getByText(name).filter({visible: true})
    await expect(row("@Board")).toHaveCSS("color", boardPill)
    await expect(row("@Gamers")).toHaveCSS("color", gamersPill)
    await expect(listed(page).filter({hasText: "@"}).locator("img")).toHaveCount(0)
  })

  test("offers channels with their category at the start of a line, and keeps `# ` a heading", async ({page}) => {
    await withTheBot(page)

    await typeInDescription(page, "#ev")

    await expect(listed(page)).toHaveCount(1)
    await expect(listed(page).first()).toContainText("#events-info")
    await expect(listed(page).first()).toContainText("Events")
    await page.keyboard.press("Enter")
    await page.keyboard.type(" and\n# Heading", {delay: 30})
    await expect(listed(page)).toHaveCount(0)
  })

  test("draws a spoiler, a quote, code, subtext and headings as Discord draws them", async ({page}) => {
    await withTheBot(page)

    await typeInDescription(page, "# Big\nsome ||hidden|| and `inline`\n-# small\n```\nblock\n```\n> quoted")
    const line = (text: string) => page.locator(".cm-line").filter({hasText: text})

    await expect(line("Big").getByText("Big")).toHaveCSS("font-weight", "700")
    await expect(line("Big").getByText("Big")).toHaveCSS("font-size", /^2[0-9](\.\d+)?px$/u)
    await expect(page.getByText("hidden", {exact: true})).not.toHaveCSS("background-color", "rgba(0, 0, 0, 0)")
    await expect(page.getByText("inline", {exact: true})).toHaveCSS("font-family", /monospace/u)
    // The box is drawn around the letters, which carry the font.
    await expect(page.getByText("inline", {exact: true}).locator("..")).not.toHaveCSS("background-color", "rgba(0, 0, 0, 0)")
    await expect(line("quoted")).toHaveCSS("border-left-width", "4px")
    await expect(line("quoted").getByText("quoted")).toHaveCSS("font-style", "normal")
    await expect(line("block")).toHaveCSS("font-family", /monospace/u)
    await expect(line("block")).not.toHaveCSS("background-color", "rgba(0, 0, 0, 0)")
    await expect(line("small").getByText("small")).toHaveCSS("font-size", /^1[0-2](\.\d+)?px$/u)
  })

  test("offers the server's own emoji by name", async ({page}) => {
    await withTheBot(page)

    await typeInDescription(page, "yay :pogg")

    await expect(listed(page).first()).toContainText(":POGGERS:")
    await expect(listed(page).first().locator("img")).toHaveAttribute("src", /657733730491826186/u)
  })
})
