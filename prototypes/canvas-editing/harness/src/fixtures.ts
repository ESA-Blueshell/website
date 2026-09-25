import seed from "./fixtures.json"

/** The dev seed's records, as the canvas boards read them, with invented attendees for sign-ups. */
const PEOPLE: [string, string, string][] = [
  ["Sem de Wit", "semmie", "MEMBER"], ["Noor Bakker", "noorb", "MEMBER"], ["Luuk Jansen", "luukj", "MEMBER"],
  ["Fleur Visser", "fleurtje", "NON_MEMBER"], ["Daan Smit", "daan.s", "MEMBER"], ["Iris Mulder", "irisplays", "GUEST"],
  ["Milan de Boer", "milanb", "MEMBER"], ["Sara Kok", "sarak", "NON_MEMBER"], ["Thijs Peters", "thijsp", "MEMBER"],
]

export const fixtures = {
  ...seed,
  eventDetailsById: Object.fromEntries(seed.events.map((one: {id: number}) => [String(one.id), one])),
  eventSignUpsByEventId: {"108200": PEOPLE.map(([fullName, discord, kind], index) => ({
    id: 900 + index, eventId: 108200, kind, createdAt: `2026-09-${String(14 + (index % 6)).padStart(2, "0")}T1${index % 10}:12:00.000Z`,
    user: {id: 700 + index, fullName, discord, email: `${discord.replace(".", "")}@example.com`, phoneNumber: `06${12345670 + index * 1111}`},
    answers: [],
  }))},
}
