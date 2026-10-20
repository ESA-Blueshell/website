/** A person's one bank account as it is typed in: declarations are paid to it and incasso collects from it. */
export interface BankAccount {
  holder: string
  iban: string
  /** Needed for a bank outside the Netherlands, optional for a Dutch one; empty where not given. */
  bic: string
}

/**
 * What the site shows of an account without opening it: the country and the last two digits.
 * It is kept open beside the sealed account, so the reader can tell which account is on file.
 */
export interface BankAccountPreview {
  preview: string
  addedAt: string
}

/** An account by its country code and last two characters, as the api answers a preview. */
export const maskedIban = (country: string, lastTwo: string): string => `${country} •• … ••${lastTwo}`

/** The IBAN check digits, so a typo is caught before the treasurer's bank refuses the transfer. */
export function ibanValid(iban: string): boolean {
  const held = plain(iban)
  if (!/^[A-Z]{2}\d{2}[A-Z0-9]{11,30}$/.test(held)) return false
  const moved = held.slice(4) + held.slice(0, 4)
  const digits = moved.replaceAll(/[A-Z]/g, letter => String(letter.charCodeAt(0) - 55))
  let rest = 0
  for (const digit of digits) rest = (rest * 10 + Number(digit)) % 97
  return rest === 1
}

/** A Dutch bank is reached by the IBAN alone; any other needs its BIC. TWIN: `bicFor` in the api's BankAccounts. */
export const needsBic = (iban: string): boolean => {
  const country = plain(iban).slice(0, 2)
  return country.length === 2 && country !== "NL"
}

/**
 * What each country's IBAN holds after its country letters and check digits, as the SWIFT IBAN
 * registry writes it: a count, then a for capitals, n for digits or c for either.
 */
const BBAN: Record<string, string> = {
  AD: "4n4n12c", AT: "5n11n", BE: "3n7n2n", BG: "4a4n2n8c", CH: "5n12c", CY: "3n5n16c", CZ: "4n6n10n",
  DE: "8n10n", DK: "4n9n1n", EE: "2n2n11n1n", ES: "4n4n1n1n10n", FI: "3n11n", FR: "5n5n11c2n",
  GB: "4a6n8n", GI: "4a15c", GR: "3n4n16c", HR: "7n10n", HU: "3n4n1n15n1n", IE: "4a6n8n", IS: "4n2n6n10n",
  IT: "1a5n5n12c", LI: "5n12c", LT: "5n11n", LU: "3n13c", LV: "4a13c", MC: "5n5n11c2n", MT: "4a5n18c",
  NL: "4a10n", NO: "4n6n1n", PL: "8n16n", PT: "4n4n11n2n", RO: "4a16c", SE: "3n16n1n", SI: "5n8n2n",
  SK: "4n6n10n", SM: "1a5n5n12c", VA: "3n15n",
}

export type Slot = "a" | "n" | "c"

/** Every position of a country's IBAN: two capitals, two check digits, then its own layout. */
export function ibanShape(country: string): Slot[] | null {
  const layout = BBAN[country]
  if (!layout) return null
  const slots: Slot[] = ["a", "a", "n", "n"]
  for (const [, count, kind] of layout.matchAll(/(\d+)([anc])/g)) slots.push(...Array<Slot>(Number(count)).fill(kind as Slot))
  return slots
}

/** How long an IBAN is in each country that pays in euros or sits in SEPA. */
export const IBAN_LENGTHS: Record<string, number> = Object.fromEntries(
  Object.keys(BBAN).map(country => [country, ibanShape(country)!.length]))

/** Whether [character] may stand in a position of kind [slot]. */
export const fits = (character: string, slot: Slot): boolean =>
  slot === "a" ? /[A-Z]/.test(character) : slot === "n" ? /\d/.test(character) : /[A-Z0-9]/.test(character)

const ANY: Slot[] = ["a", "a", "n", "n", ...Array<Slot>(30).fill("c")]

/* Until the country is typed the Dutch layout is shown, since most members bank in the Netherlands. */
/** Every position of the IBAN being typed, as far as its first letters tell. */
export const ibanShapeFor = (held: string): Slot[] => {
  const country = ibanCountry(held)
  return held.length < 2 ? ibanShape("NL")! : country ? ibanShape(country) ?? ANY : ANY
}

