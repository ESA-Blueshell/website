import {beforeEach, describe, expect, it, vi} from "vitest"
import {mount} from "@vue/test-utils"
import {h} from "vue"
import TeamEditor from "@/domains/esports/components/TeamEditor.vue"
import {dropTeam, loadRoster, loadTeamSeasons, loadTeams, unfieldTeamFromSeason} from "@/domains/esports/adapters/esports"
import {fieldExistingTeam, publishLineup} from "@/domains/esports/adapters/lineup"
import {loadMemberAccounts} from "@/domains/user"
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

vi.mock("@/domains/user", () => ({loadMemberAccounts: vi.fn()}))

const season = {id: 3, name: "2025/26", startDate: "2025-09-01", endDate: "2026-08-31"}

// The page shell is replaced by a pass-through: what is under test is what the editor puts
// inside it, the form, its footer and its preview.
const stubs = {
  EditPage: {
    setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) =>
      () => h("div", [slots["actions"]?.(), slots["default"]?.(), slots["footer"]?.(), slots["preview"]?.()]),
  },
  PreviewFrame: {setup: (_: unknown, {slots}: {slots: Record<string, () => unknown>}) => () => h("div", slots["default"]?.())},
  SliceBand: {
    props: ["items"],
    setup: (props: {items: unknown[]}, {slots}: {slots: Record<string, () => unknown>}) =>
      () => h("div", {"data-testid": "preview-band"}, [JSON.stringify(props.items), slots["details"]?.()]),
  },
  ConfirmDialog: true,
  ImagePicker: true,
  SegmentedChoice: true,
  SearchPicker: true,
  LineupSource: true,
}

const openEditor = async () => {
  const wrapper = mount(TeamEditor, {
    props: {back: "/competition/valorant", gameName: "Valorant", game: "VAL", teamId: 7, teamName: "Blueshell", season, accent: "#0af"},
    global: {stubs},
  })
  await settle()
  return wrapper
}

/** Types into the island field under [testid], as its control reports what was typed. */
const write = async (wrapper: Awaited<ReturnType<typeof openEditor>>, testid: string, value: string) => {
  wrapper.findAllComponents({name: "FormControl"}).find(one => one.attributes("data-testid") === testid)!.vm.$emit("update:modelValue", value)
  await settle()
}

const memberSearch = (wrapper: Awaited<ReturnType<typeof openEditor>>, index: number) =>
  wrapper.findAllComponents({name: "SearchPicker"}).find(one => one.props("testidPrefix") === `lineup-search-${index}`)!

const entry = (id: number, handle: string) => ({
  id, handle, role: "PLAYER", sortIndex: id, userId: null, displayName: null,
  roleTitle: null, description: null, icon: null,
})

beforeEach(() => {
  vi.clearAllMocks()
  vi.mocked(publishLineup).mockResolvedValue({ok: true})
  vi.mocked(fieldExistingTeam).mockResolvedValue({ok: true})
  vi.mocked(loadMemberAccounts).mockResolvedValue([])
})

/**
 * Fielding a team that played before, where the line-up being carried across could not be read.
 *
 * The source component reports it and the adapter refuses to write on it; what this holds to is
 * that the button is not offered in the first place.
 */
describe("TeamEditor, fielding from a line-up that could not be read", () => {
  const carried = (unread: boolean) => ({
    from: {game: "VAL", season: {id: 2, name: "2024/25", startDate: "2024-09-01", endDate: "2025-08-31"}},
    entries: [],
    unread,
  })

  const pickTeamThen = async (unread: boolean) => {
    vi.mocked(loadTeams).mockResolvedValue([{id: 9, name: "Old squad"}] as never)
    const wrapper = mount(TeamEditor, {
      props: {back: "/competition/valorant", gameName: "Valorant", game: "VAL", teamId: null, teamName: "", season, accent: "#0af"},
      global: {stubs},
    })
    await settle()
    wrapper.findComponent({name: "SearchPicker"}).vm.$emit("pick", "9")
    await settle()
    wrapper.findComponent({name: "LineupSource"}).vm.$emit("update:carried", carried(unread))
    await settle()
    return wrapper
  }

  it("does not offer to field the team, so no roster is copied from a read that failed", async () => {
    const wrapper = await pickTeamThen(true)

    expect(wrapper.find('[data-testid="field-team-confirm"]').attributes("disabled")).toBeDefined()
    expect(wrapper.get("[data-testid=preview-band]").text()).toContain("\"title\":\"Old squad\"")
    await wrapper.get('[data-testid="lineup-cancel"]').trigger("click")
    expect(wrapper.emitted("cancel")).toHaveLength(1)
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
    expect(wrapper.find('[data-testid="lineup-failure"]').text()).toContain("Nope.")
  })

  it("writes the sentence for a source that could not be read, which the adapter does not", async () => {
    vi.mocked(fieldExistingTeam)
      .mockResolvedValue({ok: false, reason: "", written: 0, stage: "source"})

    const wrapper = await pickTeamThen(false)
    await wrapper.find('[data-testid="field-team-confirm"]').trigger("click")
    await settle()

    expect(wrapper.find('[data-testid="lineup-failure"]').text()).toBe(
      "That line-up could not be read, so nobody can be carried across. Pick another line-up, or none.")
  })
})

