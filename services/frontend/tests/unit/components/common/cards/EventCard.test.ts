import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import EventCard from "@/components/common/cards/EventCard.vue"

const {
  mockMarkdownToHtml,
  mockApproveEvent,
  mockDeleteEventById,
  mockDeleteEventSignup,
  mockDownloadEventBanner,
  mockGoto,
  mockRouterPush,
  mockStoreCommit,
  mockCreateIcs,
  mockRoute,
  mockStoreGetters,
  mockHandleNetworkError,
} = vi.hoisted(() => ({
  mockMarkdownToHtml: vi.fn((text: string) => `<p>${text}</p>`),
  mockApproveEvent: vi.fn(),
  mockDeleteEventById: vi.fn(),
  mockDeleteEventSignup: vi.fn(),
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
  mockHandleNetworkError: vi.fn(),
}))

vi.mock("@/plugins/markdownToHtml.ts", () => ({
  default: mockMarkdownToHtml,
}))

vi.mock("@/services/api", () => ({
  apiUrl: (path: string) => `https://api.test${path}`,
  approveEvent: mockApproveEvent,
  deleteEventById: mockDeleteEventById,
  deleteEventSignup: mockDeleteEventSignup,
  downloadEventBanner: mockDownloadEventBanner,
}))

vi.mock("@/plugins/handleNetworkError.ts", () => ({
  $handleNetworkError: mockHandleNetworkError,
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

vi.mock("vuetify", async (importOriginal) => {
  const {withVuetify} = await import("../../../helpers/testUtils")
  return withVuetify(importOriginal, {
    useTheme: () => ({global: {current: {value: {dark: false}}}}),
  })
})

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
  banner: {
    eventId: 10,
    fileId: 4,
    version: 0,
    createdAt: "2099-01-01T00:00:00.000Z",
    updatedAt: "2099-01-01T00:00:00.000Z",
    image: {
      url: "/files/public/event-banners/lan.webp",
      path: "event-banners/lan.webp",
      width: 2560,
      height: 1440,
      renditions: [
        {url: "/files/public/event-banners/lan-640.webp", width: 640},
        {url: "/files/public/event-banners/lan-1280.webp", width: 1280},
      ],
    },
  },
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
    expect(mockDeleteEventById).toHaveBeenCalledWith({path: {eventId: 10}, throwOnError: true})
    expect(mockStoreCommit).toHaveBeenCalledWith("setStatusSnackbarMessage", expect.stringContaining("Deleted"))
    expect(wrapper.emitted("delete:event")?.[0]).toEqual([10])
  })

  // The client resolves with an `error` unless asked to throw, so before `throwOnError`
  // the catch below could not run and a refused delete still read "Deleted".
  it("a refused delete says so, and the event stays in the list", async () => {
    mockDeleteEventById.mockRejectedValueOnce(new Error("forbidden"))

    const wrapper = mount(EventCard, {
      props: {event: baseEvent, committees: [{id: 77, name: "Board"}], signUps: []},
      global: {stubs: {EventSignUpForm: true, DeletionConfirmationDialog: true}},
    })

    await (wrapper.vm as any).confirmDeleteEvent()

    expect(mockStoreCommit).toHaveBeenCalledWith(
      "setStatusSnackbarMessage",
      expect.stringContaining("Couldn't delete"),
    )
    expect(wrapper.emitted("delete:event")).toBeUndefined()
    // Without this the client would resolve on a 403 and the catch above could not run.
    expect(mockDeleteEventById).toHaveBeenCalledWith(
      expect.objectContaining({throwOnError: true}),
    )
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

  const mountCard = (event: unknown) => mount(EventCard, {
    props: {
      event,
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

  it("draws the art the event answers with, without fetching it itself", () => {
    const wrapper = mountCard(baseEvent)

    const background = (wrapper.vm as any).cardStyle.backgroundImage
    expect(background).toContain("image-set(")
    // The copy nearest the drawn width for a normal screen, and twice that for a dense one.
    expect(background).toContain("url('https://api.test/files/public/event-banners/lan-640.webp') 1x")
    expect(background).toContain("url('https://api.test/files/public/event-banners/lan-1280.webp') 2x")
    expect(mockDownloadEventBanner).not.toHaveBeenCalled()
  })

  it("draws a single stored width as a plain url", () => {
    const wrapper = mountCard({
      ...baseEvent,
      banner: {
        ...baseEvent.banner,
        image: {...baseEvent.banner.image, renditions: []},
      },
    })

    const background = (wrapper.vm as any).cardStyle.backgroundImage
    expect(background).toContain("url('https://api.test/files/public/event-banners/lan.webp')")
    expect(background).not.toContain("image-set(")
  })

  it("draws no art for an event that carries none", () => {
    const wrapper = mountCard({...baseEvent, banner: null})

    expect((wrapper.vm as any).cardStyle.backgroundImage).toBeUndefined()
    expect(mockDownloadEventBanner).not.toHaveBeenCalled()
  })
})
