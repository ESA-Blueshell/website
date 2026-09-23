import {expect, test as base} from "@playwright/test"
import process from "node:process"
import {collectContextCoverage, coverageEnabled} from "./coverage"

export const test = base.extend<{
  coverageCollector: void
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
  coverageCollector: [
    async ({context}, use, testInfo) => {
      await use()
      if (!coverageEnabled) {
        return
      }
      const artifactsWritten = await collectContextCoverage(context, testInfo)
      if (process.env.FRONTEND_E2E_COVERAGE_DEBUG === "true") {
        console.log(`[e2e coverage] ${testInfo.title} -> ${artifactsWritten}`)
      }
    },
    {auto: true},
  ],
})

export {expect}
export type {Page} from "@playwright/test"
