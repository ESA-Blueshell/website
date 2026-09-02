import {describe, expect, it} from "vitest"
import {bySeatRank, SEAT_OFFICES, seatRank, seatsInOrder, UNRANKED_SEAT} from "@/domains/boards"
import {seededRoles, seededSeats} from "./seed"

type Office = (typeof SEAT_OFFICES)[number] | "unranked"

function rankOf(office: Office): number {
  return office === "unranked" ? UNRANKED_SEAT : SEAT_OFFICES.indexOf(office)
}

/**
 * Every role string the association has written down, and the office each one ranks under.
 *
 * Named by office rather than by number so the table reads the way the rule does, and covering
 * all of the real history rather than a sample: the lower-case commissioners, the four roles that
 * name two offices, and the board that wrote "Esports affairs" where the one before it wrote
 * "Esports Affairs" are the cases the ranking exists to get right.
 */
const RANKS: [string, Office][] = [
  ["Chair", "chair"],
  ["Chairman", "chair"],
  ["Secretary", "secretary"],
  ["Secretary and Treasurer", "secretary"],
  ["Secretary and Commissioner of Esports Affairs", "secretary"],
  ["Secretary and Commissioner of External Affairs", "secretary"],
  ["Secretary and Commissioner of the Esports Lounge", "secretary"],
  ["Treasurer", "treasurer"],
  ["Treasurer and Commissioner of Esports Affairs", "treasurer"],
  ["Treasurer and Commissioner of Esports affairs", "treasurer"],
  ["Commissioner of Internal Affairs", "internal"],
  ["Officer of Internal Affairs", "internal"],
  ["Commissioner of External Affairs", "external"],
  ["Officer of External Affairs", "external"],
  ["Commissioner of Esports", "esports"],
  ["Commissioner of Esports Affairs", "esports"],
]

/**
 * Spellings no board uses now, which the rule still has to rank.
 *
 * The seed has been normalised since these were written: the third board's role is spelled out
 * rather than joined with a slash, and the two boards that wrote their commissioners in lower
 * case have been given capitals. The rule is case-insensitive and reads a slash as a space, and
 * that is worth keeping asserted even once nothing in the files exercises it, because the next
 * board to invent a spelling will not check first.
 */
const TOLERATED: [string, Office][] = [
  ["Secretary/Treasurer", "secretary"],
  ["Commissioner of internal affairs", "internal"],
  ["Commissioner of external affairs", "external"],
  ["COMMISSIONER OF ESPORTS", "esports"],
]

/** A role naming two offices, written the other way round. No board has done this yet. */
const REVERSED: [string, Office][] = [
  ["Commissioner of the Esports Lounge and Secretary", "secretary"],
  ["Commissioner of Esports Affairs and Treasurer", "treasurer"],
  ["Commissioner of Internal Affairs and Chair", "chair"],
  ["Commissioner of External Affairs and Commissioner of Internal Affairs", "internal"],
]

const seat = (name: string, role: string) => ({name, role})

describe("seatRank", () => {
  it.each(RANKS)("ranks '%s' as %s", (role, office) => {
    expect(seatRank(role), `'${role}' should rank as ${office}`).toBe(rankOf(office))
  })

  it("has a rank for every role the seed records, so a new one cannot slip past unranked", () => {
    const asserted = new Set(RANKS.map(([role]) => role))
    const unasserted = seededRoles().filter(role => !asserted.has(role))

    expect(unasserted, "a role in the seed has no asserted rank in this test").toEqual([])
    // And nothing in the table has been left behind by a correction to the seed.
    expect(RANKS.map(([role]) => role).filter(role => !seededRoles().includes(role))).toEqual([])
  })

  it("recognises the office of every seat the association has ever seated", () => {
    const unrecognised = seededSeats().filter(one => seatRank(one.role) === UNRANKED_SEAT)

    expect(unrecognised, "a seated role ranked as unrecognised").toEqual([])
  })

  it.each(TOLERATED)("ranks '%s' as %s, though no board writes it that way now", (role, office) => {
    expect(seatRank(role), `'${role}' should still rank as ${office}`).toBe(rankOf(office))
  })

  it.each(REVERSED)("takes the senior office of '%s', which is %s", (role, office) => {
    expect(seatRank(role), `'${role}' should rank as ${office} whichever office is written first`)
      .toBe(rankOf(office))
  })

  it.each(["Commissioner of Beans", "Board Assistant", "", "  "])(
    "puts '%s' after every office it does not name",
    role => {
      expect(seatRank(role), `'${role}' should be unrecognised`).toBe(UNRANKED_SEAT)
    },
  )

  it("ranks an unrecorded role with the unrecognised ones rather than throwing", () => {
    expect(seatRank(null)).toBe(UNRANKED_SEAT)
    expect(seatRank(undefined)).toBe(UNRANKED_SEAT)
  })
})

