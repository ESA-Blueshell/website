import {EventField} from "@/services/api"

// Read when called, not when loaded: the door loads this file under tests that mock the api in part.
const fieldWord = (field: EventField): string => {
  switch (field) {
    case EventField.TITLE: return "title"
    case EventField.DESCRIPTION: return "description"
    case EventField.LOCATION: return "location"
    case EventField.TIMES: return "times"
    case EventField.PRICES: return "prices"
    case EventField.MEMBERS_ONLY: return "members only"
    case EventField.SIGN_UP: return "sign-ups"
    case EventField.COMMITTEE: return "committee"
    case EventField.PINGED_ROLES: return "pinged roles"
    case EventField.GAMES: return "games"
    case EventField.BANNER: return "banner"
    case EventField.SIGN_UP_FORM: return "sign-up form"
    // A field the api added later is named as it is sent, never as another one.
    default: return String(field).toLowerCase().replaceAll("_", " ")
  }
}

/** What a re-approval changed, in words: "Changed: title and times", or a plain line where nothing was kept. */
export function changesSaid(changes: EventField[]): string {
  if (changes.length === 0) return "Changed since it was approved"
  const words = changes.map(fieldWord)
  const named = words.length === 1 ? words[0] : `${words.slice(0, -1).join(", ")} and ${words.at(-1)}`
  return `Changed: ${named}`
}
