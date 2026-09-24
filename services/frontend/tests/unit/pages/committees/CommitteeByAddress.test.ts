import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount} from "@vue/test-utils"
import {reactive} from "vue"
import CommitteeByAddress from "@/pages/committees/CommitteeByAddress.vue"

const route = reactive({params: {address: "lancie"}})
vi.mock("vue-router", async importOriginal => ({...(await importOriginal<typeof import("vue-router")>()), useRoute: () => route}))
const findCommitteePage = vi.fn()
vi.mock("@/services/api", async importOriginal => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommitteePage: (options: unknown) => findCommitteePage(options),
}))

const stubs = {
  CommitteePage: {name: "CommitteePage", props: ["page"], emits: ["changed"], template: "<div data-testid=page />"},
  NotFound: {name: "NotFound", template: "<div data-testid=missing />"},
}

const lan = {id: 7, name: "LanCie", slug: "lancie", description: "", listed: true, archived: false, banner: null, gameCodes: [], members: []}

beforeEach(() => {
  route.params.address = "lancie"
  findCommitteePage.mockReset()
})

describe("a committee's page by its address", () => {
  it("shows the committee the address names, titles the tab after it, and reads it again when it changes", async () => {
    findCommitteePage.mockResolvedValue({data: lan})
    const wrapper = mount(CommitteeByAddress, {global: {stubs}})
    await flushPromises()

    expect(wrapper.getComponent({name: "CommitteePage"}).props("page").id).toBe(7)
    expect(document.title).toBe("LanCie — Blueshell")
    wrapper.getComponent({name: "CommitteePage"}).vm.$emit("changed")
    await flushPromises()
    expect(findCommitteePage).toHaveBeenCalledTimes(2)
  })

  it("reads as not found once the api has answered without it, and as nothing before", async () => {
    findCommitteePage.mockResolvedValueOnce({data: lan}).mockResolvedValueOnce({error: {status: 404}})
    const wrapper = mount(CommitteeByAddress, {global: {stubs}})
    await flushPromises()

    route.params.address = "gone"
    await wrapper.vm.$nextTick()
    expect(wrapper.find("[data-testid=missing]").exists()).toBe(false)
    await flushPromises()

    expect(findCommitteePage).toHaveBeenLastCalledWith({path: {address: "gone"}})
    expect(wrapper.find("[data-testid=missing]").exists()).toBe(true)
  })
})
