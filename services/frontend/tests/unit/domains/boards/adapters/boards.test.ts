import {describe, expect, it, vi} from "vitest"
import {
  addMemberOrReason,
  dropBoard,
  dropMemberOrReason,
  linkMemberAccountOrReason,
  loadBoards,
  memberTitle,
  saveBoardOrReason,
  saveMemberOrReason,
  storeBoardPhoto,
  storeMemberPortrait,
} from "@/domains/boards/adapters/boards"
import {
  addMember,
  createBoard,
  deleteBoard,
  findAllBoards,
  linkMember,
  removeMember,
  updateBoard,
  updateMember,
  uploadPublicImage,
} from "@/services/api"

vi.mock("@/services/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/services/api")>()),
  // Stubbed rather than left real: it reads the page's origin, and what matters here is that
  // every picture the adapter answers with went through it.
  apiUrl: (path: string) => `https://api.test${path}`,
  addMember: vi.fn(),
  createBoard: vi.fn(),
  deleteBoard: vi.fn(),
  findAllBoards: vi.fn(),
  linkMember: vi.fn(),
  removeMember: vi.fn(),
  updateBoard: vi.fn(),
  updateMember: vi.fn(),
  uploadPublicImage: vi.fn(),
}))

const image = (path: string) => ({
  id: 1,
  url: path,
  renditions: [{width: 320, url: `${path}?w=320`}],
})

const board = (over: Record<string, unknown> = {}) => ({
  id: 1,
  number: 9,
  name: "Board 9",
  startDate: "2024-09-01",
  photo: null,
  members: [],
  ...over,
})

const member = (over: Record<string, unknown> = {}) => ({
  id: 5,
  role: "Chair",
  startDate: "2024-09-01",
  portrait: null,
  ...over,
})

/** The api answers a refusal as a body rather than throwing, so `error` is set and `data` is not. */
const refusal = (body: unknown) => ({error: body, data: undefined}) as never

describe("memberTitle", () => {
  it("puts the nickname between the first name and the rest of it", () => {
    expect(memberTitle({name: "Roos Kruk", nickname: "SkyeWolf"})).toBe('Roos "SkyeWolf" Kruk')
  })

  it("leaves a name without a nickname as it was written", () => {
    expect(memberTitle({name: "Roos Kruk"})).toBe("Roos Kruk")
    expect(memberTitle({name: "Roos Kruk", nickname: null})).toBe("Roos Kruk")
  })

  it("quotes a nickname beside a single name rather than dropping it", () => {
    expect(memberTitle({name: "Roos", nickname: "SkyeWolf"})).toBe('Roos "SkyeWolf"')
  })

  it("answers with nothing where the membership stands under no name at all", () => {
    expect(memberTitle({})).toBe("")
    expect(memberTitle({name: null})).toBe("")
  })
})

describe("loadBoards", () => {
  it("answers with the newest board first, whatever order the api listed them in", async () => {
    vi.mocked(findAllBoards).mockResolvedValue({
      data: [
        board({id: 1, number: 8, startDate: "2023-09-01"}),
        board({id: 2, number: 10, startDate: "2025-09-01"}),
        board({id: 3, number: 9, startDate: "2024-09-01"}),
      ],
    } as never)

    await expect(loadBoards()).resolves.toMatchObject([{number: 10}, {number: 9}, {number: 8}])
  })

  it("resolves the board's photograph and every portrait on it to where they are served", async () => {
    vi.mocked(findAllBoards).mockResolvedValue({
      data: [board({photo: image("/files/photo"), members: [member({portrait: image("/files/face")})]})],
    } as never)

    const [only] = await loadBoards()

    expect(only.photo?.url).toBe("https://api.test/files/photo")
    expect(only.photo?.renditions[0].url).toBe("https://api.test/files/photo?w=320")
    expect(only.members[0].portrait?.url).toBe("https://api.test/files/face")
    expect(only.members[0].portrait?.renditions[0].url).toBe("https://api.test/files/face?w=320")
  })

  it("leaves a board without a photograph without one", async () => {
    vi.mocked(findAllBoards).mockResolvedValue({data: [board({members: [member()]})]} as never)

    const [only] = await loadBoards()

    expect(only.photo).toBeNull()
    expect(only.members[0].portrait).toBeNull()
  })

  it("answers with no boards where the read failed", async () => {
    vi.mocked(findAllBoards).mockResolvedValue(refusal({status: 500}))

    await expect(loadBoards()).resolves.toEqual([])
  })
})

