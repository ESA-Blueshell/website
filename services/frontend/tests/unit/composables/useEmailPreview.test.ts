import {describe, expect, it, vi} from "vitest"
import {useEmailPreview} from "@/composables/useEmailPreview"

const rendered = {
  subject: "Activate your Account",
  html: "<p>hello</p>",
  recipientEmail: "alice@example.com",
  recipientName: "Alice Regular",
}

describe("useEmailPreview", () => {
  it("stores what the fetcher returned", async () => {
    const {show, preview, loading, open} = useEmailPreview()

    await show(async () => rendered)

    expect(preview.value).toEqual(rendered)
    expect(open.value).toBe(true)
    expect(loading.value).toBe(false)
  })

  it("opens before the fetch resolves, so the dialog can show progress", () => {
    let settle: (v: typeof rendered) => void = () => {}
    const {show, open, loading} = useEmailPreview()

    void show(() => new Promise((r) => (settle = r)))

    expect(open.value).toBe(true)
    expect(loading.value).toBe(true)
    settle(rendered)
  })

  it("reports a failure rather than showing an empty email", async () => {
    const {show, error, preview, loading} = useEmailPreview()

    await show(async () => null)

    expect(error.value).toBe("The preview could not be rendered.")
    expect(preview.value).toBeNull()
    expect(loading.value).toBe(false)
  })

  it("reports a thrown fetch the same way", async () => {
    const {show, error, loading} = useEmailPreview()

    await show(async () => {
      throw new Error("network")
    })

    expect(error.value).toBe("The preview could not be rendered.")
    expect(loading.value).toBe(false)
  })

  it("clears the previous email before fetching the next", async () => {
    const {show, preview, error} = useEmailPreview()
    await show(async () => rendered)

    await show(async () => null)

    // A stale email must not sit under a fresh error.
    expect(preview.value).toBeNull()
    expect(error.value).not.toBeNull()
  })

  it("knows nothing about any one endpoint", async () => {
    const fetcher = vi.fn(async () => rendered)
    const {show} = useEmailPreview()

    await show(fetcher)

    // The caller owns the call; the composable only owns the state around it.
    expect(fetcher).toHaveBeenCalledWith()
  })

  it("reset returns it to its starting state", async () => {
    const {show, reset, open, loading, error, preview} = useEmailPreview()
    await show(async () => rendered)

    reset()

    expect(open.value).toBe(false)
    expect(loading.value).toBe(false)
    expect(error.value).toBeNull()
    expect(preview.value).toBeNull()
  })
})
