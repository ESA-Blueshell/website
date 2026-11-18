import type {Page, Route} from "@playwright/test"
import {expect, test} from "./test"
import {installApiMocks, loginAsBoard, loginAsMember} from "./mocks"

const PAGE = "/events/ost-top-50"

const celeste = {id: 1, igdbId: 26226, name: "Celeste", firstReleaseYear: 2018, cover: null}
const hollow = {id: 2, igdbId: 14593, name: "Hollow Knight", firstReleaseYear: 2017, cover: null}
const track = (id: number, candidate: typeof celeste, title: string) => ({
  id,
  candidate,
  source: "YOUTUBE",
  externalId: `video000${id}`.slice(-11),
  url: `https://www.youtube.com/watch?v=video000${id}`,
  title,
  thumbnail: null,
})
const resurrections = track(11, celeste, "Resurrections")
const firstSteps = track(12, celeste, "First Steps")
const greenpath = track(21, hollow, "Greenpath")

const vote = {
  kind: "OST_TOP_50",
  year: 2026,
  version: 3,
  state: "VOTING",
  opensAt: "2026-11-30T23:00:00Z",
  closesAt: "2026-12-21T22:59:00Z",
  revealAt: "2026-12-28T19:00:00Z",
  ballots: 1,
  results: [],
  points: [12, 10, 8, 6, 4],
  picksTrack: true,
  releasedInYear: false,
}

/**
 * The vote endpoints, answered from memory over the shared stand-in: what the page sent is kept
 * in [sent], and a saved ballot is read back as saved.
 */
async function standInVote(page: Page) {
  const sent: Array<{method: string; path: string; body: unknown}> = []
  const tracks = new Map([[11, resurrections], [12, firstSteps], [21, greenpath]])
  let ballot: {version: number | null; submittedAt: string | null; picks: unknown[]} = {version: null, submittedAt: null, picks: []}
  const json = (route: Route, body: unknown, status = 200) =>
    route.fulfill({status, contentType: "application/json", body: JSON.stringify(body)})

  await page.route(/\/api\/(annual-votes|vote-candidates|vote-tracks)/, async route => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace(/^\/api/, "")
    const method = request.method()
    const body = request.postDataJSON() as Record<string, unknown> | null
    if (method !== "GET") sent.push({method, path, body})

    if (path === "/annual-votes/OST_TOP_50/current") return json(route, vote)
    if (path === "/annual-votes/OST_TOP_50/2026" && method === "PUT") return json(route, {...vote, ...body, version: vote.version + 1})
    if (path === "/annual-votes/OST_TOP_50/2026/standings") {
      return json(route, [{place: 1, candidate: celeste, track: resurrections, points: 22, voters: 2}])
    }
    if (path === "/annual-votes/OST_TOP_50/2026/ballot" && method === "GET") return json(route, ballot)
    if (path === "/annual-votes/OST_TOP_50/2026/ballot" && method === "PUT") {
      const picks = (body!.picks as Array<{candidateId: number; trackId: number}>).map(pick => ({
        candidate: pick.candidateId === 1 ? celeste : hollow,
        track: tracks.get(pick.trackId),
      }))
      ballot = {version: (ballot.version ?? 0) + 1, submittedAt: "2026-12-10T12:00:00Z", picks}
      return json(route, ballot)
    }
    if (path === "/vote-candidates" && method === "GET") {
      const query = url.searchParams.get("query") ?? ""
      const games = [
        {igdbId: 26226, name: "Celeste", firstReleaseYear: 2018, coverImageId: null},
        {igdbId: 14593, name: "Hollow Knight", firstReleaseYear: 2017, coverImageId: null},
      ]
      return json(route, games.filter(one => one.name.toLowerCase().startsWith(query.toLowerCase())))
    }
    if (path === "/vote-candidates/26226/tracks") return json(route, [resurrections, firstSteps])
    if (path === "/vote-candidates/14593/tracks") return json(route, [])
    if (path === "/vote-tracks" && method === "POST") return json(route, greenpath, 201)
    if (path === "/vote-tracks/12/merge") return json(route, resurrections)
    return json(route, {title: `No stand-in for ${method} ${path}`, status: 501}, 501)
  })
  return sent
}

