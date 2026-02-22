import {beforeEach, describe, expect, it, vi} from "vitest"
import {shallowMount} from "@vue/test-utils"
import EventSignUps from "@/pages/events/EventSignUps.vue"
import {settle} from "../helpers"

const {
  mockRoute,
  mockFindEventById,
  mockFindEventSignUpsByEventId,
  mockQuestionType,
} = vi.hoisted(() => ({
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
}))

vi.mock("vue-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("vue-router")>()
  return {
    ...actual,
    useRoute: () => mockRoute,
  }
})

vi.mock("@/services/api", () => ({
  findEventById: mockFindEventById,
  findEventSignUpsByEventId: mockFindEventSignUpsByEventId,
  QuestionType: mockQuestionType,
}))

describe("EventSignUps page", () => {
  beforeEach(() => {
    vi.clearAllMocks()

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

  it("maps signup responses and computes totals for choice questions", async () => {
    const wrapper = shallowMount(EventSignUps)
    await settle()

    expect(mockFindEventById).toHaveBeenCalledWith({path: {id: 55}})
    expect(mockFindEventSignUpsByEventId).toHaveBeenCalledWith({path: {eventId: 55}})

    expect((wrapper.vm as any).responses).toHaveLength(2)
    expect((wrapper.vm as any).responses[0].person.fullName).toBe("Alice")

    const questions = (wrapper.vm as any).sortedQuestions
    expect(questions[0].id).toBe(2)

    const totals = (wrapper.vm as any).totalForQuestion(questions[0])
    expect(totals).toEqual([2, 1])
  })
})
