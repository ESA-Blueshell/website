import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount, RouterLinkStub, shallowMount} from "@vue/test-utils"
import EventSignUps from "@/pages/events/EventSignUps.vue"
import {settle} from "../helpers"

// The dialogs portal out of the page and have their own specs; the page only opens them.
const renderingStubs = {
  VMain: {name: "VMain", template: "<main><slot /></main>"},
  RouterLink: RouterLinkStub,
  RemoveSignUpDialog: {name: "RemoveSignUpDialog", props: ["modelValue", "personName"], template: "<div />"},
  EditSignUpDialog: {name: "EditSignUpDialog", props: ["modelValue", "event", "signUp"], template: "<div />"},
}

const {
  mockRoute,
  mockReadEvent,
  mockQuestionType,
  mockEventSignUpKind,
  mockRemoveSignUp,
  mockListEventSignUps,
  mockHandleNetworkError,
  mockBuildCsv,
  mockGetters,
  mockRole,
} = vi.hoisted(() => ({
  mockRemoveSignUp: vi.fn(),
  mockHandleNetworkError: vi.fn(),
  mockBuildCsv: vi.fn(() => "a,b\r\n1,2"),
  mockListEventSignUps: vi.fn(),
  mockGetters: {isBoard: true} as Record<string, unknown>,
  mockRoute: {
    params: {id: "55"},
  },
  mockReadEvent: vi.fn(),
  mockQuestionType: {
    OPEN: "OPEN",
    CHECKBOX: "CHECKBOX",
    RADIO: "RADIO",
  },
  mockEventSignUpKind: {
    GUEST: "GUEST",
    NON_MEMBER: "NON_MEMBER",
    MEMBER: "MEMBER",
  },
  mockRole: {
    ANONYMOUS: "ANONYMOUS",
    GUEST: "GUEST",
    MEMBER: "MEMBER",
    COMMITTEE: "COMMITTEE",
    BOARD: "BOARD",
    TREASURER: "TREASURER",
    ADMIN: "ADMIN",
    SYSTEM: "SYSTEM",
  },
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("vuex", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vuex")>()
  return {
    ...actual,
    useStore: () => ({getters: mockGetters}),
  }
})

vi.mock("@/domains/events", () => ({
  listEventSignUps: mockListEventSignUps,
  removeSignUp: mockRemoveSignUp,
  readEvent: mockReadEvent,
  QuestionType: mockQuestionType,
  EventSignUpKind: mockEventSignUpKind,
  whenOf: () => ({day: "Sat 3 October", hours: "19:00-22:00"}),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: mockHandleNetworkError}))

vi.mock("@/utils/eventSignUpsCsv", () => ({
  buildEventSignUpsCsv: mockBuildCsv,
  eventSignUpsCsvFilename: () => "LAN-signups.csv",
}))

// Partial: the user domain's door loads its whole adapter surface, so the rest of the client
// has to stay real.
vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  Role: mockRole,
  updateEventSignUpById: vi.fn(),
}))