describe("seatsInOrder", () => {
  it("reads chair, secretary, treasurer, internal, external, esports", () => {
    const ordered = seatsInOrder([
      seat("Esports", "Commissioner of Esports"),
      seat("External", "Commissioner of External Affairs"),
      seat("Internal", "Commissioner of Internal Affairs"),
      seat("Treasurer", "Treasurer"),
      seat("Secretary", "Secretary"),
      seat("Chair", "Chair"),
    ])

    expect(ordered.map(one => one.name)).toEqual([
      "Chair",
      "Secretary",
      "Treasurer",
      "Internal",
      "External",
      "Esports",
    ])
  })

  it("puts a role nobody recognises after all of them", () => {
    const ordered = seatsInOrder([
      seat("Bean", "Commissioner of Beans"),
      seat("Esports", "Commissioner of Esports"),
      seat("Chair", "Chair"),
    ])

    expect(ordered.map(one => one.name)).toEqual(["Chair", "Esports", "Bean"])
  })

  it("breaks a tie on name, so a board reads the same way twice running", () => {
    const ordered = seatsInOrder([
      seat("Zoë", "Commissioner of Internal Affairs"),
      seat("Anne", "Commissioner of internal affairs"),
      seat("Marit", "Officer of Internal Affairs"),
    ])

    expect(ordered.map(one => one.name)).toEqual(["Anne", "Marit", "Zoë"])
  })

  it("orders the third board by office, not by the order its seats were recorded", () => {
    const third = seededSeats().filter(one => one.board === 3)

    // Recorded chair, secretary/treasurer, esports, internal, external.
    expect(third.map(one => one.name)).toEqual([
      "Jander Gilbers",
      "Joran Hagen",
      "Andrei Raureanu",
      "William Schaarman",
      "Allysha Sewradj",
    ])
    expect(seatsInOrder(third).map(one => one.name)).toEqual([
      "Jander Gilbers",
      "Joran Hagen",
      "William Schaarman",
      "Allysha Sewradj",
      "Andrei Raureanu",
    ])
  })

  it("puts the sixth board's treasurer behind a secretary who also ran the esports lounge", () => {
    const sixth = seededSeats().filter(one => one.board === 6)

    expect(seatsInOrder(sixth).map(one => one.name)).toEqual([
      "Amber Scholtz",
      "Jelle van Wezep",
      "Thomas Dekker",
      "Roos Kruk",
      "Thijs Willems",
      "Jonas Valentijn",
    ])
  })

  it("leaves the list it was given alone, because it is usually a prop", () => {
    const seats = [seat("Esports", "Commissioner of Esports"), seat("Chair", "Chair")]

    seatsInOrder(seats)

    expect(seats.map(one => one.name)).toEqual(["Esports", "Chair"])
  })
})

describe("bySeatRank", () => {
  it("is the comparator the order is made of, so a page can sort with it directly", () => {
    const seats = [seat("Esports", "Commissioner of Esports"), seat("Chair", "Chairman")]

    expect(seats.slice().sort(bySeatRank).map(one => one.name)).toEqual(["Chair", "Esports"])
  })
})
