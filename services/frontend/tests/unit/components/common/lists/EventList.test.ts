import {describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventList from "@/components/common/lists/EventList.vue"

vi.mock("vue-router", () => ({
  useRoute: () => ({hash: ""}),
}))

vi.mock("@/components/common/cards/EventCard.vue", () => ({
  default: {
    name: "EventCard",
    template: "<div />",
  },
}))

describe("EventList", () => {
  it("renders empty state and forwards card events", async () => {
    const empty = mount(EventList, {
      props: {
        events: [],
        eventSignUps: [],
        committees: [],
      },
      global: {
        stubs: {
          EventCard: true,
        },
      },
    })

    expect(empty.text()).toContain("No upcoming events found")

    const withData = mount(EventList, {
      props: {
        events: [{id: 10, title: "Lan Party"}],
        eventSignUps: [],
        committees: [],
      },
      global: {
        stubs: {
          EventCard: {
            template: "<button @click=\"$emit('update:event', { id: 10, title: 'Updated' })\">event-card</button>",
          },
        },
      },
    })

    await withData.find("button").trigger("click")
    expect(withData.emitted("update:event")?.[0]).toEqual([{id: 10, title: "Updated"}])
  })
})
