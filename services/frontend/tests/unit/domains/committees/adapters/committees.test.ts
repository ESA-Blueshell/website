import {beforeEach, describe, expect, it, vi} from "vitest"
import {
  addCommittee,
  listCommittees,
  removeCommittee,
  listMyCommittees,
  loadCommitteePage,
  loadCommittees,
  saveCommitteeAsBoard,
  saveCommitteeDiscord,
  saveGameOrganisers,
  saveOwnCommitteePage,
  setCommitteeArchived,
  storeCommitteeBanner,
  storeCommitteeIcon,
} from "@/domains/committees/adapters/committees"
import * as api from "@/services/api"
import {aCommittee, aCommitteePage, anImage} from "../../../helpers/apiFixtures"
import {answer, emptyAnswer, refusal} from "../../../helpers/sdkAnswers"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  findCommittees: vi.fn(),
  setCommitteeDiscord: vi.fn(),
  findCommitteesByUserId: vi.fn(),
  deleteCommitteeById: vi.fn(),
  findCommitteePage: vi.fn(),
  createCommittee: vi.fn(),
  updateCommittee: vi.fn(),
  updateCommitteePage: vi.fn(),
  archiveCommittee: vi.fn(),
  uploadCommitteeBanner: vi.fn(),
  uploadCommitteeIcon: vi.fn(),
  uploadPublicImage: vi.fn(),
  setGameOrganisers: vi.fn(),
}))

const banner = anImage({url: "/files/public/committee-banners/l.webp", path: "committee-banners/l.webp", width: null, height: null, renditions: [{url: "/files/public/640/l.webp", width: 640}]})
const lan = aCommittee({banner})
const resolved = "http://localhost:3000/api/files/public/committee-banners/l.webp"
const draft = {name: "LanCie", slug: "", description: "LANs", banner: null, icon: null, members: [{userId: 4, role: null}], gameCodes: ["CS2"]}

beforeEach(() => vi.clearAllMocks())

describe("reading committees", () => {
  it("answers every committee with its banner resolved against the api, and none where there is no body", async () => {
    vi.mocked(api.findCommittees).mockResolvedValueOnce(answer(api.findCommittees, [lan])).mockResolvedValueOnce(emptyAnswer(api.findCommittees))

    const [first] = await listCommittees()
    expect(first?.banner?.url).toBe(resolved)
    expect(first?.banner?.renditions[0]?.url).toBe("http://localhost:3000/api/files/public/640/l.webp")
    await expect(listCommittees()).resolves.toEqual([])
  })

  it("throws on a refusal rather than answering with an empty listing", async () => {
    vi.mocked(api.findCommittees).mockRejectedValue(new Error("refused"))

    await expect(listCommittees()).rejects.toBeDefined()
  })

  it("answers the committees the account belongs to, and none where there is no body", async () => {
    vi.mocked(api.findCommitteesByUserId).mockResolvedValueOnce(answer(api.findCommitteesByUserId, [aCommittee({id: 2, name: "EventCie"})])).mockResolvedValueOnce(emptyAnswer(api.findCommitteesByUserId))

    await expect(listMyCommittees()).resolves.toEqual([aCommittee({id: 2, name: "EventCie", banner: null, icon: null})])
    await expect(listMyCommittees()).resolves.toEqual([])
  })

  it("answers nothing, rather than throwing, where the committees pages cannot read them", async () => {
    vi.mocked(api.findCommittees).mockResolvedValueOnce(answer(api.findCommittees, [lan])).mockResolvedValueOnce(refusal(api.findCommittees, {status: 500}))

    expect((await loadCommittees())[0]?.banner?.url).toBe(resolved)
    await expect(loadCommittees()).resolves.toEqual([])
  })

  it("answers one committee's page by its address, or null where none answers to it", async () => {
    vi.mocked(api.findCommitteePage).mockResolvedValueOnce(answer(api.findCommitteePage, aCommitteePage({banner}))).mockResolvedValueOnce(refusal(api.findCommitteePage, {status: 404}))

    expect((await loadCommitteePage("lancie"))?.banner?.url).toBe(resolved)
    expect(api.findCommitteePage).toHaveBeenCalledWith({path: {address: "lancie"}})
    await expect(loadCommitteePage("gone")).resolves.toBeNull()
  })
})

