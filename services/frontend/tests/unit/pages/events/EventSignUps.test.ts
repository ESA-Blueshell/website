import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EventSignUps from "@/pages/events/EventSignUps.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockFindEventById,
  mockFindEventSignUpsByEventId,
  mockQuestionType,
  mockEventSignUpKind,
  mockDeleteEventSignup,
  mockGetters,
  mockRole,
} = vi.hoisted(() => ({
  mockDeleteEventSignup: vi.fn(),
  mockGetters: {hasBoardAuthority: true} as Record<string, unknown>,
  mockRoute: {
    params: {id: "55"},
  },
  mockFindEventById: vi.fn(),
  mockFindEventSignUpsByEventId: vi.fn(),
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

vi.mock("@/services/api", () => ({
  deleteEventSignup: mockDeleteEventSignup,
  findEventById: mockFindEventById,
  findEventSignUpsByEventId: mockFindEventSignUpsByEventId,
  QuestionType: mockQuestionType,
  EventSignUpKind: mockEventSignUpKind,
  Role: mockRole,
  updateEventSignUpById: vi.fn(),
}))

describe("EventSignUps page", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockGetters.hasBoardAuthority = true
    mockDeleteEventSignup.mockResolvedValue({data: undefined})

    mockFindEventById.mockResolvedValue({
      data: {
        id: 55,
        title: "LAN",
        signUpForm: {
          questions: [
            {id: 3, idx: 1, type: mockQuestionType.OPEN, label: "Comment"},
            {id: 2, idx: 0, type: mockQuestionType.CHECKBOX, label: "Food", choiceLabels: ["Pizza", "Pasta"]},
          ],
        },
      },
    })

    mockFindEventSignUpsByEventId.mockResolvedValue({
      data: [
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
      ],
    })
  })

  it("keeps each signup on its row and computes totals for choice questions", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()

    expect(mockFindEventById).toHaveBeenCalledWith({path: {id: 55}})
    expect(mockFindEventSignUpsByEventId).toHaveBeenCalledWith({path: {eventId: 55}})

    const respondents = (wrapper.vm as any).respondents
    expect(respondents).toHaveLength(2)
    expect(respondents[0].person.name).toBe("Alice")
    expect(respondents[0].signUp.id).toBe(11)
    expect(respondents[1].person.name).toBe("Bob")
    expect(respondents[1].signUp.guest.name).toBe("Bob")

    const questions = (wrapper.vm as any).sortedQuestions
    expect(questions[0].id).toBe(2)

    const totals = (wrapper.vm as any).totalForQuestion(questions[0])
    expect(totals).toEqual([2, 1])
  })

  it("sorts by kind on request and gives the signup order back", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()
    const vm = wrapper.vm as any

    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([11, 12])

    vm.toggleKindSort()
    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([12, 11])

    vm.toggleKindSort()
    expect(vm.respondents.map((row: any) => row.signUp.id)).toEqual([11, 12])

    vm.toggleKindSort()
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

    expect(mockDeleteEventSignup).toHaveBeenCalledWith({
      path: {id: 11},
      query: {notify: false},
      throwOnError: true,
    })
    expect(mockFindEventSignUpsByEventId).toHaveBeenCalledTimes(2)
    expect(vm.removeDialogOpen).toBe(false)
  })

  it("offers no removal to a reader who is not board", async () => {
    mockGetters.hasBoardAuthority = false

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

    expect(mockDeleteEventSignup).toHaveBeenCalledWith({
      path: {id: 12},
      query: {notify: true},
      throwOnError: true,
    })
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
    expect(mockFindEventSignUpsByEventId).toHaveBeenCalledTimes(2)
  })
})