test.describe("the annual vote", () => {
  test("a member fills two places with a game and a song each, casts the vote and changes it", async ({page, context}) => {
    await installApiMocks(page)
    const sent = await standInVote(page)
    await loginAsMember(context)

    await page.goto(PAGE)
    await expect(page.getByTestId("vote-free")).toHaveCount(5)
    const place = page.getByTestId("vote-finder")
    const game = place.getByRole("combobox").nth(0)
    const song = place.getByRole("combobox").nth(1)

    await game.click()
    await game.fill("cel")
    await page.getByTestId("vote-game-26226").click()
    await song.click()
    await page.getByTestId("vote-song-11").click()

    await game.click()
    await game.fill("hol")
    await page.getByTestId("vote-game-14593").click()
    await song.click()
    await song.fill("https://youtu.be/video000021")
    await page.getByTestId("vote-song-link").click()

    await expect(page.getByTestId("vote-pick")).toHaveText([/Celeste.*Resurrections/s, /Hollow Knight.*Greenpath/s])
    await page.getByTestId("vote-save").click()
    await expect(page.getByTestId("vote-saved")).toBeVisible()

    await page.getByTestId("vote-pick-down-0").click()
    await page.getByTestId("vote-save").click()
    await expect(page.getByTestId("vote-pick")).toHaveText([/Hollow Knight/, /Celeste/])

    // The list moves at once; the save that follows is what the api must see.
    const casts = () => sent.filter(one => one.method === "PUT" && one.path.endsWith("/ballot")).map(one => one.body)
    await expect.poll(casts).toEqual([
      {picks: [{candidateId: 1, trackId: 11}, {candidateId: 2, trackId: 21}], version: null},
      {picks: [{candidateId: 2, trackId: 21}, {candidateId: 1, trackId: 11}], version: 1},
    ])
    expect(sent).toContainEqual({
      method: "POST",
      path: "/vote-tracks",
      body: {igdbId: 14593, url: "https://youtu.be/video000021", kind: "OST_TOP_50", year: 2026},
    })
  })

  test("a member on a phone drags a pick to the top by its handle @phone", async ({page, context}) => {
    await installApiMocks(page)
    const sent = await standInVote(page)
    await loginAsMember(context)
    await page.goto(PAGE)
    const place = page.getByTestId("vote-finder")
    for (const [term, igdbId, song] of [["cel", 26226, "vote-song-11"], ["hol", 14593, null]] as const) {
      await place.getByRole("combobox").nth(0).click()
      await place.getByRole("combobox").nth(0).fill(term)
      await page.getByTestId(`vote-game-${igdbId}`).click()
      await place.getByRole("combobox").nth(1).click()
      if (song) await page.getByTestId(song).click()
      else {
        await place.getByRole("combobox").nth(1).fill("https://youtu.be/video000021")
        await page.getByTestId("vote-song-link").click()
      }
    }
    await expect(page.getByTestId("vote-pick-up-1")).toBeHidden()

    const grip = (await page.getByTestId("vote-pick-grip-1").boundingBox())!
    await page.mouse.move(grip.x + grip.width / 2, grip.y + grip.height / 2)
    await page.mouse.down()
    await page.mouse.move(grip.x + grip.width / 2, grip.y - 120, {steps: 10})
    await page.mouse.up()
    await expect(page.getByTestId("vote-pick")).toHaveText([/Hollow Knight/, /Celeste/])

    await page.getByTestId("vote-save").click()
    const casts = () => sent.filter(one => one.method === "PUT" && one.path.endsWith("/ballot")).map(one => one.body)
    await expect.poll(casts).toEqual([{picks: [{candidateId: 2, trackId: 21}, {candidateId: 1, trackId: 11}], version: null}])
  })

  test("the board saves the dates at the version it read, and merges a duplicate track", async ({page, context}) => {
    await installApiMocks(page)
    const sent = await standInVote(page)
    await loginAsBoard(context)

    await page.goto(PAGE)
    await expect(page.getByTestId("vote-board")).toContainText("in Amsterdam time")
    await expect(page.getByTestId("vote-board-picks")).toHaveCount(0)
    await page.getByTestId("vote-board-save").click()
    await expect.poll(() => sent.find(one => one.method === "PUT" && one.path === "/annual-votes/OST_TOP_50/2026")?.body).toEqual({
      opensAt: "2026-11-30T23:00:00Z",
      closesAt: "2026-12-21T22:59:00Z",
      revealAt: "2026-12-28T19:00:00Z",
      version: 3,
    })

    await expect(page.getByTestId("vote-standing")).toContainText("Celeste")
    await page.getByTestId("vote-standing-open-26226").click()
    await page.getByTestId("vote-merge-12").click()
    await expect.poll(() => sent.find(one => one.path === "/vote-tracks/12/merge")?.body).toEqual({intoId: 11})
  })
})
