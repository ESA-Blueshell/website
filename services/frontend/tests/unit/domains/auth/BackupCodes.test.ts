import {afterEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import BackupCodes from "@/domains/auth/components/BackupCodes.vue"
import {settle} from "../../pages/helpers"

describe("the backup codes", () => {
  afterEach(() => vi.unstubAllGlobals())

  it("are shown once, copied and downloaded as one text", async () => {
    const writeText = vi.fn(async () => undefined)
    vi.stubGlobal("navigator", {clipboard: {writeText}})
    const wrapper = mount(BackupCodes, {props: {codes: ["aaaaa-bbbbb", "ccccc-ddddd"]}})

    expect(wrapper.findAll("[data-testid=backup-code]").map(code => code.text())).toEqual(["aaaaa-bbbbb", "ccccc-ddddd"])
    await wrapper.find("[data-testid=backup-codes-copy-btn]").trigger("click")
    await settle()

    expect(writeText).toHaveBeenCalledWith("ESA Blueshell backup codes\n\naaaaa-bbbbb\nccccc-ddddd\n")
    expect(wrapper.find("[data-testid=backup-codes-copy-btn]").text()).toContain("Copied")
    expect(wrapper.find("[data-testid=backup-codes-download-btn]").attributes("href"))
      .toBe(`data:text/plain;charset=utf-8,${encodeURIComponent("ESA Blueshell backup codes\n\naaaaa-bbbbb\nccccc-ddddd\n")}`)
  })
})
