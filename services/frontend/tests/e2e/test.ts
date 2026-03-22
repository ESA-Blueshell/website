import {expect, test as base} from "@playwright/test"
import process from "node:process"
import {collectContextCoverage, coverageEnabled} from "./coverage"

export const test = base.extend<{
  coverageCollector: void
}>({
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
