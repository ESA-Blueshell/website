import {describe, expect, it, vi} from "vitest"
import {mount, RouterLinkStub} from "@vue/test-utils"
import AgendaRow from "@/domains/events/island/AgendaRow.vue"
import EventAgenda from "@/domains/events/island/EventAgenda.vue"
import NextEventBand from "@/domains/events/island/NextEventBand.vue"

const {getters} = vi.hoisted(() => ({getters: {isLoggedIn: true}}))
vi.mock("@/plugins/store", () => ({default: {getters}}))

/* The actions and the form have their own specs; here they only have to say what they would. */
const EventActions = {
  name: "EventActions",
  props: ["event", "signUps", "committees", "signing"],
  emits: ["update:signing", "update:event", "delete:event", "delete:signUp"],
  template: "<span />",
}
const EventSignUpForm = {
  name: "EventSignUpForm",
  props: ["event", "initialSignUp", "showGuestForm"],
  emits: ["update:signUp", "delete:signUp"],
  template: "<form data-testid='event-signup-form' />",
}
const stubs = {EventActions, EventSignUpForm, RouterLink: RouterLinkStub}

const event = (over: Record<string, unknown> = {}) => ({
  id: 7,
  title: "4Funcie Pooling",
  startTime: "2026-09-22T19:00:00",
  endTime: "2026-09-22T22:00:00",
  location: "Snooker & Poolcentrum Enschede",
  description: "Join **4funcie** for an evening of pool.",
  approved: true,
  signUp: true,
  signUpCount: 6,
  signUpLimit: 24,
  membersOnly: true,
  committeeId: 2,
  ...over,
})

const committees = [{id: 2, name: "4FunCie"}]
const banner = {image: {url: "/files/p.webp", path: "p.webp", width: 1080, height: 1080, renditions: [{url: "/files/p-540.webp", width: 540}]}}

describe("the next event's band", () => {
  const mountBand = (over: Record<string, unknown> = {}) =>
    mount(NextEventBand, {props: {event: event(over), committees, signUps: [{id: 40, eventId: 7}]}, global: {stubs}})

  it("is the event's card, and leads with its poster, uncut", () => {
    const wrapper = mountBand({banner})

    expect(wrapper.attributes("data-testid")).toBe("event-card-7")
    expect(wrapper.attributes("id")).toBe("7")
    expect(wrapper.findComponent({name: "PosterArt"}).props("title")).toBe("4Funcie Pooling")
    expect(wrapper.findComponent({name: "PosterArt"}).props("srcset")).toContain("540w")
  })

  it("says when, where with directions, how full, what it is and who runs it", () => {
    const wrapper = mountBand()

    expect(wrapper.text()).toContain("Tue 22 September")
    expect(wrapper.text()).toContain("19:00-22:00")
    expect(wrapper.get(".band__link").attributes("href")).toContain("google.com/maps")
    expect(wrapper.get("[data-testid=event-band-places]").text()).toBe("6 of 24 taken")
    expect(wrapper.get(".band__meter span").attributes("style")).toContain("width: 25%")
    expect(wrapper.get(".band__blurb").text()).toBe("Join 4funcie for an evening of pool.")
    expect(wrapper.text()).toContain("By 4FunCie")
    expect(wrapper.getComponent(RouterLinkStub).props("to")).toBe("/events/7")
    expect(wrapper.text()).toContain("Members only")
  })

  it("leaves out what the event does not say", () => {
    const wrapper = mount(NextEventBand, {
      props: {event: event({location: null, description: null, signUpLimit: null, membersOnly: false, committeeId: null, startTime: "2099-01-01T19:00:00", endTime: "2099-01-01T21:00:00"})},
      global: {stubs},
    })

    expect(wrapper.find(".band__link").exists()).toBe(false)
    expect(wrapper.find(".band__meter").exists()).toBe(false)
    expect(wrapper.find(".band__blurb").exists()).toBe(false)
    expect(wrapper.find(".next__by").exists()).toBe(false)
    expect(wrapper.find("[data-testid=event-band-price]").exists()).toBe(false)
    expect(wrapper.find("[data-testid=event-band-soon]").exists()).toBe(false)
  })

  it("opens the sign-up form under it, for a guest too, and closes it on a save or a withdrawal", async () => {
    getters.isLoggedIn = false
    const wrapper = mountBand()
    const actions = wrapper.getComponent({name: "EventActions"})

    actions.vm.$emit("update:signing", true)
    await wrapper.vm.$nextTick()
    const form = wrapper.getComponent({name: "EventSignUpForm"})
    expect(form.props()).toMatchObject({showGuestForm: true, initialSignUp: {id: 40, eventId: 7}})

    form.vm.$emit("update:signUp", {id: 41, eventId: 7})
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("update:signUp")?.[0]).toEqual([{id: 41, eventId: 7}])
    expect(wrapper.findComponent({name: "EventSignUpForm"}).exists()).toBe(false)

    actions.vm.$emit("update:signing", true)
    await wrapper.vm.$nextTick()
    wrapper.getComponent({name: "EventSignUpForm"}).vm.$emit("delete:signUp", 41)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted("delete:signUp")?.[0]).toEqual([41])
    getters.isLoggedIn = true
  })

  it("passes on what its actions did", () => {
    const wrapper = mountBand()
    const actions = wrapper.getComponent({name: "EventActions"})

    actions.vm.$emit("update:event", event({approved: false}))
    actions.vm.$emit("delete:event", 7)
    actions.vm.$emit("delete:signUp", 40)

    expect(wrapper.emitted("update:event")).toHaveLength(1)
    expect(wrapper.emitted("delete:event")?.[0]).toEqual([7])
    expect(wrapper.emitted("delete:signUp")?.[0]).toEqual([40])
  })
})

