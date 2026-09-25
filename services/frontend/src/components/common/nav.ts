import {SOCIAL_GLYPHS} from "@/components/island/socialGlyphs"

/** A game, as the bar needs it: the esports domain owns the record this is read from. */
export interface NavGame {
  name: string
  slug: string
}

/** A committee, as the bar needs it: the committees domain owns the record this is read from. */
export interface NavCommittee {
  name: string
  slug: string
}

/** One destination in the bar: a page, and the label the bar shows for it. */
export interface NavEntry {
  label: string
  to: string
  /** The sections a reader is under when this entry is the one they arrived by. */
  covers?: string[]
}

/** An entry in the bar, with the entries it drops down to where it has any. */
export interface NavSection extends NavEntry {
  entries?: NavEntry[]
}

/** Who a reader is, as far as the bar is concerned. */
export interface NavReader {
  loggedIn: boolean
  board: boolean
  admin: boolean
  /** The address the account menu edits, where the reader has one. */
  addressId?: number | string | null
}

const ASSOCIATION: NavEntry[] = [
  {label: "About us", to: "/aboutus"},
  {label: "Board", to: "/board"},
  {label: "Newsletters", to: "/blogs"},
  {label: "Documents", to: "/documents"},
]

const PARTNERS: NavEntry[] = [
  {label: "Become a partner", to: "/partners/become-a-partner"},
  {label: "El Niño", to: "/partners/el-nino"},
  {label: "Marketing Maatwerk", to: "/partners/marketing-maatwerk"},
]

/**
 * The bar, declared once.
 *
 * The desktop bar and the drawer render this same list. They were two hand-copied lists before,
 * and they had already drifted: the drawer carried an Events group the bar did not, and offered
 * no way to log in or reach an account at all.
 */
export const sectionsFor = (games: NavGame[], committees: NavCommittee[] = []): NavSection[] => [
  {label: "Home", to: "/"},
  {label: "Membership", to: "/membership"},
  {
    label: "Association",
    to: "/aboutus",
    covers: ["/aboutus", "/board", "/blogs", "/documents"],
    entries: ASSOCIATION,
  },
  {
    label: "Committees",
    to: "/committees",
    covers: ["/committees"],
    entries: [
      {label: "All committees", to: "/committees"},
      ...committees.map(committee => ({label: committee.name, to: `/committees/${committee.slug}`})),
    ],
  },
  {
    label: "Events",
    to: "/events",
    covers: ["/events"],
    // Circuit Showdown is only ever arrived at from here: nothing else on the site links to it.
    entries: [
      {label: "Upcoming events", to: "/events"},
      {label: "Past events", to: "/events/past"},
      {label: "Circuit Showdown", to: "/events/circuitShowdown"},
    ],
  },
  {label: "Casual", to: "/casual"},
  {
    label: "Competition",
    to: "/competition",
    covers: ["/competition"],
    entries: [
      {label: "Competitive scene", to: "/competition"},
      ...games.map(game => ({label: game.name, to: `/competition/${game.slug}`})),
    ],
  },
  {label: "Partners", to: "/partners/become-a-partner", covers: ["/partners"], entries: PARTNERS},
  {label: "Contact", to: "/contact"},
]

/**
 * The management entries this reader may use.
 *
 * The gates are the controller's, mirrored: a board runs the association's own records, an admin
 * runs the machinery, and the outbox answers to both.
 */
export const managementFor = (reader: NavReader): NavEntry[] => [
  ...(reader.board
    ? [
      {label: "Manage addresses", to: "/addresses/manage"},
      {label: "Manage account recovery", to: "/recovery/manage"},
      {label: "Manage committees", to: "/committees/manage"},
      {label: "Manage users", to: "/user-manager"},
    ]
    : []),
  ...(reader.admin
    ? [
      {label: "Manage jobs", to: "/management/jobs"},
      {label: "Manage cohorts", to: "/management/cohorts"},
    ]
    : []),
  ...(reader.board || reader.admin ? [{label: "Manage emails", to: "/management/emails"}] : []),
]

/** Where the bar sends somebody who is logged in, beside logging out. */
export const accountFor = (reader: NavReader): NavEntry[] => [
  {label: "Account", to: "/account"},
  {label: "Security", to: "/account/security"},
  {label: "Esports Teams", to: "/account/games"},
  ...(reader.addressId == null ? [] : [{label: "Address", to: `/account/addresses/${reader.addressId}`}]),
]

/**
 * Whether the reader is under one of these sections, which is what the bar marks.
 *
 * Read off the path rather than off a router-link's own active class: an entry that opens a menu
 * addresses one page of its section, so `/competition/valorant` would leave Competition unmarked.
 */
export const covers = (path: string, section: NavSection): boolean =>
  (section.covers ?? [section.to]).some(under =>
    under === "/" ? path === "/" : path === under || path.startsWith(`${under}/`))

/** The drawer draws its own stroked marks, so it takes only where each account is. */
function addressOf<G extends {label: string, href: string}>(glyph: G): {label: G["label"], href: G["href"]} {
  return {label: glyph.label, href: glyph.href}
}

/** The social accounts the drawer offers, in the order it offers them. */
export const SOCIALS = [
  {...addressOf(SOCIAL_GLYPHS.email), mark: "mail"},
  {...addressOf(SOCIAL_GLYPHS.discord), mark: "discord"},
  {...addressOf(SOCIAL_GLYPHS.instagram), mark: "instagram"},
  {...addressOf(SOCIAL_GLYPHS.facebook), mark: "facebook"},
  {...addressOf(SOCIAL_GLYPHS.twitch), mark: "twitch"},
  {...addressOf(SOCIAL_GLYPHS.x), mark: "x"},
  {...addressOf(SOCIAL_GLYPHS.linkedin), mark: "linkedin"},
] as const
