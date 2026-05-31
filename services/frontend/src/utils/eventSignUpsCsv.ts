import {
  type AnswerResponse,
  type EventResponse,
  type EventSignUpResponse,
  type QuestionResponse,
  QuestionType,
} from "@/services/api"
import {safeFormatISO} from "@/utils/datetime"

const SUBMITTED_AT_FORMAT = "yyyy-MM-dd HH:mm"
const FIXED_HEADERS = ["Submitted at", "Name", "Discord", "Email", "Phone"] as const

type PersonColumns = {name: string; discord: string; email: string; phone: string}

function personColumns(signUp: EventSignUpResponse): PersonColumns {
  const source = signUp.user ?? signUp.guest
  return {
    name: signUp.user?.fullName ?? signUp.guest?.name ?? "",
    discord: source?.discord ?? "",
    email: source?.email ?? "",
    phone: source?.phoneNumber ?? "",
  }
}

/** Questions in display order, excluding DESCRIPTION blocks which carry no answer. */
function exportableQuestions(event: EventResponse): QuestionResponse[] {
  const questions = event.signUpForm?.questions ?? []
  return questions
    .filter((q) => q.type !== QuestionType.DESCRIPTION)
    .sort((a, b) => a.idx - b.idx)
}

function answerCell(question: QuestionResponse, answer: AnswerResponse | undefined): string {
  if (!answer) return ""
  if (question.type === QuestionType.OPEN) return answer.textResponse ?? ""
  const labels = question.choiceLabels ?? []
  const selections = answer.optionSelections ?? []
  return labels.filter((_, idx) => selections[idx]).join("; ")
}

/** Prefix cells that a spreadsheet would parse as a formula, defeating CSV injection. */
function neutralizeFormula(value: string): string {
  return /^[=+\-@\t\r]/.test(value) ? `'${value}` : value
}

function escapeField(value: string): string {
  const guarded = neutralizeFormula(value)
  return /[",\r\n]/.test(guarded) ? `"${guarded.replace(/"/g, '""')}"` : guarded
}

function toRow(fields: string[]): string {
  return fields.map(escapeField).join(",")
}

/**
 * Renders an event's sign-ups as RFC 4180 CSV, mirroring how Google Forms responses land in
 * a Sheet: one header row, one row per respondent, and one column per form question.
 */
export function buildEventSignUpsCsv(event: EventResponse, signUps: EventSignUpResponse[]): string {
  const questions = exportableQuestions(event)
  const header = [...FIXED_HEADERS, ...questions.map((q) => q.label)]

  const rows = signUps.map((signUp) => {
    const person = personColumns(signUp)
    const answersByQuestion = new Map(signUp.answers.map((a) => [a.questionId, a]))
    const answerCells = questions.map((q) => answerCell(q, answersByQuestion.get(q.id)))
    return [
      safeFormatISO(signUp.createdAt, SUBMITTED_AT_FORMAT),
      person.name,
      person.discord,
      person.email,
      person.phone,
      ...answerCells,
    ]
  })

  return [header, ...rows].map(toRow).join("\r\n")
}

export function eventSignUpsCsvFilename(eventTitle: string | undefined): string {
  const base = (eventTitle ?? "")
    .trim()
    .replace(/[^\w\- ]+/g, "")
    .replace(/\s+/g, "-")
  return `${base || "event"}-signups.csv`
}
