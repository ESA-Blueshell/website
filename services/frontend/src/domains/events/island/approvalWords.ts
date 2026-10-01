import {EventField} from "@/services/api"

// Read when called, not when loaded: the door loads this file under tests that mock the api in part.
const fieldWord = (field: EventField): string => {
  switch (field) {
    case EventField.TITLE: return "title"
    case EventField.DESCRIPTION: return "description"
    case EventField.LOCATION: return "location"
    case EventField.TIMES: return "times"
    case EventField.PRICES: return "prices"
    case EventField.MEMBERS_ONLY: return "who may come"
    case EventField.SIGN_UP: return "sign-ups"
    default: return "committee"
  }
}

/** What a re-approval changed, in words: "Changed: title and times", or a plain line where nothing was kept. */
export function changesSaid(changes: EventField[]): string {
  if (changes.length === 0) return "Changed since it was approved"
  const words = changes.map(fieldWord)
  const named = words.length === 1 ? words[0] : `${words.slice(0, -1).join(", ")} and ${words.at(-1)}`
  return `Changed: ${named}`
}
