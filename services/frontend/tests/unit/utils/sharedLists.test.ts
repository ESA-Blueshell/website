import {describe, expect, it, vi} from "vitest"
import {forgetAfterWrites, refreshSharedLists, sharedList} from "@/utils/sharedLists"

describe("sharedList", () => {
  it("asks the api once, whoever reads it", async () => {
    const load = vi.fn().mockResolvedValue(["a"])
    const list = sharedList(load)

    await Promise.all([list.read(), list.read()])

    expect(load).toHaveBeenCalledTimes(1)
    expect(list.records.value).toEqual(["a"])
  })

  it("starts from nothing once forgotten", async () => {
    const load = vi.fn().mockResolvedValueOnce(["a"]).mockResolvedValueOnce(["b"])
    const list = sharedList(load)
    await list.read()

    list.forget()

    expect(list.records.value).toEqual([])
    await expect(list.read()).resolves.toEqual(["b"])
  })
})

describe("refreshSharedLists", () => {
  it("reads every list that was read again, and leaves one nobody read alone", async () => {
    const read = sharedList(vi.fn().mockResolvedValueOnce(["old"]).mockResolvedValueOnce(["new"]))
    const unread = vi.fn().mockResolvedValue([])
    sharedList(unread)
    await read.read()

    await refreshSharedLists()

    expect(read.records.value).toEqual(["new"])
    await expect(read.read()).resolves.toEqual(["new"])
    expect(unread).not.toHaveBeenCalled()
  })

  it("drops a cache signed up to be forgotten", async () => {
    const forget = vi.fn()
    forgetAfterWrites(forget)

    await refreshSharedLists()

    expect(forget).toHaveBeenCalled()
  })
})
