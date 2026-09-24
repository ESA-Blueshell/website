import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addCommittee,
  deleteCommittee,
  listCommittees,
  listMyCommittees,
  loadCommitteePage,
  loadCommittees,
  saveCommitteeAsBoard,
  saveGameOrganisers,
  saveOwnCommitteePage,
  setCommitteeArchived,
  storeCommitteeBanner,
} from "@/domains/committees/adapters/committees"
import * as api from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: vi.fn(),
  findCommitteesByUserId: vi.fn(),
  deleteCommitteeById: vi.fn(),
  findCommitteePage: vi.fn(),
  createCommittee: vi.fn(),
  updateCommittee: vi.fn(),
  updateCommitteePage: vi.fn(),
  archiveCommittee: vi.fn(),
  uploadCommitteeBanner: vi.fn(),
  uploadPublicImage: vi.fn(),
  setGameOrganisers: vi.fn(),
}))

const banner = {url: "/files/public/committee-banners/l.webp", path: "committee-banners/l.webp", renditions: [{url: "/files/public/640/l.webp", width: 640}]}
const lan = {id: 1, name: "LanCie", slug: "lancie", listed: true, archived: false, banner, gameCodes: []}
const resolved = "http://localhost:3000/api/files/public/committee-banners/l.webp"
const draft = {name: "LanCie", slug: "", listed: true, description: "LANs", banner: null, members: [{userId: 4, role: null}], gameCodes: ["CS2"]}

beforeEach(() => vi.clearAllMocks())

