import type {AssociationNumbers} from "./adapters/association"

/**
 * One figure the association shows about itself.
 *
 * `exact` is the difference between a number the api counted and a floor the association
 * publishes about itself. A floor is drawn with a `+` after it, so nothing on the page ever
 * claims a count it does not have.
 */
export interface Figure {
  id: string
  value: number
  exact: boolean
  label: string
}

/**
 * How many members the association says it has.
 *
 * Hardcoded on purpose, and not something the statistics endpoint can fix: the member count is
 * permission-gated, so a visitor reading this page while logged out cannot be told it. The
 * association's own published material says over 200, so that is what the page says.
 */
export const MEMBERS_CLAIMED = 200

/**
 * How many people are in the association's Discord.
 *
 * The same kind of claim as [MEMBERS_CLAIMED] and for a different reason: nothing here can
 * count another service's members. The association's own article puts it over eleven hundred.
 */
export const DISCORD_CLAIMED = 1100

/**
 * What the page shows before the api answers, and what it keeps if the api never does.
 *
 * Floors rather than zeroes or blanks: each is a number the association's own 2025 partnership
 * overview already publishes, so a visitor who arrives during the read, or whose read fails,
 * is told something true rather than shown a grey box.
 */
export const FLOORS = {
  committees: 10,
  eventsLastYear: 20,
  teamsThisSeason: 10,
  boards: 8,
  seasonsPlayed: 6,
  gamesPlayed: 10,
} as const

/** Every figure the association can show, by the name a page asks for it by. */
export type FigureId =
  | "members"
  | "discord"
  | "teams"
  | "committees"
  | "events"
  | "boards"
  | "seasons"
  | "games"

/**
 * How each figure is built: what it is called, and where its number comes from.
 *
 * A claim has no counter behind it and never becomes exact — nothing here can count another
 * service's members, and the association's own member count is permission-gated. Everything
 * else stands on its floor until the api answers.
 */
const FIGURES: Record<FigureId, (numbers: AssociationNumbers | null) => Figure> = {
  members: () => ({id: "members", value: MEMBERS_CLAIMED, exact: false, label: "Members"}),
  discord: () => ({id: "discord", value: DISCORD_CLAIMED, exact: false, label: "In our Discord"}),
  teams: n => counted("teams", n?.teamsThisSeason, FLOORS.teamsThisSeason, n, "Esports teams this season"),
  committees: n => counted("committees", n?.committees, FLOORS.committees, n, "Member-run committees"),
  events: n => counted("events", n?.eventsLastYear, FLOORS.eventsLastYear, n, "Events in the past year"),
  boards: n => counted("boards", n?.boards, FLOORS.boards, n, "Boards so far"),
  seasons: n => counted("seasons", n?.seasonsPlayed, FLOORS.seasonsPlayed, n, "Seasons competed"),
  games: n => counted("games", n?.gamesPlayed, FLOORS.gamesPlayed, n, "Games we field"),
}

const counted = (
  id: FigureId,
  value: number | undefined,
  floor: number,
  numbers: AssociationNumbers | null,
  label: string,
): Figure => ({id, value: value ?? floor, exact: numbers != null, label})

/** The figures a page names, in the order it names them. */
export function figuresFor(ids: readonly FigureId[], numbers: AssociationNumbers | null): Figure[] {
  return ids.map(id => FIGURES[id](numbers))
}

/** What the membership page leads with. */
export const MEMBERSHIP_FIGURES: readonly FigureId[] = ["members", "teams", "committees", "events"]

/**
 * The four figures the membership page leads with, upgraded wherever the api has counted.
 *
 * The counts and the claim are built the same way so the band draws one kind of thing, and the
 * member count simply never becomes exact.
 */
export function associationFigures(numbers: AssociationNumbers | null): Figure[] {
  return figuresFor(MEMBERSHIP_FIGURES, numbers)
}

/** A figure as it is read: a floor says `200+`, a count says `13`. */
export function figureText(figure: Figure): string {
  return figure.exact ? String(figure.value) : `${figure.value}+`
}
