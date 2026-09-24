import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import {ref} from "vue"
import EventPage from "@/pages/events/EventPage.vue"

const {mockRead, mockList, mockPush, mockCommit, getters, reader, mockNetworkError, mockIcs, route} = vi.hoisted(() => ({
  route: {params: {id: "7"}},
  mockRead: vi.fn(),
  mockList: vi.fn(),
  mockPush: vi.fn(),
  mockCommit: vi.fn(),
  getters: {isBoard: false},
  reader: {signUps: null as never, committees: null as never},
  mockNetworkError: vi.fn(),
  mockIcs: vi.fn(),
}))

vi.mock("vue-router", async (importOriginal) => {
  const {reactive} = await import("vue")
  const held = reactive(route)
  return {
    ...(await importOriginal<object>()),
    useRoute: () => held,
    useRouter: () => ({push: mockPush}),
  }
})

vi.mock("@/plugins/store", () => ({default: {getters, commit: mockCommit}}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("@/domains/events", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  readEvent: mockRead,
  listEvents: mockList,
  downloadIcs: mockIcs,
  useEventReader: () => reader,
}))

const event = (over: Record<string, unknown> = {}) => ({
  id: 7, title: "4Funcie Pooling", startTime: "2099-10-02T19:00:00Z", endTime: "2099-10-02T22:00:00Z",
  location: "Esports Lounge Twente", description: "**Pool** night.", approved: true, signUp: true,
  signUpCount: 6, signUpLimit: 24, membersOnly: false, committeeId: 2, ...over,
})

/* The parts have their own specs; here they only say what they were handed and what they did. */
const passing = (name: string, props: string[], emits: string[] = []) => ({name, props, emits, template: "<div />"})
const stubs = {
  VMain: {template: "<main><slot /></main>"},
  RouterLink: RouterLinkStub,
  EventBand: {name: "EventBand", props: {event: Object, eyebrow: String, full: Boolean, heading: String}, template: "<section><slot name='actions' /></section>"},
  EventSignUpPanel: passing("EventSignUpPanel", ["event", "signUp"], ["update:signUp", "delete:signUp"]),
  EventActions: {name: "EventActions", props: {event: Object, committees: Array, manageOnly: Boolean}, emits: ["update:event", "delete:event"], template: "<div />"},
  AlsoComingUp: passing("AlsoComingUp", ["events", "total"]),
}

const mountPage = async () => {
  const wrapper = mount(EventPage, {global: {stubs}})
  await flushPromises()
  return wrapper
}

