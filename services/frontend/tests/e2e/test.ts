import {expect, test as base} from "@playwright/test"

export const test = base.extend<{
  noGoogleFonts: void
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
})

export {expect}
export type {Page} from "@playwright/test"