describe("storing a picture", () => {
  it("answers with the stored picture, resolved to where it is served", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue({data: image("/files/photo")} as never)

    const stored = await storeBoardPhoto(new File([], "photo.png"))

    expect(stored).toEqual({ok: true, picture: expect.objectContaining({url: "https://api.test/files/photo"})})
    expect(uploadPublicImage).toHaveBeenCalledWith({query: {type: "BOARD_PHOTO"}, body: {file: expect.any(File)}})
  })

  it("stores a portrait under its own kind, which is not the board photograph's", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue({data: image("/files/face")} as never)

    await storeMemberPortrait(new File([], "face.png"))

    expect(uploadPublicImage).toHaveBeenCalledWith({query: {type: "BOARD_PORTRAIT"}, body: {file: expect.any(File)}})
  })

  it("reports a picture the converter refused in the api's own words", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue(refusal({detail: "That file is not an image."}))

    await expect(storeBoardPhoto(new File([], "notes.txt"))).resolves.toEqual({
      ok: false,
      reason: "That file is not an image.",
    })
  })

  it("falls back to the summary, and then to a sentence of its own", async () => {
    vi.mocked(uploadPublicImage).mockResolvedValue(refusal({title: "Unsupported Media Type"}))
    await expect(storeBoardPhoto(new File([], "notes.txt"))).resolves.toEqual({
      ok: false,
      reason: "Unsupported Media Type",
    })

    vi.mocked(uploadPublicImage).mockResolvedValue(refusal(null))
    await expect(storeBoardPhoto(new File([], "notes.txt"))).resolves.toEqual({
      ok: false,
      reason: "That picture could not be stored.",
    })
  })
})

describe("saveBoardOrReason", () => {
  it("creates a board that has no id yet, and carries no version with it", async () => {
    vi.mocked(createBoard).mockResolvedValue({data: board()} as never)

    await expect(saveBoardOrReason({number: 9, startDate: "2024-09-01"})).resolves.toEqual({
      ok: true,
      board: expect.objectContaining({number: 9}),
    })
    expect(createBoard).toHaveBeenCalledWith({body: expect.objectContaining({number: 9, startDate: "2024-09-01"})})
    expect(updateBoard).not.toHaveBeenCalled()
  })

  // A missing version writing as 0 is what makes an update of a board read before the field
  // existed fail on the api's optimistic lock rather than overwrite silently.
  it("updates a board that has an id, under the version it was read at", async () => {
    vi.mocked(updateBoard).mockResolvedValue({data: board()} as never)

    await saveBoardOrReason({id: 1, number: 9, startDate: "2024-09-01", version: 3})
    expect(updateBoard).toHaveBeenCalledWith({path: {id: 1}, body: expect.objectContaining({version: 3})})

    await saveBoardOrReason({id: 1, number: 9, startDate: "2024-09-01"})
    expect(updateBoard).toHaveBeenLastCalledWith({path: {id: 1}, body: expect.objectContaining({version: 0})})
  })

  // Null is what the dialog holds for a field nobody filled in; sent as null it would clear a
  // column, sent as undefined the api leaves the field out of the write.
  it("sends a field nobody filled in as absent rather than as null", async () => {
    vi.mocked(createBoard).mockResolvedValue({data: board()} as never)

    await saveBoardOrReason({
      number: 9,
      startDate: "2024-09-01",
      name: null,
      candidate: null,
      cheer: null,
      accent: null,
      description: null,
      endDate: null,
      image: null,
      photo: null,
    })

    expect(createBoard).toHaveBeenCalledWith({
      body: {
        number: 9,
        startDate: "2024-09-01",
        name: undefined,
        candidate: undefined,
        cheer: undefined,
        accent: undefined,
        description: undefined,
        endDate: undefined,
        image: undefined,
        photo: undefined,
      },
    })
  })

  it("resolves the pictures on a board it saved", async () => {
    vi.mocked(createBoard).mockResolvedValue({data: board({photo: image("/files/photo")})} as never)

    const saved = await saveBoardOrReason({number: 9, startDate: "2024-09-01"})

    expect(saved).toMatchObject({ok: true, board: {photo: {url: "https://api.test/files/photo"}}})
  })

  it("reports a clashing number as the api worded it, so the typist knows which field to change", async () => {
    vi.mocked(createBoard).mockResolvedValue(refusal({detail: "Board 9 already exists."}))

    await expect(saveBoardOrReason({number: 9, startDate: "2024-09-01"})).resolves.toEqual({
      ok: false,
      reason: "Board 9 already exists.",
    })
  })

  it("reports a save that answered with neither an error nor a board", async () => {
    vi.mocked(createBoard).mockResolvedValue({data: undefined} as never)

    await expect(saveBoardOrReason({number: 9, startDate: "2024-09-01"})).resolves.toEqual({
      ok: false,
      reason: "That board could not be saved.",
    })
  })
})

