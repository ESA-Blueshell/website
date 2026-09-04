import {afterEach, describe, expect, it, vi} from "vitest"
import {defineComponent, h} from "vue"
import {mount, type VueWrapper} from "@vue/test-utils"
import {useBoards} from "@/domains/boards/island/useBoards"
import {loadBoards, type Board} from "@/domains/boards/adapters/boards"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {settle, unmountAll} from "../../../helpers/testUtils"

vi.mock("@/domains/boards/adapters/boards", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  loadBoards: vi.fn(),
}))

vi.mock("@/plugins/handleNetworkError", () => ({$handleNetworkError: vi.fn()}))

const board = (number: number, startDate: string, endDate?: string) =>
  ({id: number, number, startDate, endDate, members: []}) as unknown as Board

const wrappers: VueWrapper[] = []

/** Mounted rather than called bare: the read is on `onMounted`, which needs an instance. */
async function held(): Promise<ReturnType<typeof useBoards>> {
  let api: ReturnType<typeof useBoards> | null = null
  const wrapper = mount(defineComponent({
    setup() {
      api = useBoards()
      return () => h("div")
    },
  }))
  wrappers.push(wrapper as VueWrapper)
  await settle()
  return api!
}

describe("useBoards", () => {
  afterEach(() => unmountAll(wrappers, "useBoards"))

  it("reads the boards once the page is up, and stops loading when they are in", async () => {
    vi.mocked(loadBoards).mockResolvedValue([board(9, "2024-09-01")])

    const boards = await held()

    expect(loadBoards).toHaveBeenCalledTimes(1)
    expect(boards.boards.value).toHaveLength(1)
    expect(boards.loading.value).toBe(false)
  })

  it("names the board in office out of the ones it read, without asking a second question", async () => {
    vi.mocked(loadBoards).mockResolvedValue([
      board(10, "2999-09-01"),
      board(9, "2020-09-01"),
      board(8, "2019-09-01", "2020-08-31"),
    ])

    const boards = await held()

    expect(boards.inOffice.value?.number).toBe(9)
    expect(loadBoards).toHaveBeenCalledTimes(1)
  })

  it("has no board in office before any have been read", async () => {
    vi.mocked(loadBoards).mockResolvedValue([])

    const boards = await held()

    expect(boards.inOffice.value).toBeNull()
  })

  it("reads again on a refresh, which is how a correction reaches the page", async () => {
    vi.mocked(loadBoards).mockResolvedValue([board(9, "2020-09-01")])
    const boards = await held()
    expect(boards.inOffice.value?.number).toBe(9)

    vi.mocked(loadBoards).mockResolvedValue([board(10, "2021-09-01"), board(9, "2020-09-01", "2021-08-31")])
    await boards.refresh()

    expect(boards.boards.value).toHaveLength(2)
    expect(boards.inOffice.value?.number).toBe(10)
  })

  // A read that failed leaves whatever was on the page standing: replacing it with emptiness
  // would state a history nobody confirmed had gone.
  it("hands a failed read to the network handler and stops loading", async () => {
    vi.mocked(loadBoards).mockResolvedValue([board(9, "2020-09-01")])
    const boards = await held()

    const boom = new Error("boom")
    vi.mocked(loadBoards).mockRejectedValue(boom)
    await boards.refresh()

    expect($handleNetworkError).toHaveBeenCalledWith(boom)
    expect(boards.boards.value).toHaveLength(1)
    expect(boards.loading.value).toBe(false)
  })
})
