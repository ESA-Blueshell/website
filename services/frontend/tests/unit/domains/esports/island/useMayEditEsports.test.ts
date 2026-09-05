import {describe, expect, it, vi} from "vitest"
import {useMayEditEsports} from "@/domains/esports/island/useMayEditEsports"

const {store} = vi.hoisted(() => ({store: {getters: {isBoard: false as unknown}}}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, store)
})

describe("useMayEditEsports", () => {
  // The one rule the api enforces is `hasPermission('Team', 'write')`, and the login response
  // carries inherited roles, so an admin arrives holding BOARD and one question answers it.
  it("offers the edit affordances to somebody holding BOARD", () => {
    store.getters.isBoard = true

    expect(useMayEditEsports().value).toBe(true)
  })

  it("offers them to nobody else", () => {
    store.getters.isBoard = false

    expect(useMayEditEsports().value).toBe(false)
  })

  // A page full of edit icons that answer 403 is worse than a page without them, so anything
  // short of the answer itself is read as no.
  it("reads a getter that says nothing as no", () => {
    store.getters.isBoard = undefined

    expect(useMayEditEsports().value).toBe(false)
  })
})