describe("TeamEditor, on a roster that could not be read", () => {
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

describe("TeamEditor, on accounts that could not be read", () => {
  beforeEach(() => {
    vi.mocked(loadRoster).mockResolvedValue([entry(1, "nova")] as never)
  })

  it("offers the search where the accounts were read", async () => {
    const wrapper = await openEditor()

    expect(memberSearch(wrapper, 0).props("disabled")).toBe(false)
    expect(memberSearch(wrapper, 0).props("placeholder")).toBe("No account")
  })

  it("says the accounts could not be read rather than answering every search with nobody", async () => {
    vi.mocked(loadMemberAccounts).mockResolvedValue(null)

    const wrapper = await openEditor()

    expect(memberSearch(wrapper, 0).props("disabled")).toBe(true)
    expect(wrapper.text()).toContain("The accounts could not be read, so nobody can be attached.")
  })
})

/**
 * Several writes stand behind one Save, and a refusal partway leaves what came before it
 * written. Closing on "saved" would report a line-up that only half landed.
 */
describe("TeamEditor, when the publish is refused", () => {
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

describe("TeamEditor, as a page", () => {
  beforeEach(() => {
    vi.mocked(loadRoster).mockResolvedValue([
      {...entry(1, "nova"), roleTitle: "IGL"},
      {...entry(2, "coach"), role: "COACH"},
    ] as never)
  })

  it("previews the team's slice and its line-up as typed, and saves and leaves", async () => {
    const wrapper = await openEditor()
    const band = () => wrapper.get("[data-testid=preview-band]")

    expect(band().text()).toContain("\"title\":\"Blueshell\"")
    expect(band().text()).toContain("2 on the roster")
    expect(band().text()).toContain("IGL")
    expect(band().text()).toContain("Coach")
    await write(wrapper, "lineup-team-name", "Blueshell Black")
    await write(wrapper, "lineup-handle-0", "")
    expect(band().text()).toContain("\"title\":\"Blueshell Black\"")
    expect(band().text()).toContain("1 on the roster")

    await write(wrapper, "lineup-handle-0", "nova")
    await wrapper.get("[data-testid=lineup-save]").trigger("click")
    await settle()
    expect(wrapper.emitted("saved")).toHaveLength(1)
  })

  it("leaves without writing on Cancel", async () => {
    const wrapper = await openEditor()

    await wrapper.get("[data-testid=lineup-cancel]").trigger("click")

    expect(wrapper.emitted("cancel")).toHaveLength(1)
    expect(publishLineup).not.toHaveBeenCalled()
  })

  it.each([
    ["deleting the team", "lineup-remove-team", "team-remove-dialog"],
    ["taking it out of this season", "lineup-drop-from-season", "team-drop-dialog"],
  ])("leaves once %s is confirmed", async (_, button, dialog) => {
    vi.mocked(loadTeamSeasons).mockResolvedValue([] as never)
    vi.mocked(dropTeam).mockResolvedValue({ok: true})
    vi.mocked(unfieldTeamFromSeason).mockResolvedValue({ok: true})
    const wrapper = await openEditor()

    await wrapper.get(`[data-testid=${button}]`).trigger("click")
    await settle()
    const confirm = wrapper.findAllComponents({name: "ConfirmDialog"}).find(one => one.props("testid") === dialog)!
    expect(confirm.props("open")).toBe(true)
    confirm.vm.$emit("confirm")
    await settle()

    expect(wrapper.emitted("removed")).toHaveLength(1)
  })
})