describe("an event's own page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    getters.isBoard = false
    reader.signUps = ref([]) as never
    reader.committees = ref([]) as never
    mockRead.mockResolvedValue(event())
    mockList.mockResolvedValue([event(), event({id: 8}), event({id: 9})])
  })

  it("reads the event its address names, and heads the page with it", async () => {
    const wrapper = await mountPage()

    expect(mockRead).toHaveBeenCalledWith(7)
    const band = wrapper.getComponent({name: "EventBand"})
    expect(band.props()).toMatchObject({eyebrow: "Blueshell event", full: true, heading: "h1"})
    expect(wrapper.get("[data-testid=event-page-description]").html()).toContain("<strong>Pool</strong>")
    expect(wrapper.find("[data-testid=event-page-signup]").exists()).toBe(true)
  })

  it("names the event in the tab, and leaves the tab alone where there is none", async () => {
    document.title = "Blueshell Esports"
    mockRead.mockResolvedValueOnce(undefined)
    await mountPage()
    expect(document.title).toBe("Blueshell Esports")

    await mountPage()
    expect(document.title).toBe("4Funcie Pooling — Blueshell Esports")
  })

  it("reads the next event when its address changes under it", async () => {
    const {useRoute} = await import("vue-router")
    await mountPage()

    useRoute().params.id = "8"
    await flushPromises()

    expect(mockRead).toHaveBeenLastCalledWith(8)
    useRoute().params.id = "7"
  })

  it("names the committee that runs it, where the reader knows it", async () => {
    reader.committees = ref([{id: 2, name: "4FunCie"}]) as never
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "EventBand"}).props("eyebrow")).toBe("4FunCie")
  })

  it("shows the rest of what is coming, without this event", async () => {
    const wrapper = await mountPage()

    const also = wrapper.getComponent({name: "AlsoComingUp"})
    expect(also.props("events").map((one: {id: number}) => one.id)).toEqual([8, 9])
    expect(also.props("total")).toBe(2)
  })

  it("shows nothing else coming where that could not be read", async () => {
    mockList.mockRejectedValue(new Error("500"))
    const wrapper = await mountPage()

    expect(wrapper.getComponent({name: "AlsoComingUp"}).props("events")).toEqual([])
  })

  it("says so where there is no such event, or where it could not be read", async () => {
    mockRead.mockResolvedValue(undefined)
    expect((await mountPage()).find("[data-testid=event-page-missing]").exists()).toBe(true)

    mockRead.mockRejectedValue(new Error("404"))
    const failed = await mountPage()
    expect(failed.find("[data-testid=event-page-missing]").exists()).toBe(true)
    expect(mockNetworkError).toHaveBeenCalled()
  })

  it("gives its organisers their strip, and leaves after a deletion", async () => {
    expect((await mountPage()).find("[data-testid=event-organiser]").exists()).toBe(false)

    reader.committees = ref([{id: 2, name: "4FunCie"}]) as never
    const wrapper = await mountPage()
    const actions = wrapper.getComponent({name: "EventActions"})
    expect(actions.props("manageOnly")).toBe(true)

    actions.vm.$emit("update:event", event({approved: false}))
    await flushPromises()
    expect(wrapper.getComponent({name: "EventBand"}).props("event")).toMatchObject({approved: false})

    actions.vm.$emit("delete:event", 7)
    expect(mockPush).toHaveBeenCalledWith("/events")
  })

  it("gives the board the strip on any event", async () => {
    getters.isBoard = true

    expect((await mountPage()).find("[data-testid=event-organiser]").exists()).toBe(true)
  })

  it("hands over the calendar file, and copies the page's own address", async () => {
    const writeText = vi.fn().mockResolvedValue(undefined)
    Object.defineProperty(navigator, "clipboard", {value: {writeText}, configurable: true})
    const wrapper = await mountPage()

    await wrapper.get("[data-testid=event-page-ics]").trigger("click")
    expect(mockIcs).toHaveBeenCalled()
    await wrapper.get("[data-testid=event-page-copy]").trigger("click")
    await flushPromises()
    expect(writeText.mock.calls[0]![0]).toMatch(/\/events\/7$/u)
    expect(mockCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", "Link for 4Funcie Pooling copied")
  })

  it("keeps its count and the reader's sign-up in step with the panel", async () => {
    reader.signUps = ref([{id: 99, eventId: 3}]) as never
    const wrapper = await mountPage()
    const panel = () => wrapper.getComponent({name: "EventSignUpPanel"})

    panel().vm.$emit("update:signUp", {id: 40, eventId: 7})
    await flushPromises()
    expect(panel().props("signUp")).toEqual({id: 40, eventId: 7})
    expect(wrapper.getComponent({name: "EventBand"}).props("event").signUpCount).toBe(7)
    expect(wrapper.find("[data-testid=event-page-signup]").exists()).toBe(false)

    panel().vm.$emit("update:signUp", {id: 40, eventId: 7, note: "changed"})
    await flushPromises()
    expect(wrapper.getComponent({name: "EventBand"}).props("event").signUpCount).toBe(7)
    expect(panel().props("signUp")).toMatchObject({note: "changed"})

    panel().vm.$emit("delete:signUp", 40)
    await flushPromises()
    expect(panel().props("signUp")).toBeUndefined()
    expect(wrapper.getComponent({name: "EventBand"}).props("event").signUpCount).toBe(6)
  })

  it("offers no sign-up in the head of an event that takes none, and reads a missing description as none", async () => {
    mockRead.mockResolvedValue(event({signUp: false, description: null}))
    const wrapper = await mountPage()

    expect(wrapper.find("[data-testid=event-page-signup]").exists()).toBe(false)
    expect(wrapper.get("[data-testid=event-page-description]").text()).toBe("")
  })
})
