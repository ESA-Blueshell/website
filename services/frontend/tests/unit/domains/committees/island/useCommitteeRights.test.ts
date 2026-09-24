import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises} from "@vue/test-utils"
import {defineComponent, h, reactive} from "vue"
import {mount} from "@vue/test-utils"
import {useCommitteeRights} from "@/domains/committees"

const store = vi.hoisted(() => ({getters: {isBoard: false, isLoggedIn: false}}))
vi.mock("vuex", async importOriginal => ({...(await importOriginal<typeof import("vuex")>()), useStore: () => store}))
const findCommitteesByUserId = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommitteesByUserId: () => findCommitteesByUserId(),
}))

const rightsOf = async () => {
  let rights!: ReturnType<typeof useCommitteeRights>
  mount(defineComponent({setup: () => { rights = useCommitteeRights(); return () => h("div") }}))
  await flushPromises()
  return rights
}

beforeEach(() => {
  Object.assign(store.getters, reactive({isBoard: false, isLoggedIn: false}))
  findCommitteesByUserId.mockReset()
})

describe("what the viewer may do with committees", () => {
  it("knows nothing of a visitor", async () => {
    const rights = await rightsOf()

    expect(rights.isBoard.value).toBe(false)
    expect(rights.sitsOn(1)).toBe(false)
    expect(findCommitteesByUserId).not.toHaveBeenCalled()
  })

  it("knows which committees a member sits on, and the board", async () => {
    store.getters.isLoggedIn = true
    store.getters.isBoard = true
    findCommitteesByUserId.mockResolvedValue({data: [{id: 4, name: "LanCie"}]})
    const rights = await rightsOf()

    expect(rights.isBoard.value).toBe(true)
    expect(rights.sitsOn(4)).toBe(true)
    expect(rights.sitsOn(5)).toBe(false)
  })

  it("offers nothing where the committees cannot be read", async () => {
    store.getters.isLoggedIn = true
    findCommitteesByUserId.mockRejectedValue(new Error("down"))
    const rights = await rightsOf()

    expect(rights.sitsOn(4)).toBe(false)
  })
})
