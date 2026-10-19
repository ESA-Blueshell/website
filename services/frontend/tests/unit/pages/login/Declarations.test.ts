import {beforeEach, describe, expect, it, vi} from "vitest"
import Declarations from "@/pages/login/Declarations.vue"
import type {Declaration} from "@/domains/declarations"
import {mountInApp, settle} from "../helpers"

const listOwnDeclarations = vi.hoisted(() => vi.fn())

vi.mock("@/domains/declarations", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/declarations")>()),
  listOwnDeclarations,
}))
vi.mock("@/components/common/AccountFrame.vue", () => ({
  default: {name: "AccountFrame", props: ["heading", "islandContent"], template: "<div><slot /></div>"},
}))

const at = "2026-10-02T10:00:00Z"
const one = (id: number, state: Declaration["state"], amount: string): Declaration => ({
  id,
  submitter: "Ann Vos",
  state,
  confirmed: true,
  createdAt: at,
  receipts: [],
  expenses: [{id: "1", paidOn: "2026-10-01", description: `Expense ${id}`, amount, for: null, receipts: []}],
  steps: [{state, at, by: "Ann Vos"}],
})

describe("the declarations tab", () => {
  beforeEach(() => vi.clearAllMocks())

  const mount = async () => {
    const wrapper = mountInApp(Declarations)
    await settle()
    return wrapper
  }

  it("sums up what waits, what was paid and the drafts, and lists the open ones before the done ones", async () => {
    listOwnDeclarations.mockResolvedValue([
      one(1, "SUBMITTED", "10.00"),
      one(2, "APPROVED", "5.00"),
      one(3, "PAID", "7.50"),
      one(4, "DRAFT", "1.00"),
      one(5, "REJECTED", "2.00"),
    ])
    const wrapper = await mount()

    expect(wrapper.get('[data-testid="declarations-waiting"]').text()).toContain("€ 15.00")
    expect(wrapper.get('[data-testid="declarations-waiting"]').text()).toContain("2 declarations")
    expect(wrapper.get('[data-testid="declarations-paid"]').text()).toContain("1 declaration")
    expect(wrapper.get('[data-testid="declarations-drafts"]').text()).toContain("Not submitted yet")
    expect(wrapper.get('[data-testid="declarations-row-3"]').text()).toContain("Paid")
    expect(wrapper.findAll(".declarations__group").map(group => group.text())).toEqual(["Open", "Done"])
  })

  it("says when there is nothing yet, and when the list could not be read", async () => {
    listOwnDeclarations.mockResolvedValueOnce([])
    const empty = await mount()
    expect(empty.get('[data-testid="declarations-none"]').exists()).toBe(true)
    expect(empty.get('[data-testid="declarations-waiting"]').text()).toContain("No declarations are waiting for the board")
    expect(empty.get('[data-testid="declarations-paid"]').text()).toContain("Nothing yet")

    listOwnDeclarations.mockResolvedValueOnce(null)
    expect((await mount()).find('[data-testid="declarations-unread"]').exists()).toBe(true)
  })

  it("is reached from its routes, which open a declaration by its id or a new one", async () => {
    const router = (await import("@/plugins/router")).default
    for (const [path, name] of [["/account/declarations", "accountDeclarations"], ["/account/declarations/new", "accountDeclaration"], ["/account/declarations/40", "accountDeclaration"]]) {
      const route = router.resolve(path)
      expect(route.name).toBe(name)
      const load = route.matched.at(-1)!.components!.default as () => Promise<{default: unknown}>
      expect((await load()).default).toBeTruthy()
    }
    expect(router.resolve("/account/declarations/other").name).not.toBe("accountDeclaration")
  })
})
