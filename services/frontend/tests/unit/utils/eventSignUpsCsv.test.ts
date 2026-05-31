import {beforeAll, describe, expect, it} from "vitest"
import {Settings} from "luxon"
import {
  type EventResponse,
  type EventSignUpResponse,
  type QuestionResponse,
  QuestionType,
} from "@/services/api"
import {buildEventSignUpsCsv, eventSignUpsCsvFilename} from "@/utils/eventSignUpsCsv"

// Pin the zone so the formatted "Submitted at" column is deterministic across machines.
beforeAll(() => {
  Settings.defaultZone = "utc"
})

function question(partial: Partial<QuestionResponse> & {id: number; idx: number; type: QuestionType}): QuestionResponse {
  return {
    label: `Question ${partial.id}`,
    surveyId: 1,
    createdAt: "2026-01-01T00:00:00.000Z",
    updatedAt: "2026-01-01T00:00:00.000Z",
    version: 0,
    ...partial,
  } as QuestionResponse
}

function event(questions: QuestionResponse[], title = "Game Night"): EventResponse {
  return {
    id: 500,
    title,
    signUpForm: {id: 1, questions, responseCount: questions.length, createdAt: "", updatedAt: "", version: 0},
  } as unknown as EventResponse
}

function signUp(partial: Partial<EventSignUpResponse>): EventSignUpResponse {
  return {
    id: 1,
    eventId: 500,
    answers: [],
    createdAt: "2026-02-20T12:34:00.000Z",
    updatedAt: "2026-02-20T12:34:00.000Z",
    version: 0,
    ...partial,
  } as EventSignUpResponse
}

function rows(csv: string): string[] {
  return csv.split("\r\n")
}

describe("buildEventSignUpsCsv", () => {
  it("emits identity columns followed by one column per question in idx order", () => {
    const questions = [
      question({id: 11, idx: 1, type: QuestionType.OPEN, label: "Comments"}),
      question({id: 10, idx: 0, type: QuestionType.OPEN, label: "Why join"}),
    ]
    const csv = buildEventSignUpsCsv(event(questions), [])
    expect(rows(csv)[0]).toBe("Submitted at,Name,Discord,Email,Phone,Why join,Comments")
  })

  it("excludes DESCRIPTION blocks since they carry no answer", () => {
    const questions = [
      question({id: 10, idx: 0, type: QuestionType.DESCRIPTION, label: "Intro text"}),
      question({id: 11, idx: 1, type: QuestionType.OPEN, label: "Name tag"}),
    ]
    const csv = buildEventSignUpsCsv(event(questions), [])
    expect(rows(csv)[0]).toBe("Submitted at,Name,Discord,Email,Phone,Name tag")
  })

  it("renders a member sign-up from the user fields", () => {
    const questions = [question({id: 10, idx: 0, type: QuestionType.OPEN})]
    const su = signUp({
      user: {
        id: 1,
        fullName: "Ada Lovelace",
        discord: "ada#0001",
        email: "ada@example.com",
        phoneNumber: "0612345678",
        createdAt: "",
        updatedAt: "",
        version: 0,
      },
      answers: [{id: 1, questionId: 10, textResponse: "Love games", createdAt: "", updatedAt: "", version: 0}],
    })
    const csv = buildEventSignUpsCsv(event(questions), [su])
    expect(rows(csv)[1]).toBe("2026-02-20 12:34,Ada Lovelace,ada#0001,ada@example.com,0612345678,Love games")
  })

  it("falls back to guest fields when there is no user", () => {
    const csv = buildEventSignUpsCsv(event([]), [
      signUp({
        guest: {
          id: 9,
          name: "Guesty",
          discord: "guest#1234",
          email: "guest@example.com",
          phoneNumber: "0600000000",
          createdAt: "",
          updatedAt: "",
          version: 0,
        },
      }),
    ])
    expect(rows(csv)[1]).toBe("2026-02-20 12:34,Guesty,guest#1234,guest@example.com,0600000000")
  })

  it("joins the selected labels for choice questions and leaves missing answers blank", () => {
    const questions = [
      question({id: 10, idx: 0, type: QuestionType.CHECKBOX, label: "Snacks", choiceLabels: ["Pizza", "Chips", "Fruit"]}),
      question({id: 11, idx: 1, type: QuestionType.RADIO, label: "Drink", choiceLabels: ["Cola", "Water"]}),
    ]
    const su = signUp({
      answers: [
        {id: 1, questionId: 10, optionSelections: [true, false, true], createdAt: "", updatedAt: "", version: 0},
        // no answer for question 11 -> blank cell
      ],
    })
    const cells = rows(buildEventSignUpsCsv(event(questions), [su]))[1].split(",")
    expect(cells.at(-2)).toBe("Pizza; Fruit")
    expect(cells.at(-1)).toBe("")
  })

  it("escapes commas, quotes and newlines per RFC 4180", () => {
    const questions = [question({id: 10, idx: 0, type: QuestionType.OPEN, label: "Note"})]
    const su = signUp({
      answers: [{id: 1, questionId: 10, textResponse: 'a, "b"\nc', createdAt: "", updatedAt: "", version: 0}],
    })
    const csv = buildEventSignUpsCsv(event(questions), [su])
    expect(csv).toContain('"a, ""b""\nc"')
  })

  it("neutralizes spreadsheet formulas to prevent CSV injection", () => {
    const questions = [question({id: 10, idx: 0, type: QuestionType.OPEN, label: "Note"})]
    const su = signUp({
      answers: [{id: 1, questionId: 10, textResponse: "=HYPERLINK(\"http://evil\")", createdAt: "", updatedAt: "", version: 0}],
    })
    const csv = buildEventSignUpsCsv(event(questions), [su])
    expect(csv).toContain("'=HYPERLINK")
  })
})

describe("eventSignUpsCsvFilename", () => {
  it("slugifies the event title", () => {
    expect(eventSignUpsCsvFilename("Game Night: 2026!")).toBe("Game-Night-2026-signups.csv")
  })

  it("falls back to a default base when the title is empty", () => {
    expect(eventSignUpsCsvFilename(undefined)).toBe("event-signups.csv")
    expect(eventSignUpsCsvFilename("   ")).toBe("event-signups.csv")
  })
})
