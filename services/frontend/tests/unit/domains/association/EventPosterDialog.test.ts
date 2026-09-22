import {describe, expect, it} from "vitest"
import {DateTime} from "luxon"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import EventPosterDialog from "@/domains/association/island/EventPosterDialog.vue"

// The dialog portals its content out of the component's subtree, so it is replaced by a
// pass-through: what is under test is what this one puts inside it.
const stubs = {
  IslandDialog: {
    props: ["open"],
    setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) => () => h("div", slots["default"]?.()),
  },
}

const event = (over: Record<string, unknown> = {}) => ({
  id: 7,
  title: "Ye Olde Quest for the Eleven Ales",
  startTime: "2026-10-03T12:00:00Z",
  location: "Witbreuksweg 401B",
  description: "Hear ye, **hear ye**",
  membersOnly: true,
  banner: {
    url: "/files/poster.webp",
    path: "files/poster.webp",
    width: 1600,
    height: 900,
    renditions: [{url: "/files/poster-800.webp", width: 800}],
  },
  ...over,
})

const dialog = (props: Record<string, unknown> = {}) => mount(EventPosterDialog, {
  props: {open: true, event: event(), testid: "events-dialog", ...props},
  global: {stubs},
})

describe("EventPosterDialog", () => {
  it("draws the poster at every width the api stored it in", () => {
    const wrapper = dialog()

    const art = wrapper.get("img")
    expect(art.attributes("src")).toBe("/files/poster.webp")
    expect(art.attributes("srcset")).toBe("/files/poster-800.webp 800w")
  })

  it("says when it was, where it was and who it was for", () => {
    const said = dialog().text()

    // Written where the reader is, so the day is the reader's own rather than the api's.
    expect(said).toContain(DateTime.fromISO("2026-10-03T12:00:00Z").toFormat("cccc d LLLL yyyy, HH:mm"))
    expect(said).toContain("Witbreuksweg 401B")
    expect(said).toContain("Members only")
  })

  it("draws the description as the markdown it was written in", () => {
    expect(dialog().html()).toContain("<strong>hear ye</strong>")
  })

  it("leaves out what the event does not say", () => {
    const said = dialog({event: event({location: undefined, membersOnly: false, description: ""})})

    expect(said.text()).not.toContain("Witbreuksweg")
    expect(said.text()).not.toContain("Members only")
    expect(said.html()).not.toContain("prose-island")
  })

  it("draws nothing at all where there is no event to draw", () => {
    expect(dialog({event: undefined}).find("img").exists()).toBe(false)
  })

  /* Closing is the dialog's own: the band takes the event out of the address on the way back. */
  it("hands the close back up when the dialog is shut", async () => {
    const wrapper = dialog()

    wrapper.findComponent(stubs.IslandDialog).vm.$emit("update:open", false)
    await wrapper.vm.$nextTick()

    expect(wrapper.emitted("update:open")).toEqual([[false]])
  })
})
