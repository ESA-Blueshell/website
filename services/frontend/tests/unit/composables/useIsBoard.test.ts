import {describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount} from "@vue/test-utils"
import {useIsBoard} from "@/composables/useIsBoard"

const {mockStore} = vi.hoisted(() => ({mockStore: {getters: {isBoard: false as unknown}}}))

vi.mock("vuex", async (importOriginal) => {
  const {withVuexUseStore} = await import("../helpers/testUtils")
  return withVuexUseStore(importOriginal, mockStore)
})

function isBoard(getter: unknown): boolean {
  mockStore.getters.isBoard = getter
  let answer = false
  mount(defineComponent({
    setup() {
      answer = useIsBoard().value
      return () => h("div")
    },
  })).unmount()
  return answer
}

describe("useIsBoard", () => {
  it("says yes to a reader holding the board role", () => {
    expect(isBoard(true)).toBe(true)
  })

  it("says no to a reader who does not", () => {
    expect(isBoard(false)).toBe(false)
  })

  // Asked strictly, so a getter that is merely truthy, or missing while the login response is
  // still on its way, does not put pencils on a page whose every write answers 403.
  it("says no where the getter's answer is not the word yes", () => {
    expect(isBoard(undefined)).toBe(false)
    expect(isBoard("true")).toBe(false)
    expect(isBoard(1)).toBe(false)
  })
})