describe("a row of the agenda", () => {
  const mountRow = (over: Record<string, unknown> = {}) =>
    mount(AgendaRow, {props: {event: event({banner, ...over}), committees, signUps: [{id: 40, eventId: 7}]}, global: {stubs}})

  it("is the event's card: its day, what, when, where, who, and whether it can be signed up for", () => {
    const wrapper = mountRow()

    expect(wrapper.attributes("data-testid")).toBe("event-card-7")
    expect(wrapper.get(".row__day").text()).toBe("22")
    expect(wrapper.get(".row__weekday").text()).toBe("Tue")
    expect(wrapper.get(".row__meta").text()).toBe("19:00-22:00 · Snooker & Poolcentrum Enschede · 4FunCie")
    expect(wrapper.get(".row__state").text()).toContain("6 of 24 taken")
    expect(wrapper.get(".row__tag").text()).toBe("Members only")
    expect(wrapper.getComponent(RouterLinkStub).props("to")).toBe("/events/7")
  })

  it("says the one thing there is to say about an event with no sign-ups", () => {
    const wrapper = mount(AgendaRow, {props: {event: event({signUp: false, membersOnly: false, location: null})}, global: {stubs}})

    expect(wrapper.get(".row__state").text()).toBe("No sign-ups, just walk in")
    expect(wrapper.get(".row__meta").text()).toBe("19:00-22:00")
  })

  it("opens the sign-up form under it, and passes on what the form and the actions did", async () => {
    const wrapper = mountRow()
    const actions = wrapper.getComponent({name: "EventActions"})

    actions.vm.$emit("update:signing", true)
    await wrapper.vm.$nextTick()
    expect(wrapper.getComponent({name: "EventSignUpForm"}).props("initialSignUp")).toEqual({id: 40, eventId: 7})
    expect(wrapper.getComponent({name: "PosterArt"}).props("srcset")).toContain("540w")
    wrapper.getComponent({name: "EventSignUpForm"}).vm.$emit("update:signUp", {id: 41, eventId: 7})
    await wrapper.vm.$nextTick()
    expect(wrapper.findComponent({name: "EventSignUpForm"}).exists()).toBe(false)

    actions.vm.$emit("update:signing", true)
    await wrapper.vm.$nextTick()
    wrapper.getComponent({name: "EventSignUpForm"}).vm.$emit("delete:signUp", 41)
    actions.vm.$emit("update:event", event())
    actions.vm.$emit("delete:event", 7)

    expect(wrapper.emitted("update:signUp")).toHaveLength(1)
    expect(wrapper.emitted("delete:signUp")).toHaveLength(1)
    expect(wrapper.emitted("update:event")).toHaveLength(1)
    expect(wrapper.emitted("delete:event")).toHaveLength(1)
  })
})

describe("the agenda", () => {
  it("counts what is coming, groups it by month and offers a committee member a way to add one", () => {
    const wrapper = mount(EventAgenda, {
      props: {
        events: [event({id: 1}), event({id: 2, startTime: "2026-09-29T19:00:00"}), event({id: 3, startTime: "2026-10-02T19:00:00"})],
        committees,
        mayAdd: true,
      },
      global: {stubs: {AgendaRow: {name: "AgendaRow", props: ["event"], emits: ["update:event", "delete:event", "update:signUp", "delete:signUp"], template: "<div />"}}},
    })

    expect(wrapper.getComponent({name: "CountBadge"}).props("count")).toBe(3)
    expect(wrapper.findAll(".agenda__month-name").map(one => one.text())).toEqual(["September 2026", "October 2026"])
    expect(wrapper.findAll(".agenda__month-count").map(one => one.text())).toEqual(["2 events", "1 event"])
    expect(wrapper.get("[data-testid=event-create-btn]").exists()).toBe(true)

    const row = wrapper.getComponent({name: "AgendaRow"})
    row.vm.$emit("update:event", event())
    row.vm.$emit("delete:event", 1)
    row.vm.$emit("update:signUp", {id: 41})
    row.vm.$emit("delete:signUp", 41)
    expect(Object.keys(wrapper.emitted())).toEqual(expect.arrayContaining(["update:event", "delete:event", "update:signUp", "delete:signUp"]))
  })

  it("says nothing else is planned, and offers no adding to a reader outside the committees", () => {
    const wrapper = mount(EventAgenda, {props: {events: []}, global: {stubs: {RouterLink: true}}})

    expect(wrapper.get(".agenda__empty").text()).toContain("Nothing else is planned yet")
    expect(wrapper.find("[data-testid=event-create-btn]").exists()).toBe(false)
  })
})
