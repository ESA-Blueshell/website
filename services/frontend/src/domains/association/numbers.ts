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
} as const

/**
 * The four figures the membership page leads with, upgraded wherever the api has counted.
 *
 * The counts and the claim are built the same way so the band draws one kind of thing, and the
 * member count simply never becomes exact.
 */
export function associationFigures(numbers: AssociationNumbers | null): Figure[] {
  return [
    {id: "members", value: MEMBERS_CLAIMED, exact: false, label: "Members"},
    {
      id: "teams",
      value: numbers?.teamsThisSeason ?? FLOORS.teamsThisSeason,
      exact: numbers != null,
      label: "Esports teams this season",
    },
    {
      id: "committees",
      value: numbers?.committees ?? FLOORS.committees,
      exact: numbers != null,
      label: "Member-run committees",
    },
    {
      id: "events",
      value: numbers?.eventsLastYear ?? FLOORS.eventsLastYear,
      exact: numbers != null,
      label: "Events in the past year",
    },
  ]
}

/** A figure as it is read: a floor says `200+`, a count says `13`. */
export function figureText(figure: Figure): string {
  return figure.exact ? String(figure.value) : `${figure.value}+`
}