describe("dropBoard", () => {
  it("answers that the board went", async () => {
    vi.mocked(deleteBoard).mockResolvedValue({data: undefined, error: undefined} as never)

    await expect(dropBoard(1)).resolves.toEqual({ok: true})
    expect(deleteBoard).toHaveBeenCalledWith({path: {id: 1}})
  })

  // The refusal is composed on this side from the code, per ADR-026, so the count in the
  // sentence comes from the api's fields rather than from its prose.
  it("composes the refusal for a board that still has members on it", async () => {
    vi.mocked(deleteBoard).mockResolvedValue(
      refusal({code: "BoardHoldsMembers", number: 9, members: 2, detail: "Conflict"}),
    )

    const refused = await dropBoard(1)

    expect(refused).toMatchObject({ok: false})
    expect((refused as {reason: string}).reason).toContain("Board 9 still has 2 members on it")
  })

  it("falls back to a sentence of its own where the api gave no words", async () => {
    vi.mocked(deleteBoard).mockResolvedValue(refusal({}))

    await expect(dropBoard(1)).resolves.toEqual({ok: false, reason: "The board could not be removed."})
  })
})

describe("board memberships", () => {
  it("adds a member, resolves the portrait, and sends unfilled fields as absent", async () => {
    vi.mocked(addMember).mockResolvedValue({data: member({portrait: image("/files/face")})} as never)

    const added = await addMemberOrReason(1, {role: "Chair", startDate: "2024-09-01", userId: null})

    expect(added).toMatchObject({ok: true, member: {portrait: {url: "https://api.test/files/face"}}})
    expect(addMember).toHaveBeenCalledWith({
      path: {boardId: 1},
      body: {
        role: "Chair",
        startDate: "2024-09-01",
        endDate: undefined,
        userId: undefined,
        displayName: undefined,
        nickname: undefined,
        description: undefined,
        image: undefined,
        portrait: undefined,
      },
    })
  })

  it("reports a member the api would not add in its own words", async () => {
    vi.mocked(addMember).mockResolvedValue(refusal({errors: [{message: "The end date is before the start date."}]}))

    await expect(addMemberOrReason(1, {role: "Chair", startDate: "2024-09-01"})).resolves.toEqual({
      ok: false,
      reason: "The end date is before the start date.",
    })
  })

  // The account is deliberately absent from an update: a membership's account is changed
  // through `linkMember`, so a save cannot detach one by leaving the field empty.
  it("saves a membership without touching the account it stands under", async () => {
    vi.mocked(updateMember).mockResolvedValue({data: member()} as never)

    await saveMemberOrReason(1, 5, {role: "Treasurer", startDate: "2024-09-01"})

    expect(updateMember).toHaveBeenCalledWith({
      path: {boardId: 1, id: 5},
      body: expect.not.objectContaining({userId: expect.anything()}),
    })
  })

  it("reports a membership the api would not save", async () => {
    vi.mocked(updateMember).mockResolvedValue(refusal({}))

    await expect(saveMemberOrReason(1, 5, {role: "Chair", startDate: "2024-09-01"})).resolves.toEqual({
      ok: false,
      reason: "That member could not be saved.",
    })
  })

  it("links a membership to an account", async () => {
    vi.mocked(linkMember).mockResolvedValue({data: member()} as never)

    await expect(linkMemberAccountOrReason(1, 5, 42)).resolves.toMatchObject({ok: true})
    expect(linkMember).toHaveBeenCalledWith({path: {boardId: 1, id: 5}, body: {userId: 42}})
  })

  it("detaches a membership with no account named, which leaves it under its own name", async () => {
    vi.mocked(linkMember).mockResolvedValue({data: member()} as never)

    await linkMemberAccountOrReason(1, 5, null)

    expect(linkMember).toHaveBeenCalledWith({path: {boardId: 1, id: 5}, body: {userId: undefined}})
  })

  it("says which of the two it failed at, since attaching and detaching read alike otherwise", async () => {
    vi.mocked(linkMember).mockResolvedValue(refusal({}))

    await expect(linkMemberAccountOrReason(1, 5, 42)).resolves.toEqual({
      ok: false,
      reason: "That member could not be linked to that account.",
    })
    await expect(linkMemberAccountOrReason(1, 5, null)).resolves.toEqual({
      ok: false,
      reason: "That member could not be detached.",
    })
  })

  it("removes a membership, and reports a removal the api refused", async () => {
    vi.mocked(removeMember).mockResolvedValue({error: undefined} as never)
    await expect(dropMemberOrReason(1, 5)).resolves.toEqual({ok: true})
    expect(removeMember).toHaveBeenCalledWith({path: {boardId: 1, id: 5}})

    vi.mocked(removeMember).mockResolvedValue(refusal({title: "Forbidden"}))
    await expect(dropMemberOrReason(1, 5)).resolves.toEqual({ok: false, reason: "Forbidden"})
  })
})
