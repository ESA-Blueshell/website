import {beforeEach, describe, expect, it, vi} from "vitest"
import {flushPromises, mount, RouterLinkStub} from "@vue/test-utils"
import AlsoComingUp from "@/domains/events/island/AlsoComingUp.vue"
import EventBand from "@/domains/events/island/EventBand.vue"
import EventSignUpPanel from "@/domains/events/island/EventSignUpPanel.vue"
import {downloadIcs, pageUrlOf} from "@/domains/events/island/eventCalendar"

const {getters, mockWithdraw, mockNetworkError, mockCreate} = vi.hoisted(() => ({
  getters: {isLoggedIn: false, isMember: false, getGuestData: null as null | {accessToken: string}},
  mockWithdraw: vi.fn(),
  mockNetworkError: vi.fn(),
  mockCreate: vi.fn(),
}))
vi.mock("@/plugins/store", () => ({default: {getters}}))
vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockNetworkError}))
vi.mock("@/domains/events/adapters/signUps", async (importOriginal) => ({...(await importOriginal<object>()), withdrawSignUp: mockWithdraw}))
vi.mock("ics", () => ({createEvent: mockCreate}))

const soon = new Date(Date.now() + 3 * 86_400_000).toISOString()
const event = (over: Record<string, unknown> = {}) => ({
  id: 7, title: "4Funcie Pooling", startTime: soon, endTime: soon, location: "Esports Lounge Twente",
  description: "Pool.", approved: true, signUp: true, signUpCount: 6, signUpLimit: 24,
  signUpDeadline: new Date(Date.now() + 86_400_000).toISOString(), membersOnly: false, memberPrice: 5, publicPrice: 9.5, ...over,
}) as never

const EventSignUpForm = {name: "EventSignUpForm", props: ["event", "initialSignUp", "showGuestForm"], emits: ["update:signUp", "delete:signUp"], template: "<form data-testid='event-signup-form' />"}

describe("the event band in full, as an event's own page is headed", () => {
  it("names the price and the deadline, and makes the title the page's heading", () => {
    const wrapper = mount(EventBand, {props: {event: event(), eyebrow: "4FunCie", heading: "h1", full: true}, slots: {actions: "<a>Sign up</a>"}})

    expect(wrapper.get("h1").text()).toBe("4Funcie Pooling")
    expect(wrapper.get("[data-testid=event-band-price]").text()).toBe("€5,00 · €9,50")
    expect(wrapper.text()).toContain("Sign-ups close")
    expect(wrapper.find(".band__facts--four").exists()).toBe(true)
    expect(wrapper.find(".band__blurb").exists()).toBe(false)
    expect(wrapper.get(".band__actions").text()).toBe("Sign up")
  })
})

