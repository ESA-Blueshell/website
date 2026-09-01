import {describe, expect, it, vi} from "vitest"
import {flushPromises, shallowMount} from "@vue/test-utils"
import {nextTick} from "vue"
import Board from "@/pages/Board.vue"

const boards = [
  {
    id: 9,
    number: 9,
    name: "Eeveelutions",
    image: "board9/board9.jpg",
    startDate: "2025-09-01",
    members: [
      {
        id: 91, role: "Chair", name: "Emma Dokter", nickname: "LyndisLuna",
        description: "Chairing.", image: "board9/Emma.jpg", userId: 1,
      },
      {id: 92, role: "Secretary", name: "Viktor Petrov", nickname: null, description: null, image: null, userId: null},
    ],
  },
  {
    // A board's name may never have been written down. The page names it from its number.
    id: 1,
    number: 1,
    name: null,
    image: null,
    startDate: "2017-09-01",
    members: [
      {
        id: 11, role: "Chairman", name: "Thijs Lieverse", nickname: "Grootbuik",
        description: null, image: null, userId: null,
      },
    ],
  },
]

vi.mock("@/domains/boards/adapters/boards", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/boards/adapters/boards")>()),
  loadBoards: () => Promise.resolve(boards),
}))
vi.mock("@/plugins/require", () => ({$require: (path: string) => `resolved:${path}`}))

const mountPage = async () => {
  const wrapper = shallowMount(Board, {global: {stubs: {BoardMemberRow: true, TopBanner: true}}})
  await flushPromises()
  await nextTick()
  return wrapper
}

describe("Board page", () => {
  it("shows the board in office outright and the older ones behind a toggle", async () => {
    const wrapper = await mountPage()
    const vm = wrapper.vm as never as {expandedBoards: Record<number, boolean>; toggleBoard: (id: number) => void}

    // The sitting board is not a toggle at all; the older one starts shut.
    expect(wrapper.find('[data-testid="board-9"]').exists()).toBe(true)
    expect(vm.expandedBoards[1]).toBeFalsy()

    vm.toggleBoard(1)
    await nextTick()
    expect(vm.expandedBoards[1]).toBe(true)

    vm.toggleBoard(1)
    await nextTick()
    expect(vm.expandedBoards[1]).toBe(false)
  })

  it("hands each seat its title, its blurb and its resolved portrait", async () => {
    const wrapper = await mountPage()
    const vm = wrapper.vm as never as {seatsOf: (board: unknown) => Array<Record<string, unknown>>}

    const seats = vm.seatsOf(boards[0])
    expect(seats[0]).toMatchObject({
      // The nickname is recorded beside the name and composed back for a reader.
      name: 'Emma "LyndisLuna" Dokter',
      title: "Chair",
      description: "Chairing.",
      image: "resolved:@/assets/board9/Emma.jpg",
    })
    // A seat with no portrait resolves to nothing rather than to a broken path.
    expect(seats[1].image).toBe("")
    // And a seat with no nickname is named plainly.
    expect(seats[1].name).toBe("Viktor Petrov")
  })

  it("names a board with no recorded name from its number", async () => {
    const wrapper = await mountPage()

    expect(wrapper.find('[data-testid="board-9"]').text()).toContain("Eeveelutions")
    expect(wrapper.find('[data-testid="board-1"]').text()).toContain("Board 1")
  })
})
