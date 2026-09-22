import {describe, expect, it, vi} from "vitest"
import {enqueueJob, listJobTypes} from "@/domains/jobs/adapters/jobs"
import {enqueue, jobTypes} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  enqueue: vi.fn(),
  jobTypes: vi.fn(),
}))

describe("listJobTypes", () => {
  it("answers with the jobs that can be triggered by hand", async () => {
    vi.mocked(jobTypes).mockResolvedValue({data: [{type: "contact.sync", payloadFields: []}]} as never)

    await expect(listJobTypes()).resolves.toEqual([{type: "contact.sync", payloadFields: []}])
  })

  it("reads an answer without a body as no job types", async () => {
    vi.mocked(jobTypes).mockResolvedValue({} as never)

    await expect(listJobTypes()).resolves.toEqual([])
  })
})

describe("enqueueJob", () => {
  it("queues the job with the payload it was given", async () => {
    vi.mocked(enqueue).mockResolvedValue({data: {id: 5}} as never)

    await expect(enqueueJob("contact.sync", {userId: 7})).resolves.toEqual({ok: true})
    expect(enqueue).toHaveBeenCalledWith({body: {jobType: "contact.sync", payload: {userId: 7}}})
  })

  // Pressing Trigger and being told nothing is indistinguishable from pressing nothing at all.
  it("answers with the api's own words when it says no", async () => {
    vi.mocked(enqueue).mockResolvedValue({error: {detail: "Unknown job type."}} as never)

    await expect(enqueueJob("nope", {})).resolves.toEqual({ok: false, reason: "Unknown job type."})
  })

  it("falls back to its own sentence where the api gave no words", async () => {
    vi.mocked(enqueue).mockResolvedValue({data: undefined} as never)

    await expect(enqueueJob("contact.sync", {}))
      .resolves.toEqual({ok: false, reason: "That job could not be triggered."})
  })
})
