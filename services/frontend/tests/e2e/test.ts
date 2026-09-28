import {expect, test as base} from "@playwright/test"
import {unmockedCalls} from "./mocks"

export const test = base.extend<{
  noGoogleFonts: void
  noUnmockedApiCalls: void
}>({
  /**
   * The app asks Google for Roboto as it starts, and `page.goto` waits for the load event, which
   * waits for that answer. A slow one timed navigations out, so the suite answers it itself.
   */
  noGoogleFonts: [
    async ({context}, use) => {
      await context.route("https://fonts.googleapis.com/**", route =>
        route.fulfill({status: 200, contentType: "text/css", body: ""}))
      await use()
    },
    {auto: true},
  ],
  noUnmockedApiCalls: [
    async ({page}, use) => {
      await use()
      expect(await unmockedCalls(page), "api calls the e2e stand-in in mocks.ts has no answer for").toEqual([])
    },
    {auto: true},
  ],
})

export {expect}
export type {Page} from "@playwright/test"
