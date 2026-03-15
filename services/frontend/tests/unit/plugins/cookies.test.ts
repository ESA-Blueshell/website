import {describe, expect, it} from "vitest"
import {deleteCookie, readJsonCookie, writeJsonCookie} from "@/plugins/cookies"

describe("cookies plugin", () => {
  it("writes and reads json cookies", () => {
    writeJsonCookie("login", {username: "tester"}, {secure: false})
    expect(readJsonCookie<{ username: string }>("login")).toEqual({username: "tester"})
  })

  it("returns null for unknown cookie", () => {
    expect(readJsonCookie("does-not-exist")).toBeNull()
  })

  it("deletes cookies", () => {
    writeJsonCookie("to-delete", {ok: true}, {secure: false})
    deleteCookie("to-delete")
    expect(readJsonCookie("to-delete")).toBeNull()
  })
})