/* What each empty position of an IBAN shows: the country's letters, 00 for the check digits, then
   letters and digits counted up, so NL reads NL00 ABCD 0123 4567 89. */
export const ibanSample = (held: string, shape: Slot[]): string => {
  let letters = 0
  let digits = 0
  return shape.map((slot, index) => {
    if (index < 2) return held[index] ?? "NL"[index]
    if (index < 4) return "0"
    if (slot === "a") return String.fromCharCode(65 + (letters++ % 26))
    return String(digits++ % 10)
  }).join("")
}

export const inFours = (held: string): string => held.replaceAll(/(.{4})(?=.)/g, "$1 ")

/** A BIC: the bank's four letters, the country's two, two for the place and three for a branch. */
export const BIC_SHAPE: Slot[] = ["a", "a", "a", "a", "a", "a", "c", "c", "c", "c", "c"]

export const bicSample = (country: string | null) => `BANK${country ?? "NL"}2AXXX`

/** A BIC split as it is built: bank, country, place and branch. */
export const bicGroups = (held: string): string =>
  [held.slice(0, 4), held.slice(4, 6), held.slice(6, 8), held.slice(8, 11)].filter(Boolean).join(" ")

/** Only letters and digits, in capitals: what a pasted or typed IBAN or BIC is read as. */
export const plain = (typed: string): string => typed.replaceAll(/[^0-9a-z]/gi, "").toUpperCase()

/** The country an IBAN is from, once its first two letters are typed. */
export const ibanCountry = (iban: string): string | null => {
  const country = plain(iban).slice(0, 2)
  return /^[A-Z]{2}$/.test(country) ? country : null
}

/** How far an IBAN has got as it is typed, and what to say about it. */
export function ibanState(iban: string): {complete: boolean; problem: string | null; hint: string} {
  const held = plain(iban)
  if (!held) return {complete: false, problem: null, hint: ""}
  const country = ibanCountry(held)
  if (held.length >= 2 && !country) return {complete: false, problem: "An IBAN starts with two letters, like NL.", hint: ""}
  const length = country ? IBAN_LENGTHS[country] : undefined
  if (country && !length) return {complete: false, problem: `We do not know IBANs that start with ${country}.`, hint: ""}
  if (!length || held.length < length) {
    const left = length ? length - held.length : 0
    return {complete: false, problem: null, hint: length ? `${left} more ${left === 1 ? "character" : "characters"} to go.` : ""}
  }
  return ibanValid(held)
    ? {complete: true, problem: null, hint: "This IBAN is valid."}
    : {complete: true, problem: "This IBAN has a mistake. Check the numbers.", hint: ""}
}

/** What is wrong with a BIC as typed, or nothing: eight or eleven characters, its country the IBAN's. */
export function bicProblem(bic: string, iban: string): string | null {
  const held = plain(bic)
  if (!held) return null
  if (!/^[A-Z]{4}[A-Z]{2}[A-Z0-9]{2}([A-Z0-9]{3})?$/.test(held)) return "A BIC has 8 or 11 letters and digits, like INGBNL2A."
  const country = ibanCountry(iban)
  return country && held.slice(4, 6) !== country ? `This BIC is for a bank in ${held.slice(4, 6)}, but your IBAN is from ${country}.` : null
}

/** What is wrong with the account as typed, in the words the form shows, or nothing. */
export function wrongIn(account: BankAccount): {holder?: string; iban?: string; bic?: string} {
  return {
    ...(account.holder.trim() ? {} : {holder: "Add the name on the account."}),
    ...(ibanValid(account.iban) ? {} : {iban: plain(account.iban) ? ibanState(account.iban).problem ?? "This IBAN is not complete yet." : "Add your IBAN."}),
    ...(ibanValid(account.iban) && needsBic(account.iban) && !plain(account.bic) ? {bic: "Add the BIC of your bank."} : {}),
    ...(bicProblem(account.bic, account.iban) ? {bic: bicProblem(account.bic, account.iban)!} : {}),
  }
}

export const tidied = (account: BankAccount): BankAccount => ({
  holder: account.holder.trim(),
  iban: plain(account.iban),
  bic: needsBic(account.iban) ? plain(account.bic) : "",
})
