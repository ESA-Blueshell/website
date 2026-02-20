import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventCard from "@/components/common/cards/EventCard.vue"

const {
  mockMarkdownToHtml,
  mockApproveEvent,
  mockDeleteEventById,
  mockDownloadEventBanner,
  mockGoto,
  mockRouterPush,
  mockStoreCommit,
  mockCreateIcs,
  mockRoute,
  mockStoreGetters,
} = vi.hoisted(() => ({
  mockMarkdownToHtml: vi.fn((text: string) => `<p>${text}</p>`),
  mockApproveEvent: vi.fn(),
  mockDeleteEventById: vi.fn(),
  mockDownloadEventBanner: vi.fn(),
  mockGoto: vi.fn(),
  mockRouterPush: vi.fn(),
  mockStoreCommit: vi.fn(),
  mockCreateIcs: vi.fn(),
  mockRoute: {hash: ""},
  mockStoreGetters: {
    isMember: true,
    isLoggedIn: true,
    isBoard: true,
  },
}))

vi.mock("@/plugins/markdownToHtml.ts", () => ({
  default: mockMarkdownToHtml,
}))

vi.mock("@/services/api", () => ({
  approveEvent: mockApproveEvent,
  deleteEventById: mockDeleteEventById,
  downloadEventBanner: mockDownloadEventBanner,
}))

vi.mock("@/plugins/goto.ts", () => ({
  $goto: mockGoto,
}))

vi.mock("@/plugins/store.ts", () => ({
  default: {
    getters: mockStoreGetters,
    commit: mockStoreCommit,
  },
}))

vi.mock("vue-router", () => ({
  useRouter: () => ({
    push: mockRouterPush,
  }),
  useRoute: () => mockRoute,
}))

vi.mock("vuetify", () => ({
  useTheme: () => ({
    global: {
      current: {
        value: {
          dark: false,
        },
      },
    },
  }),
}))

vi.mock("ics", () => ({
  createEvent: mockCreateIcs,
}))

vi.mock("@/components/form/EventSignUpForm.vue", () => ({
  default: {
    name: "EventSignUpForm",
    template: "<div />",
  },
}))

const baseEvent = {
  id: 10,
  title: "Lan Party",
  description: "Bring your setup",
  location: "Discord server",
  startTime: "2099-02-20T12:00:00.000Z",
  endTime: "2099-02-20T14:00:00.000Z",
  approved: true,
  membersOnly: false,
  committeeId: 77,
  signUp: true,
  signUpCount: 2,
  banner: true,
}

describe("EventCard", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockApproveEvent.mockResolvedValue({data: {...baseEvent, approved: false}})
    mockDeleteEventById.mockResolvedValue({})
    mockDownloadEventBanner.mockResolvedValue({data: new Blob(["image"])})
    mockCreateIcs.mockImplementation((_opts, cb) => cb(null, "BEGIN:VCALENDAR"))
  })

  it("toggles event approval and emits update", async () => {
    const wrapper = mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [{id: 77, name: "Board"}],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    await (wrapper.vm as any).toggleEventApproved()
    expect(mockApproveEvent).toHaveBeenCalledWith({
      path: {id: 10},
      query: {approved: false},
      throwOnError: true,
    })
    expect(wrapper.emitted("update:event")?.[0]).toEqual([{...baseEvent, approved: false}])
  })

  it("deletes event and emits delete:event", async () => {
    const wrapper = mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [{id: 77, name: "Board"}],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    await (wrapper.vm as any).confirmDeleteEvent()
    expect(mockDeleteEventById).toHaveBeenCalledWith({path: {eventId: 10}})
    expect(mockStoreCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("Deleted"))
    expect(wrapper.emitted("delete:event")?.[0]).toEqual([10])
  })

  it("navigates to discord or maps based on location", () => {
    const discordWrapper = mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [{id: 77, name: "Board"}],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    ;(discordWrapper.vm as any).findLocation()
    expect(mockGoto).toHaveBeenCalledWith("https://discord.gg/23YMFQy")

    const mapWrapper = mount(EventCard, {
      props: {
        event: {
          ...baseEvent,
          location: "Enschede station",
        },
        committees: [{id: 77, name: "Board"}],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    ;(mapWrapper.vm as any).findLocation()
    expect(mockGoto).toHaveBeenCalledWith(expect.stringContaining("google.com/maps/search"))
  })

  it("copies share link and shows snackbar message", async () => {
    const wrapper = mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    await (wrapper.vm as any).copyShareLink()
    expect(navigator.clipboard.writeText).toHaveBeenCalledWith(expect.stringContaining("#10"))
    expect(mockStoreCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      "Link for Lan Party copied to clipboard",
    )
  })

  it("creates and downloads an ICS file", () => {
    const append = vi.spyOn(document.body, "appendChild")
    const remove = vi.spyOn(HTMLElement.prototype, "remove")

    const wrapper = mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    ;(wrapper.vm as any).downloadIcs()
    expect(mockCreateIcs).toHaveBeenCalled()
    expect(append).toHaveBeenCalled()
    expect(remove).toHaveBeenCalled()
  })

  it("loads banner image blob when event has banner", () => {
    mount(EventCard, {
      props: {
        event: baseEvent,
        committees: [],
        signUps: [],
      },
      global: {
        stubs: {
          EventSignUpForm: true,
          DeletionConfirmationDialog: true,
        },
      },
    })

    expect(mockDownloadEventBanner).toHaveBeenCalledWith({
      path: {eventId: 10},
      throwOnError: true,
      responseType: "blob",
    })
  })
})
