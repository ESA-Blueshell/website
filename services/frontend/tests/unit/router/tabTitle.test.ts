import {describe, expect, it} from "vitest"
import router from "@/plugins/router"

describe("Tab title", () => {
  it("names the page the reader is on, not the last event they opened", async () => {
    await router.push("/events/12")
    document.title = "LAN party — Blueshell Esports"

    await router.push("/contact")
    expect(document.title).toBe("Contact — Blueshell Esports")

    await router.push("/")
    expect(document.title).toBe("Blueshell Esports - Student esports association")
  }, 20_000)

  it("keeps the title a page set for itself when only the query changes", async () => {
    await router.push("/esports/valorant")
    document.title = "Valorant — Blueshell Esports"

    await router.push("/esports/valorant?season=2025")
    expect(document.title).toBe("Valorant — Blueshell Esports")
    await router.push("/")
  }, 20_000)
})
