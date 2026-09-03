import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import LineupEditor from "@/domains/esports/island/LineupEditor.vue"
import {
  addToRoster,
  fieldTeamInSeason,
  loadRoster,
  loadTeams,
  saveRosterEntry,
  saveTeamAs,
} from "@/domains/esports/adapters/esports"
import {settle} from "../../../helpers/testUtils"

vi.mock("@/domains/esports/adapters/esports", () => ({
  addToRoster: vi.fn(),
  dropRosterEntry: vi.fn(),
  dropTeam: vi.fn(),
  saveTeamOrReason: vi.fn(),
  fieldTeamInSeason: vi.fn(),
  linkRosterMember: vi.fn(),
  loadRoster: vi.fn(),
  loadTeamSeasons: vi.fn(),
  loadTeams: vi.fn(),
  saveRosterEntry: vi.fn(),
  saveTeamAs: vi.fn(),
  storePicture: vi.fn(),
  unfieldTeamFromSeason: vi.fn(),
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

/**
 * Fielding a team that played before, where the line-up being carried across could not be read.
 *
 * The source component reports it, but the write happens here — and `carryFrom` has the api
 * copy the whole roster, so this is the path that could publish a squad nobody read.
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
    const vm = wrapper.vm as unknown as {picked: unknown; onCarried: (c: unknown) => void}
    vm.picked = {id: 9, name: "Old squad"}
    vm.onCarried(carried(unread))
    await settle()
    return wrapper
  }

  it("does not field the team, so no roster is copied from a read that failed", async () => {
    const wrapper = await pickTeamThen(true)

    expect(wrapper.find('[data-testid="field-team-confirm"]').attributes("disabled")).toBeDefined()

    await (wrapper.vm as unknown as {fieldPicked: () => Promise<void>}).fieldPicked()

    expect(fieldTeamInSeason).not.toHaveBeenCalled()
    expect(addToRoster).not.toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toBeUndefined()
  })

  it("fields the team from a source that was read and holds nobody", async () => {
    vi.mocked(fieldTeamInSeason).mockResolvedValue({} as never)
    const wrapper = await pickTeamThen(false)

    expect(wrapper.find('[data-testid="field-team-confirm"]').attributes("disabled")).toBeUndefined()

    await (wrapper.vm as unknown as {fieldPicked: () => Promise<void>}).fieldPicked()

    expect(fieldTeamInSeason).toHaveBeenCalled()
  })
})

describe("LineupEditor, on a roster that could not be read", () => {
  beforeEach(() => {
    vi.mocked(saveTeamAs).mockResolvedValue({ok: true, team: {id: 7, name: "Blueshell"}} as never)
  })

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

    expect(saveTeamAs).not.toHaveBeenCalled()
    expect(addToRoster).not.toHaveBeenCalled()
    expect(saveRosterEntry).not.toHaveBeenCalled()
    expect(wrapper.emitted("saved")).toBeUndefined()
  })
})

/**
 * Several writes stand behind one Save. The adapters answer a refusal rather than throwing,
 * so discarding it closed the dialog on "saved" with only part of the line-up written.
 */
describe("LineupEditor, when one of the writes is refused", () => {
  beforeEach(() => {
    vi.mocked(saveTeamAs).mockResolvedValue({ok: true, team: {id: 7, name: "Blueshell"}} as never)
    vi.mocked(loadRoster).mockResolvedValue([] as never)
    vi.mocked(fieldTeamInSeason).mockResolvedValue({} as never)
  })

  it("does not report the line-up saved when the team could not be fielded", async () => {
    vi.mocked(fieldTeamInSeason).mockResolvedValue(null)

    const wrapper = await openEditor()
    await wrapper.find('[data-testid="lineup-save"]').trigger("click")
    await settle()

    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.emitted("update:open")).toBeUndefined()
    expect(wrapper.text()).toContain("could not be fielded")
  })

  it("says which entry stopped it, rather than closing on a half-written roster", async () => {
    vi.mocked(addToRoster).mockResolvedValue(null)

    const wrapper = await openEditor()
    await wrapper.find('[data-testid="lineup-add"]').trigger("click")
    await settle()
    const handle = wrapper.find('[data-testid="lineup-handle-0"] input')
    if (handle.exists()) await handle.setValue("nova")
    await settle()

    await wrapper.find('[data-testid="lineup-save"]').trigger("click")
    await settle()

    expect(wrapper.emitted("saved")).toBeUndefined()
    expect(wrapper.emitted("update:open")).toBeUndefined()
  })
})
