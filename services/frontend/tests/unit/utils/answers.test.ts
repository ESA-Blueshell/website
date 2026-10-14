import {describe, expect, it} from "vitest"
import {readBlobRefusal, readOr} from "@/utils/answers"

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

describe("a file download's refusal", () => {
  it("reads a refusal that came back as a Blob as the JSON it is, and leaves every other answer alone", async () => {
    const refused = await readBlobRefusal(Promise.resolve({error: new Blob(['{"code":"NoOnlineMandate"}'])}))
    expect(refused.error).toEqual({code: "NoOnlineMandate"})

    const garbled = await readBlobRefusal(Promise.resolve({error: new Blob(["not json"])}))
    expect(garbled.error).toBeNull()

    const file = new Blob(["%PDF"])
    expect(await readBlobRefusal(Promise.resolve({data: file}))).toEqual({data: file})
  })
})