describe("reading committees", () => {
  it("answers every committee with its banner resolved against the api, and none where there is no body", async () => {
    vi.mocked(api.findCommittees).mockResolvedValueOnce({data: [lan]} as never).mockResolvedValueOnce({} as never)

    const [first] = await listCommittees()
    expect(first.banner?.url).toBe(resolved)
    expect(first.banner?.renditions[0].url).toBe("http://localhost:3000/api/files/public/640/l.webp")
    await expect(listCommittees()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with an empty listing", async () => {
    vi.mocked(api.findCommittees).mockRejectedValue(new Error("refused"))

    await expect(listCommittees()).rejects.toBeDefined()
  })

  it("answers the committees the account belongs to, and none where there is no body", async () => {
    vi.mocked(api.findCommitteesByUserId).mockResolvedValueOnce({data: [{id: 2, name: "EventCie"}]} as never).mockResolvedValueOnce({} as never)

    await expect(listMyCommittees()).resolves.toEqual([{id: 2, name: "EventCie", banner: null}])
    await expect(listMyCommittees()).resolves.toEqual([])
  })

  it("answers nothing, rather than throwing, where the committees pages cannot read them", async () => {
    vi.mocked(api.findCommittees).mockResolvedValueOnce({data: [lan]} as never).mockResolvedValueOnce({error: {status: 500}} as never)

    expect((await loadCommittees())[0].banner?.url).toBe(resolved)
    await expect(loadCommittees()).resolves.toEqual([])
  })

  it("answers one committee's page by its address, or null where none answers to it", async () => {
    vi.mocked(api.findCommitteePage).mockResolvedValueOnce({data: {...lan, members: []}} as never).mockResolvedValueOnce({error: {status: 404}} as never)

    expect((await loadCommitteePage("lancie"))?.banner?.url).toBe(resolved)
    expect(api.findCommitteePage).toHaveBeenCalledWith({path: {address: "lancie"}})
    await expect(loadCommitteePage("gone")).resolves.toBeNull()
  })
})

describe("writing committees", () => {
  it("adds a committee, leaving an empty address for the api to make", async () => {
    vi.mocked(api.createCommittee).mockResolvedValueOnce({data: lan} as never).mockResolvedValueOnce({error: {code: "CommitteeAddressTaken", address: "lancie", committeeName: "LanCie"}} as never)

    expect(await addCommittee(draft)).toMatchObject({ok: true, committee: {id: 1}})
    expect(api.createCommittee).toHaveBeenCalledWith({body: {
      name: "LanCie", slug: undefined, listed: true, description: "LANs", banner: undefined, members: [{userId: 4, role: undefined}], gameCodes: ["CS2"],
    }})
    expect(await addCommittee(draft)).toEqual({ok: false, reason: "The address 'lancie' is already used by LanCie."})
  })

  it("saves the board's correction with its version, and a refusal in words", async () => {
    vi.mocked(api.updateCommittee).mockResolvedValueOnce({data: lan} as never).mockResolvedValueOnce({error: {}} as never)

    expect(await saveCommitteeAsBoard(1, 3, {...draft, slug: "lan", banner: "b.webp", members: [{userId: 4, role: "Chair"}]})).toMatchObject({ok: true})
    expect(api.updateCommittee).toHaveBeenCalledWith({path: {id: 1}, body: expect.objectContaining({slug: "lan", banner: "b.webp", version: 3, members: [{userId: 4, role: "Chair"}]})})
    expect(await saveCommitteeAsBoard(1, 3, draft)).toEqual({ok: false, reason: "The committee could not be saved."})
  })

  it("saves what a committee's own members change", async () => {
    vi.mocked(api.updateCommitteePage).mockResolvedValueOnce({data: lan} as never).mockResolvedValueOnce({error: {code: "GameArchived", gameName: "CS:GO"}} as never)

    expect(await saveOwnCommitteePage(1, {description: "LANs", banner: null, gameCodes: []})).toMatchObject({ok: true})
    expect(api.updateCommitteePage).toHaveBeenCalledWith({path: {id: 1}, body: {description: "LANs", banner: undefined, gameCodes: []}})
    expect(await saveOwnCommitteePage(1, {description: "LANs", banner: "b.webp", gameCodes: ["CSGO"]}))
      .toEqual({ok: false, reason: "CS:GO is archived, so it cannot be newly picked."})
  })

  it("archives a committee and brings it back, each refusal said its own way", async () => {
    vi.mocked(api.archiveCommittee).mockResolvedValueOnce({data: {...lan, archived: true}} as never).mockResolvedValue({error: {}} as never)

    expect(await setCommitteeArchived(1, true)).toMatchObject({ok: true, committee: {archived: true}})
    expect(await setCommitteeArchived(1, true)).toEqual({ok: false, reason: "The committee could not be archived."})
    expect(await setCommitteeArchived(1, false)).toEqual({ok: false, reason: "The committee could not be brought back."})
  })

  it("stores a banner through the committee's own route, or as a public picture for one being added", async () => {
    const file = new File(["x"], "b.png")
    vi.mocked(api.uploadCommitteeBanner).mockResolvedValue({data: banner} as never)
    vi.mocked(api.uploadPublicImage).mockResolvedValueOnce({data: banner} as never).mockResolvedValueOnce({error: {}} as never)

    expect(await storeCommitteeBanner(file, 1)).toMatchObject({ok: true, picture: {url: resolved}})
    expect(api.uploadCommitteeBanner).toHaveBeenCalledWith({path: {id: 1}, body: {file}})
    expect(await storeCommitteeBanner(file, null)).toMatchObject({ok: true})
    expect(api.uploadPublicImage).toHaveBeenCalledWith({query: {type: "COMMITTEE_BANNER"}, body: {file}})
    expect(await storeCommitteeBanner(file, null)).toEqual({ok: false, reason: "That picture could not be stored."})
  })

  it("sets which committees organise events for a game", async () => {
    vi.mocked(api.setGameOrganisers).mockResolvedValueOnce({data: []} as never).mockResolvedValueOnce({error: {}} as never)

    expect(await saveGameOrganisers("CS2", [1, 2])).toEqual({ok: true})
    expect(api.setGameOrganisers).toHaveBeenCalledWith({path: {game: "CS2"}, body: {committeeIds: [1, 2]}})
    expect(await saveGameOrganisers("CS2", [])).toEqual({ok: false, reason: "The committees could not be saved."})
  })

  it("removes a committee by its number", async () => {
    vi.mocked(api.deleteCommitteeById).mockResolvedValue({} as never)

    await deleteCommittee(5)

    expect(api.deleteCommitteeById).toHaveBeenCalledWith({path: {id: 5}, throwOnError: true})
  })
})
