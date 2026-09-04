import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {useMayEditBoards} from "@/domains/boards/island/useMayEditBoards"

const {mockStore} = vi.hoisted(() => ({mockStore: {getters: {isBoard: false as unknown}}}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../../../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

function mayEdit(isBoard: unknown): boolean {
  mockStore.getters.isBoard = isBoard
  let answer = false
  mount(defineComponent({
    setup() {
      answer = useMayEditBoards().value
      return () => h("div")
    },
  })).unmount()
  return answer
}

describe("useMayEditBoards", () => {
  it("offers the affordance to a viewer holding the board role", () => {
    expect(mayEdit(true)).toBe(true)
  })

  it("withholds it from a viewer who does not", () => {
    expect(mayEdit(false)).toBe(false)
  })

  // Asked strictly, so a getter that is merely truthy — or missing while the login response is
  // still on its way — does not put pencils on a page whose every write answers 403.
  it("withholds it where the answer is not the word yes", () => {
    expect(mayEdit(undefined)).toBe(false)
    expect(mayEdit("true")).toBe(false)
    expect(mayEdit(1)).toBe(false)
  })
})
