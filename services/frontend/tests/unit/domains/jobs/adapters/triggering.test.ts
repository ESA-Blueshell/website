import {describe, expect, it, vi} from "vitest"
import {enqueueJob, listJobTypes, loadJob} from "@/domains/jobs/adapters/jobs"
import {enqueue, findJobById, jobTypes} from "@/services/api"
import {aJob} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  enqueue: vi.fn(),
  findJobById: vi.fn(),
  jobTypes: vi.fn(),
}))

describe("listJobTypes", () => {
  it("answers with the jobs that can be triggered by hand", async () => {
    vi.mocked(jobTypes).mockResolvedValue(answer(jobTypes, [{type: "contact.sync", payloadFields: []}]))

    await expect(listJobTypes()).resolves.toEqual([{type: "contact.sync", payloadFields: []}])
  })

  it("reads an answer without a body as no job types", async () => {
    vi.mocked(jobTypes).mockResolvedValue(emptyAnswer(jobTypes))

    await expect(listJobTypes()).resolves.toEqual([])
  })
})

describe("enqueueJob", () => {
  it("queues the job with the payload it was given", async () => {
    vi.mocked(enqueue).mockResolvedValue(answer(enqueue, aJob({id: 5})))

    await expect(enqueueJob("contact.sync", {userId: 7})).resolves.toMatchObject({ok: true})
    expect(enqueue).toHaveBeenCalledWith({body: {jobType: "contact.sync", payload: {userId: 7}}})
  })

  // Pressing Queue the job and being told nothing is indistinguishable from pressing nothing at all.
  it("answers with the api's own words when it says no", async () => {
    vi.mocked(enqueue).mockResolvedValue(refusal(enqueue, {detail: "Unknown job type."}))

    await expect(enqueueJob("nope", {})).resolves.toEqual({ok: false, reason: "Unknown job type."})
  })

  it("falls back to its own sentence where the api gave no words", async () => {
    vi.mocked(enqueue).mockResolvedValue(emptyAnswer(enqueue))

    await expect(enqueueJob("contact.sync", {}))
      .resolves.toEqual({ok: false, reason: "That job could not be triggered."})
  })
})

describe("loadJob", () => {
  it("reads one job by its id", async () => {
    vi.mocked(findJobById).mockResolvedValue(answer(findJobById, aJob({id: 9})))

    await expect(loadJob(9)).resolves.toMatchObject({id: 9})
    expect(findJobById).toHaveBeenCalledWith({path: {id: 9}})
  })

  it("answers nothing for a job it could not read", async () => {
    vi.mocked(findJobById).mockResolvedValue(refusal(findJobById, {detail: "Not found."}))

    await expect(loadJob(9)).resolves.toBeNull()
  })
})
