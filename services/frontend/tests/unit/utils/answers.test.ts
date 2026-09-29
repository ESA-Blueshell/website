import {describe, expect, it} from "vitest"
import {readOr} from "@/utils/answers"

describe("readOr", () => {
  it("answers the body a read came back with", async () => {
    await expect(readOr(Promise.resolve({data: [1, 2]}), null)).resolves.toEqual([1, 2])
  })

  it("answers the fallback where the api refused", async () => {
    await expect(readOr(Promise.resolve({data: [1], error: {title: "Forbidden"}}), [])).resolves.toEqual([])
  })

  it("answers the fallback where no body came back", async () => {
    await expect(readOr(Promise.resolve({}), null)).resolves.toBeNull()
  })
})