describe("EventSignUps page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetters.isBoard = true
    mockRemoveSignUp.mockResolvedValue(undefined)

    mockReadEvent.mockResolvedValue({
      id: 55,
      title: "LAN",
      signUpForm: {
        questions: [
          {id: 3, idx: 1, type: mockQuestionType.OPEN, label: "Comment"},
          {id: 2, idx: 0, type: mockQuestionType.CHECKBOX, label: "Food", choiceLabels: ["Pizza", "Pasta"]},
        ],
      },
    })

    mockListEventSignUps.mockResolvedValue([
        {
          id: 11,
          version: 0,
          kind: mockEventSignUpKind.MEMBER,
          answers: [
            {questionId: 2, optionSelections: [true, false]},
            {questionId: 3, textResponse: "No peanuts"},
          ],
          user: {
            fullName: "Alice",
            discord: "alice#1234",
            email: "alice@example.com",
            phoneNumber: "123",
          },
        },
        {
          id: 12,
          version: 0,
          kind: mockEventSignUpKind.GUEST,
          answers: [
            {questionId: 2, optionSelections: [true, true]},
          ],
          guest: {
            name: "Bob",
            discord: "bob#5555",
            email: "bob@example.com",
            phoneNumber: "456",
          },
        },
    ])
  })

  it("keeps each signup on its row and computes totals for choice questions", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()

    expect(mockReadEvent).toHaveBeenCalledWith(55)
    expect(mockListEventSignUps).toHaveBeenCalledWith(55)

    const respondents = (wrapper.vm as any).respondents
    expect(respondents).toHaveLength(2)
    expect(respondents[0].person.name).toBe("Alice")
    expect(respondents[0].signUp.id).toBe(11)
    expect(respondents[1].person.name).toBe("Bob")
    expect(respondents[1].signUp.guest.name).toBe("Bob")

    const questions = (wrapper.vm as any).sortedQuestions
    expect(questions[0].id).toBe(2)

  })

  it("sorts by kind on request and gives the signup order back", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([11, 12])

    vm.toggleSort("kind")
    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([12, 11])

    vm.toggleSort("kind")
    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([11, 12])

    vm.toggleSort("kind")
    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([11, 12])
  })

  it("removes a signup and reads the roster back", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    vm.askToRemove(vm.respondents[0])
    expect(vm.removeDialogOpen).toBe(true)
    expect(vm.removeTargetName).toBe("Alice")

    await vm.confirmRemove(false)

    expect(mockRemoveSignUp).toHaveBeenCalledWith(11, false)
    expect(mockListEventSignUps).toHaveBeenCalledTimes(2)
    expect(vm.removeDialogOpen).toBe(false)
  })

  it("offers no removal to a reader who is not board", async () => {
    mockGetters.isBoard = false

    const wrapper = shallowMount(EventSignUps)
    await settle()

    expect((wrapper.vm as any).mayManageSignUps).toBe(false)
  })

  it("asks the api to notify only when the board ticked the box", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    vm.askToRemove(vm.respondents[1])
    await vm.confirmRemove(true)

    expect(mockRemoveSignUp).toHaveBeenCalledWith(12, true)
  })

  it("opens the edit dialog on a row and reads the roster back after a save", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    vm.askToEdit(vm.respondents[1])
    expect(vm.editDialogOpen).toBe(true)
    expect(vm.signUpToEdit.id).toBe(12)

    await vm.onSignUpSaved()

    expect(vm.editDialogOpen).toBe(false)
    expect(mockListEventSignUps).toHaveBeenCalledTimes(2)
  })

  it("keeps the rows it has when the roster cannot be read back", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    mockListEventSignUps.mockResolvedValueOnce(null)
    vm.askToRemove(vm.respondents[0])
    await vm.confirmRemove(false)

    expect(vm.respondents).toHaveLength(2)
  })

  it("has no questions to show when the event carries no sign-up form", async () => {
    mockReadEvent.mockResolvedValueOnce({id: 55, title: "LAN"})

    const wrapper = shallowMount(EventSignUps)
    await settle()

    expect((wrapper.vm as any).sortedQuestions).toEqual([])
  })

  it("downloads the roster as a file named after the event", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()

    const click = vi.fn()
    const anchor = {href: "", download: "", click, remove: vi.fn()} as unknown as HTMLAnchorElement
    const createElement = vi.spyOn(document, "createElement").mockReturnValue(anchor)
    const appendChild = vi.spyOn(document.body, "appendChild").mockImplementation((node) => node)
    const createObjectURL = vi.fn(() => "blob:roster")
    const revokeObjectURL = vi.fn()
    vi.stubGlobal("URL", {createObjectURL, revokeObjectURL})

    ;(wrapper.vm as any).exportCsv()

    expect(mockBuildCsv).toHaveBeenCalled()
    expect(anchor.download).toBe("LAN-signups.csv")
    expect(click).toHaveBeenCalled()
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:roster")

    vi.unstubAllGlobals()
    createElement.mockRestore()
    appendChild.mockRestore()
  })

  it("exports nothing before the event has arrived", async () => {
    mockReadEvent.mockResolvedValueOnce(undefined)

    const wrapper = shallowMount(EventSignUps)
    await settle()
    ;(wrapper.vm as any).exportCsv()

    expect(mockBuildCsv).not.toHaveBeenCalled()
  })

  it("reports a first read it could not make", async () => {
    mockReadEvent.mockRejectedValueOnce(new Error("500"))

    shallowMount(EventSignUps)
    await settle()

    expect(mockHandleNetworkError).toHaveBeenCalled()
  })

  it("reports a removal the api refused, and keeps the row", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    mockRemoveSignUp.mockRejectedValueOnce(new Error("403"))
    vm.askToRemove(vm.respondents[0])
    await vm.confirmRemove(false)

    expect(mockHandleNetworkError).toHaveBeenCalled()
    expect(vm.respondents).toHaveLength(2)
  })

  it("does nothing when a confirmation arrives with no row behind it", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    await vm.confirmRemove(false)

    expect(mockRemoveSignUp).not.toHaveBeenCalled()
  })

  it("closes its dialogs when they are dismissed", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    vm.askToRemove(vm.respondents[0])
    vm.removeDialogOpen = false
    expect(vm.removeDialogOpen).toBe(false)

    vm.askToEdit(vm.respondents[0])
    vm.editDialogOpen = false
    expect(vm.editDialogOpen).toBe(false)
  })

  it("draws a row per respondent, with its kind and the board's actions", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.findAll(".attendees-table tbody tr")).toHaveLength(2)
    expect(wrapper.get('[data-testid="signup-kind-11"]').text()).toBe("Member")
    expect(wrapper.get('[data-testid="signup-kind-12"]').text()).toBe("Guest")
    expect(wrapper.find('[data-testid="signup-remove-btn-11"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="signup-edit-btn-11"]').exists()).toBe(true)
    expect(wrapper.text()).toContain("alice@example.com")
  })

  it("greys out the edit button on a sign-up with nothing to edit", async () => {
    mockReadEvent.mockResolvedValueOnce({id: 55, title: "LAN"})

    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    const editable = (id: number) =>
      (wrapper.get(`[data-testid="signup-edit-btn-${id}"]`).element as HTMLButtonElement).disabled

    expect(editable(11)).toBe(true)
    expect(editable(12)).toBe(false)
  })

  it("draws no actions column for a reader who is not board", async () => {
    mockGetters.isBoard = false

    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.find('[data-testid="signup-remove-btn-11"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="signup-edit-btn-11"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="signup-kind-11"]').text()).toBe("Member")
  })

  it("draws the open answers, the choice matrix and its totals", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.text()).toContain("No peanuts")
    expect(wrapper.text()).toContain("not yet answered")
    expect(wrapper.findAll(".radio-table tfoot td").at(1)?.text()).toBe("2")
    expect(wrapper.findAll(".radio-table tfoot td").at(2)?.text()).toBe("1")
  })

  it("heads the page with the event, when and where, and counts who signed up", async () => {
    mockReadEvent.mockResolvedValueOnce({id: 55, title: "LAN", location: "Esports Lounge Twente", signUpLimit: 24})
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.get(".signups-head__title").text()).toBe("LAN")
    expect(wrapper.get(".signups-head__body").text()).toBe("Sat 3 October, 19:00-22:00 at Esports Lounge Twente.")
    expect(wrapper.getComponent(RouterLinkStub).props("to")).toBe("/events/edit/55")
    const figures = wrapper.getComponent({name: "NumberBand"}).props("figures")
    expect(figures.map((one: {label: string}) => one.label)).toEqual(["signed up", "places", "places left", "members · non-members · guests"])
    expect(figures.at(-1).text).toBe("1 · 0 · 1")
    expect(figures[2].value).toBe(22)
  })

  it("counts no places where the event has no limit, and reads a place-less event plainly", async () => {
    mockReadEvent.mockResolvedValueOnce({id: 55, title: "LAN"})
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.getComponent({name: "NumberBand"}).props("figures")).toHaveLength(2)
    expect(wrapper.get(".signups-head__body").text()).toBe("Sat 3 October, 19:00-22:00.")
    expect(wrapper.find("[data-testid=signups-responses]").exists()).toBe(false)
  })

  it("finds an attendee by any way to reach them, and says when nobody answers to it", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    await wrapper.get("[data-testid=signups-search] input").setValue("bob#5")
    expect(wrapper.findAll(".attendees-table tbody tr")).toHaveLength(1)

    await wrapper.get("[data-testid=signups-search] input").setValue("nobody")
    expect(wrapper.get(".signups-empty").text()).toBe("Nobody here answers to that.")
  })

  it("says nobody has signed up where nobody has", async () => {
    mockListEventSignUps.mockResolvedValueOnce([])
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.get(".signups-empty").text()).toBe("Nobody has signed up yet.")
  })

  it("names the order the kind column is in, as it cycles", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()
    const sort = () => wrapper.get('[data-testid="signups-kind-sort"]')

    expect(sort().attributes("aria-label")).toBe("Sort by kind, as signed up")
    await sort().trigger("click")
    expect(sort().attributes("aria-label")).toBe("Sort by kind, guests first")
    await sort().trigger("click")
    expect(sort().attributes("aria-label")).toBe("Sort by kind, members first")
  })

  it("sorts the drawn rows when the kind header is clicked", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    await wrapper.get('[data-testid="signups-kind-sort"]').trigger("click")

    const kinds = wrapper.findAll(".attendees-table tbody tr td:nth-child(3)").map((td) => td.text())
    expect(kinds).toEqual(["Guest", "Member"])
  })

  it("marks a sign-up made before a question existed as unanswered", async () => {
    mockListEventSignUps.mockResolvedValueOnce([
      {id: 13, version: 0, kind: mockEventSignUpKind.MEMBER, answers: [], user: {fullName: "Cara"}},
    ])

    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    expect(wrapper.findAll(".radio-table [data-answer=missing]")).toHaveLength(2)
    expect(wrapper.text()).toContain("not yet answered")
  })

  it("opens each dialog from the row it was asked on", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    await wrapper.get('[data-testid="signup-edit-btn-12"]').trigger("click")
    const edit = wrapper.findComponent({name: "EditSignUpDialog"})
    expect(edit.props("modelValue")).toBe(true)
    expect(edit.props("signUp")).toMatchObject({id: 12})

    await wrapper.get('[data-testid="signup-remove-btn-11"]').trigger("click")
    const remove = wrapper.findComponent({name: "RemoveSignUpDialog"})
    expect(remove.props("modelValue")).toBe(true)
    expect(remove.props("personName")).toBe("Alice")
  })

  it("closes a dialog the reader dismissed", async () => {
    const wrapper = mount(EventSignUps, {global: {stubs: renderingStubs}})
    await settle()

    await wrapper.get('[data-testid="signup-edit-btn-12"]').trigger("click")
    await wrapper.findComponent({name: "EditSignUpDialog"}).vm.$emit("update:modelValue", false)
    expect((wrapper.vm as any).editDialogOpen).toBe(false)

    await wrapper.get('[data-testid="signup-remove-btn-11"]').trigger("click")
    await wrapper.findComponent({name: "RemoveSignUpDialog"}).vm.$emit("update:modelValue", false)
    expect((wrapper.vm as any).removeDialogOpen).toBe(false)
  })
})
