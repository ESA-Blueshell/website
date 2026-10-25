import {afterEach, describe, expect, it, vi} from "vitest"
import {saveFile} from "@/utils/saveFile"

describe("saving a file", () => {
  afterEach(() => vi.useRealTimers())

  it("downloads it under its name, and lets go of its address after it started", () => {
    vi.useFakeTimers()
    vi.spyOn(URL, "createObjectURL").mockReturnValue("blob:pdf")
    const revoke = vi.spyOn(URL, "revokeObjectURL").mockImplementation(() => undefined)
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(function (this: HTMLAnchorElement) {
      expect(this.download).toBe("declarations.pdf")
      expect(this.href).toBe("blob:pdf")
    })

    saveFile(new Blob(["%PDF"]), "declarations.pdf")

    expect(click).toHaveBeenCalledOnce()
    expect(document.querySelector("a[download]")).toBeNull()
    expect(revoke).not.toHaveBeenCalled()
    vi.advanceTimersByTime(1000)
    expect(revoke).toHaveBeenCalledWith("blob:pdf")
  })
})
