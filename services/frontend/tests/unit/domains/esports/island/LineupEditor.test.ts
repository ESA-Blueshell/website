import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import LineupEditor from "@/domains/esports/island/LineupEditor.vue"
import {loadRoster, loadTeams} from "@/domains/esports/adapters/esports"
import {fieldExistingTeam, publishLineup} from "@/domains/esports/adapters/lineup"
import {settle} from "../../../helpers/testUtils"

/**
 * The writes are the adapter's, and are proven there. What is left here is what the component
 * promises at its own interface: which buttons it offers, what it says about a line-up it could
 * not read, and that a refused write closes nothing.
 */
vi.mock("@/domains/esports/adapters/esports", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/esports/adapters/esports")>()),
  dropTeam: vi.fn(),
  loadRoster: vi.fn(),
  loadTeamSeasons: vi.fn(),
  loadTeams: vi.fn(),
  storePicture: vi.fn(),
  unfieldTeamFromSeason: vi.fn(),
}))

// `isBlank` is left real: it is the rule the Save button reads, and a stub of it would prove
// the button against nothing.
vi.mock("@/domains/esports/adapters/lineup", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/domains/esports/adapters/lineup")>()),
  fieldExistingTeam: vi.fn(),
  publishLineup: vi.fn(),
}))

vi.mock("@/domains/user", () => ({loadMemberAccounts: vi.fn(async () => [])}))

const season = {id: 3, name: "2025/26", startDate: "2025-09-01", endDate: "2026-08-31"}

// The dialog portals its content out of the component's subtree, so it is replaced by a
// pass-through: what is under test is what the editor puts inside it.
const stubs = {
  IslandDialog: {
    props: ["open"],
    setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
      () => h("div", [slots["default"]?.(), slots["footer"]?.()]),
  },
  ConfirmDialog: true,
  ImagePicker: true,
  IslandChoice: true,
  IslandPicker: true,
  LineupSource: true,
}

const openEditor = async () => {
  const wrapper = mount(LineupEditor, {
    props: {open: true, game: "VAL", teamId: 7, teamName: "Blueshell", season, accent: "#0af"},
    global: {stubs},
  })
  await settle()
  return wrapper
}

const entry = (id: number, handle: string) => ({
  id, handle, role: "PLAYER", sortIndex: id, userId: null, displayName: null,
  roleTitle: null, description: null, icon: null,
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(publishLineup).mockResolvedValue({ok: true})
  vi.mocked(fieldExistingTeam).mockResolvedValue({ok: true})
})

/**
 * Fielding a team that played before, where the line-up being carried across could not be read.
 *
 * The source component reports it and the adapter refuses to write on it; what this holds to is
 * that the button is not offered in the first place.
 */
describe("LineupEditor, fielding from a line-up that could not be read", () => {
  const carried = (unread: boolean) => ({
    from: {game: "VAL", season: {id: 2, name: "2024/25", startDate: "2024-09-01", endDate: "2025-08-31"}},
    entries: [],
    unread,
  })

  const pickTeamThen = async (unread: boolean) => {
    vi.mocked(loadTeams).mockResolvedValue([{id: 9, name: "Old squad"}] as never)
    const wrapper = mount(LineupEditor, {
      props: {open: true, game: "VAL", teamId: null, teamName: "", season, accent: "#0af"},
      global: {stubs},
    })
    await settle()
    wrapper.findComponent({name: "IslandPicker"}).vm.$emit("pick", "9")
    await settle()
    wrapper.findComponent({name: "LineupSource"}).vm.$emit("update:carried", carried(unread))
    await settle()
    return wrapper
  }

  it("does not offer to field the team, so no roster is copied from a read that failed", async () => {
    const wrapper = await pickTeamThen(true)

    expect(wrapper.find('[data-testid="field-team-confirm"]').attributes("disabled")).toBeDefined()
  })

  it("fields the team from a source that was read and holds nobody", async () => {
    const wrapper = await pickTeamThen(false)

    const confirm = wrapper.find('[data-testid="field-team-confirm"]')
    expect(confirm.attributes("disabled")).toBeUndefined()

    await confirm.trigger("click")
    await settle()

    expect(fieldExistingTeam).toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toBeDefined()
  })

  it("stays open on a refused fielding, and says who could not be carried across", async () => {
    vi.mocked(fieldExistingTeam)
      .mockResolvedValue({ok: false, reason: "Nope.", written: 0, stage: "fielding"})

    const wrapper = await pickTeamThen(false)
    await wrapper.find('[data-testid="field-team-confirm"]').trigger("click")
    await settle()

    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.emitted("update:open")).toBeUndefined()
    expect(wrapper.find('[data-testid="lineup-failure"]').text()).toContain("Nope.")
  })
})

describe("LineupEditor, on a roster that could not be read", () => {
  it("shows the line-up it read, and offers to save it", async () => {
    vi.mocked(loadRoster).mockResolvedValue([entry(1, "nova")] as never)

    const wrapper = await openEditor()

    expect(wrapper.find('[data-testid="lineup-handle-0"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="lineup-unknown"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lineup-save"]').attributes("disabled")).toBeUndefined()
  })

  it("says nobody has played where the read came back empty", async () => {
    vi.mocked(loadRoster).mockResolvedValue([] as never)

    const wrapper = await openEditor()

    expect(wrapper.find('[data-testid="lineup-empty"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="lineup-unknown"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lineup-save"]').attributes("disabled")).toBeUndefined()
  })

  it("says the line-up could not be read rather than that it is empty", async () => {
    vi.mocked(loadRoster).mockResolvedValue(null)

    const wrapper = await openEditor()

    expect(wrapper.find('[data-testid="lineup-unknown"]').text()).toContain("could not be read")
    expect(wrapper.find('[data-testid="lineup-empty"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="lineup-add"]').exists()).toBe(false)
  })

  it("cannot be saved, so the emptiness is never written over the real roster", async () => {
    vi.mocked(loadRoster).mockResolvedValue(null)

    const wrapper = await openEditor()
    expect(wrapper.find('[data-testid="lineup-save"]').attributes("disabled")).toBeDefined()

    await wrapper.find('[data-testid="lineup-save"]').trigger("click")
    await settle()

    expect(publishLineup).not.toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toBeUndefined()
  })
})

/**
 * Several writes stand behind one Save, and a refusal partway leaves what came before it
 * written. Closing on "saved" would report a line-up that only half landed.
 */
describe("LineupEditor, when the publish is refused", () => {
  beforeEach(() => {
    vi.mocked(loadRoster).mockResolvedValue([entry(1, "nova")] as never)
  })

  it("does not report the line-up saved, and says the team itself is", async () => {
    vi.mocked(publishLineup).mockResolvedValue({
      ok: false, reason: "That team could not be fielded this season.", written: 0,
      stage: "fielding",
    })

    const wrapper = await openEditor()
    await wrapper.find('[data-testid="lineup-save"]').trigger("click")
    await settle()

    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.emitted("update:open")).toBeUndefined()
    expect(wrapper.text()).toContain("could not be fielded")
    expect(wrapper.text()).toContain("The team itself is saved.")
  })

  it("says how much of the line-up was written before the entry that stopped it", async () => {
    vi.mocked(publishLineup)
      .mockResolvedValue({ok: false, reason: "Nope.", written: 3, stage: "roster"})

    const wrapper = await openEditor()
    await wrapper.find('[data-testid="lineup-save"]').trigger("click")
    await settle()

    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.find('[data-testid="lineup-failure"]').text())
      .toContain("The first 3 of the line-up entries are saved.")
  })
})