describe("the sign-up panel on an event's page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    Object.assign(getters, {isLoggedIn: false, isMember: false, getGuestData: null})
  })

  const panel = (props: Record<string, unknown>) => mount(EventSignUpPanel, {props, global: {stubs: {EventSignUpForm}}})

  it("asks a visitor for their details, and a member only for the answers", () => {
    const visitor = panel({event: event()})
    expect(visitor.getComponent({name: "EventSignUpForm"}).props("showGuestForm")).toBe(true)
    expect(visitor.text()).toContain("18 places left. Anybody can come")

    getters.isLoggedIn = true
    const member = panel({event: event({signUpLimit: null})})
    expect(member.getComponent({name: "EventSignUpForm"}).props("showGuestForm")).toBe(false)
    expect(member.get(".panel__line").text()).toBe("Signing up with your account.")
    expect(panel({event: event({signUpLimit: 7})}).text()).toContain("1 place left.")
  })

  it("passes on a sign-up saved or taken back from the form", () => {
    const wrapper = panel({event: event()})
    const form = wrapper.getComponent({name: "EventSignUpForm"})

    form.vm.$emit("update:signUp", {id: 41})
    form.vm.$emit("delete:signUp", 41)

    expect(wrapper.emitted("update:signUp")).toEqual([[{id: 41}]])
    expect(wrapper.emitted("delete:signUp")).toEqual([[41]])
  })

  it.each([
    [{approved: false}, "waiting for the board's approval"],
    [{startTime: "2020-01-01T00:00:00Z"}, "has started"],
    [{membersOnly: true}, "for members"],
    [{signUpDeadline: "2020-01-01T00:00:00Z"}, "Sign-ups have closed"],
    [{signUpCount: 24}, "This event is full"],
  ])("says why nobody new can sign up: %o", (over, why) => {
    expect(panel({event: event(over)}).get("[data-testid=event-panel-closed]").text()).toContain(why)
  })

  it("says to walk in where there are no sign-ups", () => {
    expect(panel({event: event({signUp: false})}).text()).toContain("Just walk in")
  })

  it("says to join the call where an event without sign-ups is on Discord", () => {
    expect(panel({event: event({signUp: false, location: "Discord"})}).text()).toContain("Just join the call")
  })

  it("tells somebody going so, with the calendar file, and lets them change their answers", async () => {
    const wrapper = panel({event: event({signUpForm: {questions: [{id: 1}]}, signUpCount: 24}), signUp: {id: 40}})

    expect(wrapper.text()).toContain("You are going")
    await wrapper.get("[data-testid=event-panel-ics]").trigger("click")
    expect(mockCreate).toHaveBeenCalled()

    await wrapper.get("[data-testid=event-panel-change]").trigger("click")
    const form = wrapper.getComponent({name: "EventSignUpForm"})
    expect(form.props("initialSignUp")).toEqual({id: 40})
    form.vm.$emit("update:signUp", {id: 40})
    await wrapper.vm.$nextTick()
    expect(wrapper.text()).toContain("You are going")
  })

  it("withdraws somebody going to an event that asks nothing, carrying a guest's token", async () => {
    getters.getGuestData = {accessToken: "guest-token"}
    mockWithdraw.mockResolvedValue(undefined)
    const wrapper = panel({event: event(), signUp: {id: 40}})

    await wrapper.get("[data-testid=event-panel-withdraw]").trigger("click")
    await flushPromises()

    expect(mockWithdraw).toHaveBeenCalledWith(40, "guest-token")
    expect(wrapper.emitted("delete:signUp")).toEqual([[40]])
  })

  it("reports a withdrawal the api refused", async () => {
    mockWithdraw.mockRejectedValue(new Error("500"))
    const wrapper = panel({event: event(), signUp: {id: 40}})

    await wrapper.get("[data-testid=event-panel-withdraw]").trigger("click")
    await flushPromises()

    expect(mockWithdraw).toHaveBeenCalledWith(40, null)
    expect(mockNetworkError).toHaveBeenCalled()
    expect(wrapper.emitted("delete:signUp")).toBeUndefined()
  })

  it("says where somebody is going, and leaves out a place the event does not name", () => {
    expect(panel({event: event({location: null}), signUp: {id: 40}}).get(".panel__line").text()).not.toContain(" at ")
  })
})

describe("what else is coming", () => {
  it("shows a few posters, counts the rest and leads to all of them", () => {
    const wrapper = mount(AlsoComingUp, {
      props: {events: [event({id: 8}), event({id: 9, location: null, banner: {image: {url: "/f/p.webp", renditions: []}}})], total: 5},
      global: {stubs: {RouterLink: RouterLinkStub}},
    })

    expect(wrapper.getComponent({name: "CountBadge"}).props("count")).toBe(5)
    expect(wrapper.findAllComponents(RouterLinkStub).map(one => one.props("to"))).toEqual(["/events/8", "/events/9", "/events"])
    expect(wrapper.get(".also").classes()).toContain("island-dark")
  })

  it("draws nothing where nothing else is coming", () => {
    expect(mount(AlsoComingUp, {props: {events: [], total: 0}}).html()).toBe("<!--v-if-->")
  })
})

describe("the event as a calendar file and a link", () => {
  it("hands the event over as a file named after it, and makes nothing of a failure", () => {
    const clicked = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined)
    mockCreate.mockImplementationOnce((_, done) => done(undefined, "BEGIN:VCALENDAR"))
    downloadIcs(event({description: null, location: null}))
    expect(clicked).toHaveBeenCalledTimes(1)
    expect(mockCreate.mock.calls[0]![0]).toMatchObject({title: "4Funcie Pooling", startInputType: "utc"})

    mockCreate.mockImplementationOnce((_, done) => done(new Error("bad"), ""))
    downloadIcs(event())
    expect(clicked).toHaveBeenCalledTimes(1)
    clicked.mockRestore()
  })

  it("links to the event's own page", () => {
    expect(pageUrlOf(event())).toMatch(/\/events\/7$/u)
  })
})