describe("writing committees", () => {
  it("adds a committee, and says why the api would not", async () => {
    vi.mocked(api.createCommittee).mockResolvedValueOnce(answer(api.createCommittee, lan)).mockResolvedValueOnce(refusal(api.createCommittee, {code: "CommitteeAddressTaken", address: "lancie", committeeName: "LanCie"}))

    expect(await addCommittee(draft)).toMatchObject({ok: true, saved: {id: 1}})
    expect(api.createCommittee).toHaveBeenCalledWith({body: draft})
    expect(await addCommittee(draft)).toEqual({ok: false, reason: "The address 'lancie' is already used by LanCie."})
  })

  it("saves the board's correction with its version, and a refusal in words", async () => {
    vi.mocked(api.updateCommittee).mockResolvedValueOnce(answer(api.updateCommittee, lan)).mockResolvedValueOnce(refusal(api.updateCommittee, {}))

    expect(await saveCommitteeAsBoard(1, {...draft, slug: "lan", banner: "b.webp", members: [{userId: 4, role: "Chair"}], version: 3})).toMatchObject({ok: true})
    expect(api.updateCommittee).toHaveBeenCalledWith({path: {id: 1}, body: expect.objectContaining({slug: "lan", banner: "b.webp", version: 3, members: [{userId: 4, role: "Chair"}]})})
    expect(await saveCommitteeAsBoard(1, {...draft, version: 3})).toEqual({ok: false, reason: "The committee could not be saved."})
  })

  it("saves what a committee's own members change", async () => {
    vi.mocked(api.updateCommitteePage).mockResolvedValueOnce(answer(api.updateCommitteePage, lan)).mockResolvedValueOnce(refusal(api.updateCommitteePage, {code: "GameArchived", gameName: "CS:GO"}))

    const page = {description: "LANs", banner: null, icon: null, gameCodes: [], version: 4}
    expect(await saveOwnCommitteePage(1, page)).toMatchObject({ok: true})
    expect(api.updateCommitteePage).toHaveBeenCalledWith({path: {id: 1}, body: page})
    expect(await saveOwnCommitteePage(1, {...page, banner: "b.webp", gameCodes: ["CSGO"]}))
      .toEqual({ok: false, reason: "CS:GO is archived, so it cannot be newly picked."})
  })

  it("archives a committee and brings it back, each refusal said its own way", async () => {
    vi.mocked(api.archiveCommittee).mockResolvedValueOnce(answer(api.archiveCommittee, aCommittee({banner, archived: true}))).mockResolvedValue(refusal(api.archiveCommittee, {}))

    expect(await setCommitteeArchived(1, true)).toMatchObject({ok: true, saved: {archived: true}})
    expect(await setCommitteeArchived(1, true)).toEqual({ok: false, reason: "The committee could not be archived."})
    expect(await setCommitteeArchived(1, false)).toEqual({ok: false, reason: "The committee could not be brought back."})
  })

  it("stores a banner through the committee's own route, or as a public picture for one being added", async () => {
    const file = new File(["x"], "b.png")
    vi.mocked(api.uploadCommitteeBanner).mockResolvedValue(answer(api.uploadCommitteeBanner, banner))
    vi.mocked(api.uploadPublicImage).mockResolvedValueOnce(answer(api.uploadPublicImage, banner)).mockResolvedValueOnce(refusal(api.uploadPublicImage, {}))

    expect(await storeCommitteeBanner(file, 1)).toMatchObject({ok: true, saved: {url: resolved}})
    expect(api.uploadCommitteeBanner).toHaveBeenCalledWith({path: {id: 1}, body: {file}})
    expect(await storeCommitteeBanner(file, null)).toMatchObject({ok: true})
    expect(api.uploadPublicImage).toHaveBeenCalledWith({query: {type: "COMMITTEE_BANNER"}, body: {file}})
    expect(await storeCommitteeBanner(file, null)).toEqual({ok: false, reason: "That picture could not be stored."})
  })

  it("stores a logo the same two ways", async () => {
    const file = new File(["x"], "i.svg")
    vi.mocked(api.uploadCommitteeIcon).mockResolvedValueOnce(answer(api.uploadCommitteeIcon, banner)).mockResolvedValueOnce(refusal(api.uploadCommitteeIcon, {}))
    vi.mocked(api.uploadPublicImage).mockResolvedValueOnce(answer(api.uploadPublicImage, banner))

    expect(await storeCommitteeIcon(file, 1)).toMatchObject({ok: true, saved: {url: resolved}})
    expect(await storeCommitteeIcon(file, null)).toMatchObject({ok: true})
    expect(api.uploadPublicImage).toHaveBeenCalledWith({query: {type: "COMMITTEE_ICON"}, body: {file}})
    expect(await storeCommitteeIcon(file, 1)).toEqual({ok: false, reason: "That picture could not be stored."})
  })

  it("sets which committees organise events for a game", async () => {
    vi.mocked(api.setGameOrganisers).mockResolvedValueOnce(answer(api.setGameOrganisers, [])).mockResolvedValueOnce(refusal(api.setGameOrganisers, {}))

    expect(await saveGameOrganisers("CS2", [1, 2])).toEqual({ok: true})
    expect(api.setGameOrganisers).toHaveBeenCalledWith({path: {game: "CS2"}, body: {committeeIds: [1, 2]}})
    expect(await saveGameOrganisers("CS2", [])).toEqual({ok: false, reason: "The committees could not be saved."})
  })

  it("deletes a committee by its number, or says why not", async () => {
    vi.mocked(api.deleteCommitteeById).mockResolvedValueOnce(emptyAnswer(api.deleteCommitteeById)).mockResolvedValueOnce(refusal(api.deleteCommitteeById, {}))

    expect(await removeCommittee(5)).toEqual({ok: true})
    expect(api.deleteCommitteeById).toHaveBeenCalledWith({path: {id: 5}})
    expect(await removeCommittee(5)).toEqual({ok: false, reason: "The committee could not be deleted."})
  })
})

describe("a committee's Discord", () => {
  it("is saved as the api answers it, and a Discord that cannot be reached says so", async () => {
    const state = {available: true, roleId: "900", channels: []}
    vi.mocked(api.setCommitteeDiscord).mockResolvedValueOnce(answer(api.setCommitteeDiscord, state))
    await expect(saveCommitteeDiscord(7, {createRole: true, channelIds: []})).resolves.toEqual({ok: true, saved: state})
    expect(api.setCommitteeDiscord).toHaveBeenCalledWith({path: {id: 7}, body: {createRole: true, channelIds: []}})

    vi.mocked(api.setCommitteeDiscord).mockResolvedValueOnce(refusal(api.setCommitteeDiscord, {code: "TargetSystemUnavailable", system: "Discord"}, 503))
    await expect(saveCommitteeDiscord(7, {createRole: true, channelIds: []})).resolves.toEqual({
      ok: false,
      reason: "Discord cannot be reached now, so its role and channels are left as they were.",
    })
  })
})
