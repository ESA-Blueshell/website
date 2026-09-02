import {describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import {nextTick} from "vue"
import Board from "@/pages/Board.vue"

/**
 * What the page itself decides.
 *
 * Only that: which board it opens on, and where a seat's portrait comes from. How a board reads —
 * its numeral, its year, its name, where it stands, its seats' order — is the board domain's and
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
    id: 10, number: 10, name: "Rainbow road", image: null, photo: null,
    startDate: "2099-09-01", endDate: "2100-08-31", members: [],
  },
  {
    id: 9,
    number: 9,
    name: "Eeveelutions",
    image: "board9/board9.jpg",
    photo: null,
    startDate: "2025-09-01",
    endDate: null,
    members: [
      {
        id: 91, role: "Chair", name: "Emma Dokter", nickname: "LyndisLuna",
        description: "Chairing.", image: "board9/Emma.jpg", portrait: stored("emma"), userId: 1,
      },
      {
        id: 92, role: "Secretary", name: "Viktor Petrov", nickname: null,
        description: null, image: "board9/Viktor.jpg", portrait: null, userId: null,
      },
      {
        id: 93, role: "Treasurer", name: "Sylwia Nowak", nickname: null,
        description: null, image: null, portrait: null, userId: null,
      },
    ],
  },
  {
    id: 1, number: 1, name: null, image: null, photo: null,
    startDate: "2017-09-01", endDate: "2018-08-31", members: [],
  },
]

const query: Record<string, string> = {}

vi.mock("@/domains/boards/adapters/boards", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  loadBoards: () => Promise.resolve(boards),
}))
vi.mock("@/plugins/require", () => ({$require: (path: string) => `resolved:${path}`}))
// Partially, because the router plugin the network-error handler pulls in builds a real one.
vi.mock("vue-router", async (importOriginal) => ({
  ...(await importOriginal<typeof import("vue-router")>()),
  useRoute: () => ({query}),
  useRouter: () => ({push: vi.fn()}),
}))

interface Page {
  shown: {number: number} | null
  portraitOf: (seat: unknown) => string
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

  it("draws a seat's portrait from the stored picture, at the widest width it is stored at", async () => {
    const page = await mountPage()

    // The row draws a portrait a few hundred pixels across, so the master is not what it needs.
    expect(page.portraitOf(boards[1]!.members[0])).toBe(
      "/files/public/board-portraits/emma-640.webp",
    )
  })

  it("falls back to the assets directory for a seat that still names a file", async () => {
    const page = await mountPage()

    // The two answer side by side until #935 takes the directory out.
    expect(page.portraitOf(boards[1]!.members[1])).toBe("resolved:@/assets/board9/Viktor.jpg")
  })

  it("draws nothing rather than a broken path for a seat with no portrait at all", async () => {
    const page = await mountPage()

    expect(page.portraitOf(boards[1]!.members[2])).toBe("")
  })
})
