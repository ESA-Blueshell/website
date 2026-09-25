import {describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import {nextTick} from "vue"
import Board from "@/pages/Board.vue"

/**
 * What the page itself decides.
 *
 * Only that: which board it opens on, and where a member's portrait comes from. How a board reads,
 * its numeral, its year, its name, where it stands, its members' order, is the board domain's and
 * is tested against the seeded history in `tests/unit/domains/boards`. What a reader sees is the
 * end-to-end suite's, because the unit suite has no Vuetify plugin and a shallow mount renders
 * none of the island's slots.
 */
const stored = (name: string) => ({
  path: `board-portraits/${name}.webp`,
  url: `/files/public/board-portraits/${name}.webp`,
  width: 640,
  height: 640,
  renditions: [160, 320, 640].map(width => ({
    url: `/files/public/board-portraits/${name}-${width}.webp`,
    width,
  })),
})

/** Newest first, the way the adapter answers, with a board elected and not yet sitting. */
const boards = [
  {
    id: 10, number: 10, name: "Rainbow road", photo: null,
    startDate: "2099-09-01", endDate: "2100-08-31", members: [],
  },
  {
    id: 9,
    number: 9,
    name: "Eeveelutions",
    photo: null,
    startDate: "2025-09-01",
    endDate: null,
    members: [
      {
        id: 91, role: "Chair", name: "Emma Dokter", nickname: "LyndisLuna",
        description: "Chairing.", portrait: stored("emma"), userId: 1,
      },
      {
        id: 92, role: "Secretary", name: "Viktor Petrov", nickname: null,
        description: null, portrait: null, userId: null,
      },
      {
        id: 93, role: "Treasurer", name: "Sylwia Nowak", nickname: null,
        description: null, portrait: null, userId: null,
      },
    ],
  },
  {
    id: 1, number: 1, name: null, photo: null,
    startDate: "2017-09-01", endDate: "2018-08-31", members: [],
  },
]

const query: Record<string, string> = {}
const push = vi.hoisted(() => vi.fn())

vi.mock("@/domains/boards/adapters/boards", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  loadBoards: () => Promise.resolve(boards),
}))
// Partially, because the router plugin the network-error handler pulls in builds a real one.
vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({query}),
  useRouter: () => ({push}),
}))

interface Page {
  shown: {number: number} | null
  portraitOf: (member: unknown) => string
  editBoard: (number: number) => void
  editBoardAt: (stop: string | number | null) => void
  addBoard: () => void
  editMember: (id: number, stop: string | number | null) => void
  addMember: (stop: string | number | null) => void
}

const mountPage = async () => {
  // The page asks the store whether the reader may correct the history, so a mount answers.
  const wrapper = shallowMount(Board, {
    global: {provide: {store: {getters: {isBoard: false}}}},
  })
  await flushPromises()
  await nextTick()
  return wrapper.vm as never as Page
}

describe("Board page", () => {
  it("opens on the board in office, not on the newest board recorded", async () => {
    const page = await mountPage()

    // The tenth board is written down and has not taken office. The ninth runs the association.
    expect(page.shown?.number).toBe(9)
  })

  it("opens on the board a url names", async () => {
    query.board = "1"
    try {
      const page = await mountPage()
      expect(page.shown?.number).toBe(1)
    } finally {
      delete query.board
    }
  })

  it("falls through to the board in office where a url names a board nobody recorded", async () => {
    query.board = "44"
    try {
      const page = await mountPage()
      // A link can outlive the board it named, and a stale link is not worth a blank page.
      expect(page.shown?.number).toBe(9)
    } finally {
      delete query.board
    }
  })

  it("draws a member's portrait from the stored picture, at the widest width it is stored at", async () => {
    const page = await mountPage()

    // The row draws a portrait a few hundred pixels across, so the master is not what it needs.
    expect(page.portraitOf(boards[1]!.members[0])).toBe(
      "/files/public/board-portraits/emma-640.webp",
    )
  })

  it("draws nothing rather than a broken path for a member with no portrait at all", async () => {
    const page = await mountPage()

    expect(page.portraitOf(boards[1]!.members[1])).toBe("")
    expect(page.portraitOf(boards[1]!.members[2])).toBe("")
  })

  it("adds and corrects a board and its members on pages of their own", async () => {
    const page = await mountPage()
    push.mockReset()

    page.addBoard()
    page.editBoard(9)
    page.editBoardAt(9)
    page.editBoardAt(44)
    page.addMember(9)
    page.addMember(44)
    page.editMember(92, 9)
    page.editMember(92, 44)

    expect(push.mock.calls).toEqual([
      ["/board/new"], ["/board/9/edit"], ["/board/9/edit"], ["/board/9/members/new"], ["/board/9/members/92/edit"],
    ])
  })
})
