import {Buffer} from "node:buffer"
import {expect} from "@playwright/test"
import type {BrowserContext, Locator, Page, Request, Route} from "@playwright/test"
import {
  COOKIE_CONSENT_STORAGE_KEY,
  encodeCookieConsentPayload,
} from "@/config/policies.ts"
import {addressOf} from "@/utils/address"
import type {
  AddBoardMemberRequest,
  Alert,
  AddRosterEntryRequest,
  AddressResponse,
  AssociationStatisticsResponse,
  BlogResponse,
  BoardMemberResponse,
  BoardRequest,
  BoardResponse,
  CasualGameRequest,
  CasualGameResponse,
  CohortDetail,
  CohortSummary,
  CommitteeOwnPageRequest,
  CommitteeResponse,
  CreateCommitteeRequest,
  CreateContributionPeriodRequest,
  CreateEventRequest,
  CreateUserRequest,
  ContributionPeriodResponse,
  ContributionResponse,
  Email,
  ExternalTarget,
  Image,
  EventResponse,
  EventSignUpResponse,
  FieldTeamRequest,
  FieldValidationError,
  GameRostersResponse,
  JobExecution,
  LinkBoardMemberRequest,
  LinkRosterEntryRequest,
  FirstContribution,
  IncassoCandidate,
  IncassoRunView,
  MembershipResponse,
  OwnMandateResponse,
  PublishLineupRequest,
  Role,
  RosterEntryResponse,
  RosterMemberResponse,
  SeasonRequest,
  SignupAddressRequest,
  SignupResumeResponse,
  SeasonResponse,
  TeamRequest,
  TeamRosterResponse,
  TeamResponse,
  TwoFactorStanding,
  UpdateBoardMemberRequest,
  UpdateCommitteeRequest,
  UpdateContributionPeriodRequest,
  UpdateRosterEntryRequest,
  UserDetailResponse,
} from "@/services/api"
import type * as sdk from "@/services/api/blueshell/sdk.gen"
import type {WidgetResponse} from "@/domains/discord/adapters/widget"
import {aBlog, aCommittee, aContribution, aContributionPeriod, aJob, aMembership, anAddress, anEmail, anEsportsGame, anEvent, aSeason, aSignUp, aUser, type Wire} from "./records"

const stampedAt = {createdAt: "2025-01-01T00:00:00Z", updatedAt: "2025-01-01T00:00:00Z", version: 0}

/** Members on incasso as the first step of a run reads them: two to collect from, one with an accent, and two left out. */
const INCASSO_CANDIDATES: Wire<IncassoCandidate>[] = [
  {userId: 201, name: "Mila de Vries", ingName: "Mila de Vries", memberSince: "2024-09-01", feeType: "FULL_YEAR_FEE", amount: 25,
    ibanLastFour: "1234", mandateReference: "BLUESHELL-201-20240901", mandateSignedOn: "2024-09-01"},
  {userId: 202, name: "Zoë Bakker", ingName: "Zoe Bakker", memberSince: "2025-09-01", feeType: "FULL_YEAR_FEE", amount: 25,
    ibanLastFour: "4118", mandateReference: "BLUESHELL-202-20250904", mandateSignedOn: "2025-09-04"},
  {userId: 203, name: "Lotte Meijer", ingName: "Lotte Meijer", memberSince: "2025-09-01", feeType: "FULL_YEAR_FEE", amount: 25,
    leftOut: "NO_BANK_DETAILS"},
  {userId: 204, name: "Bram Kok", ingName: "Bram Kok", memberSince: "2020-09-01", feeType: "FULL_YEAR_FEE", amount: 25,
    ibanLastFour: "5560", mandateReference: "BLUESHELL-204-20200901", mandateSignedOn: "2020-09-01", leftOut: "ALREADY_PAID"},
]

export type {Wire}

type Fixtures = {
  users?: Wire<UserDetailResponse>[]
  deletedUsers?: Wire<UserDetailResponse>[]
  memberships?: Wire<MembershipResponse>[]
  contributionPeriods?: Wire<ContributionPeriodResponse>[]
  /** The period the membership page and the signup form quote, or null where none is recorded. */
  currentContributionPeriod?: Wire<ContributionPeriodResponse> | null
  /** What the signed-in reader pays to make a pending membership active; none by default. */
  firstContribution?: Wire<FirstContribution> | null
  /** The association's own numbers, or null where the endpoint refuses to say. */
  associationStatistics?: Wire<AssociationStatisticsResponse> | null
  contributions?: Wire<ContributionResponse>[]
  addresses?: Wire<AddressResponse>[]
  events?: Wire<EventResponse>[]
  eventSignUps?: Wire<EventSignUpResponse>[]
  eventDetailsById?: Record<string, Wire<EventResponse>>
  eventSignUpsByEventId?: Record<string, Wire<EventSignUpResponse>[]>
  committees?: Wire<CommitteeResponse>[]
  blogs?: Wire<BlogResponse>[]
  blogsById?: Record<string, Wire<BlogResponse>>
  blogStatusById?: Record<string, number>
  jobs?: Wire<JobExecution>[]
  alerts?: Wire<Alert>[]
  emails?: Wire<Email>[]
  cohorts?: Wire<CohortSummary>[]
  cohortMembers?: Wire<CohortDetail["members"]>
  esportsPages?: Record<string, Wire<GameRostersResponse>>
  esportsSeasons?: Wire<SeasonResponse>[]
  esportsTeams?: Wire<TeamResponse>[]
  esportsRoster?: Wire<RosterEntryResponse>[]
  esportsGames?: Wire<CasualGameResponse>[]
  casualGames?: Wire<CasualGameResponse>[]
  boards?: Wire<BoardResponse>[]
  cohortDetail?: Partial<CohortDetail>
  /** A refusal the payment-email send answers with instead of accepting the batch. */
  paymentEmailRefusal?: {status: number; errors: Wire<FieldValidationError>[]}
}

/** What Brevo reports it holds, for the target catalogue page. */
/**
 * The date the mocked bulk membership preview says it would act on. Fixed rather than
 * derived from the clock, because the point of the endpoint is that the browser does not
 * decide it — a spec asserting today's date would be asserting the wrong thing.
 */
export const BULK_MEMBERSHIP_EFFECTIVE_DATE = "2026-08-31"

const brevoTargets: Wire<ExternalTarget>[] = [
  {system: "BREVO", externalId: "7", kind: "LIST", label: "Members 2025-2026", folderLabel: "Contribution periods", path: ["Brevo", "Contribution periods"], memberCount: 2, linkedTargetId: 1},
  {system: "BREVO", externalId: "33", kind: "LIST", label: "Web Cmte", folderLabel: "Committees", path: ["Brevo", "Committees"], memberCount: 1, linkedTargetId: 2},
  {system: "BREVO", externalId: "34", kind: "LIST", label: "Board", folderLabel: "Committees", path: ["Brevo", "Committees"], memberCount: 5, linkedTargetId: null},
  // Same name as the committee list above, filed somewhere else: only the path tells them apart.
  {system: "BREVO", externalId: "88", kind: "LIST", label: "Web Cmte", folderLabel: "Archive", path: ["Brevo", "Archive"], memberCount: 0, linkedTargetId: null},
  {system: "BREVO", externalId: "50", kind: "LIST", label: "Loose ends", folderLabel: null, path: ["Brevo"], memberCount: null, linkedTargetId: null},
]

/**
 * The games themselves, as their records hold them: what each is called, the address its page
 * answers to, and the art it is drawn with. The pages read every one of these from here.
 */
const addressTaken = (gameName: string, address: string) => ({
  detail: "That address is already used by another game.",
  code: "AddressTaken",
  gameName,
  address,
})

const esportsGames = [
  anEsportsGame({code: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", banner: null, icon: null, intro: "Shooters, and plenty of them.", sortIndex: 1, inCompetition: true}),
  anEsportsGame({code: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2", accent: "#e8842a", banner: null, icon: null, intro: "Those sweet headshots.", sortIndex: 2, inCompetition: true}),
  anEsportsGame({code: "LEAGUE_OF_LEGENDS", name: "League of Legends", slug: "league-of-legends", accent: "#c8963c", banner: null, icon: null, intro: "A special place.", sortIndex: 3, inCompetition: true}),
  anEsportsGame({code: "ROCKET_LEAGUE", name: "Rocket League", slug: "rocketleague", accent: "#1183d6", banner: null, icon: null, intro: "Football, with rocket cars.", sortIndex: 4, inCompetition: true}),
  anEsportsGame({code: "GEOGUESSR", name: "GeoGuessr", slug: "geoguessr", accent: "#6cbf3f", banner: null, icon: null, intro: "Guessing where.", sortIndex: 5, inCompetition: true}),
  // No accent has ever been written for Trackmania: it reads on the island's own blue.
  anEsportsGame({code: "TRACKMANIA", name: "Trackmania", slug: "trackmania", accent: null, banner: null, icon: null, intro: "Driving, fast.", sortIndex: 6, inCompetition: true}),
  anEsportsGame({code: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: "#e8842a", banner: null, icon: null, intro: null, sortIndex: 7, inCompetition: false}),
]

/**
 * The games as the casual pages read them: every one, the archived ones included, each saying
 * whether a team is fielded in it this season. No art, so a page is seen drawing its plates.
 */
const casualGame = (
  code: string, name: string, slug: string, sortIndex: number, extra: Partial<Wire<CasualGameResponse>> = {},
): Wire<CasualGameResponse> => ({
  code, name, slug, accent: null, intro: null, banner: null, icon: null, sortIndex, archived: false, inCompetition: false, channels: [],
  competitionIntro: null, esportsChannels: [], ...extra,
})

/** What the game edit page sends. */
type CasualGameBody = Wire<CasualGameRequest>

const casualGames = [
  casualGame("VALORANT", "Valorant", "valorant", 1, {accent: "#ff4655", intro: "Five-stacks, customs and clips.", inCompetition: true, channels: [{id: "6322", guildId: "324", name: "valorant"}]}),
  casualGame("MINECRAFT", "Minecraft", "minecraft", 2, {accent: "#6cbf3f", intro: "The association server."}),
  casualGame("POKEMON", "Pokémon", "pokemon", 3, {accent: "#ffcb05"}),
  casualGame("CHESS", "Chess", "chess", 4, {accent: "#b58863"}),
  casualGame("WORDLE", "Wordle", "wordle", 5),
  casualGame("DOTA_2", "Dota 2", "dota-2", 6, {archived: true}),
  casualGame("OVERWATCH", "Overwatch", "overwatch", 7, {archived: true}),
]

/** Two seasons of one game, so a page has both a roster and something to switch to. */
const esportsSeasons = [
  aSeason({id: 20, name: "Autumn 2025", startDate: "2025-09-01", endDate: "2026-01-31"}),
  aSeason({id: 19, name: "Spring 2025", startDate: "2025-02-01", endDate: "2025-08-31"}),
]

const esportsPageBySeason: Record<string, Wire<GameRostersResponse>> = {
  "20": {
    game: "VALORANT",
    season: esportsSeasons[0],
    seasons: esportsSeasons,
    teams: [
      {
        id: 1,
        name: "BS Waterboarders",
        banner: null,
        members: [
          {role: "PLAYER", handle: "AriosFury", name: "Viktor Petrov", roleTitle: "Captain", description: "Holds the **middle** together."},
          {role: "PLAYER", handle: "Loafine"},
          {role: "SUBSTITUTE", handle: "Blackout"},
        ],
      },
      {
        id: 2,
        name: "BS SpicyWater",
        banner: null,
        members: [{role: "PLAYER", handle: "Sony"}],
      },
    ],
  },
  "19": {
    game: "VALORANT",
    season: esportsSeasons[1],
    seasons: esportsSeasons,
    teams: [
      {
        id: 3,
        name: "BS Tempra",
        banner: null,
        members: [{role: "PLAYER", handle: "fetabass"}],
      },
    ],
  },
}

/** Two boards, so the page has one in office and one to expand. */
const boardFixtures: Wire<BoardResponse>[] = [
  {
    id: 9,
    number: 9,
    name: "9th Board",
    candidate: "9th Board",
    cheer: "RNG, Be With Me!",
    accent: null,
    description: null,
    startDate: "2025-09-01",
    endDate: "2026-08-31",
    // The photograph as the api answers with one: the master, and the widths it is stored at
    // for the band to compose a `srcset` out of.
    photo: {
      path: "board-photos/board9.webp",
      url: "/files/public/board-photos/board9.webp",
      width: 2560,
      height: 1440,
      renditions: [320, 640, 960, 1280, 1920].map((width) => ({
        url: `/files/public/board-photos/board9-${width}.webp`,
        width,
      })),
    },
    version: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    members: [
      {
        id: 91, boardId: 9, userId: 1, role: "Chair", name: "Emma Dokter", nickname: null,
        description: "Chairing the ninth board.",
        portrait: {
          path: "board-portraits/emma.webp",
          url: "/files/public/board-portraits/emma.webp",
          width: 640,
          height: 960,
          renditions: [160, 320, 640].map((width) => ({
            url: `/files/public/board-portraits/emma-${width}.webp`,
            width,
          })),
        },
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
      {
        id: 92, boardId: 9, userId: null, role: "Secretary", name: "Viktor Petrov", nickname: null,
        description: null, portrait: null,
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
    ],
  },
  {
    id: 1,
    number: 1,
    name: "1st Board",
    candidate: "1st Board",
    cheer: null,
    accent: null,
    description: null,
    startDate: "2017-09-01",
    endDate: "2018-08-31",
    // One of the four early boards: no photograph was ever taken, or nobody has it.
    photo: null,
    version: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    members: [
      {
        id: 11, boardId: 1, userId: null, role: "Chairman", name: "Thijs Lieverse", nickname: null,
        description: null, portrait: null,
        startDate: "2017-09-01", endDate: "2018-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
    ],
  },
]

/** An image as the api describes one: where it is served, how large it is, and its widths. */
/** Where a kind of picture is stored, as the api's own directories name them. */
const DIRECTORY_OF: Record<string, string> = {
  TEAM_BANNER: "team-banners",
  TEAM_ICON: "team-icons",
  ROSTER_ICON: "roster-icons",
  GAME_ICON: "game-icons",
  GAME_BANNER: "game-banners",
  BOARD_PHOTO: "board-photos",
  BOARD_PORTRAIT: "board-portraits",
}

/**
 * How wide a picture of each kind is, and the widths it is stored at.
 *
 * The ladders are the ones `FileType` lists, and a banner is not an icon: a test that asserts
 * an icon carries a banner's widths passes against a mock that hands every kind the same ones
 * and fails against the api. Nothing is upscaled, so the ladder stops at the picture's own
 * width the way the encoder does.
 */
const LADDER_OF: Record<string, {width: number; height: number; widths: number[]}> = {
  TEAM_BANNER: {width: 1280, height: 720, widths: [320, 640, 960, 1280]},
  GAME_BANNER: {width: 1280, height: 720, widths: [320, 640, 960, 1280]},
  TEAM_ICON: {width: 256, height: 256, widths: [128, 256]},
  GAME_ICON: {width: 256, height: 256, widths: [128, 256]},
  ROSTER_ICON: {width: 256, height: 256, widths: [128, 256]},
  // A board's group photograph is a game banner's twin, at the ceiling FileType gives it.
  BOARD_PHOTO: {width: 2560, height: 1440, widths: [320, 640, 960, 1280, 1920, 2560]},
  // A portrait is taller than it is wide, and its ladder tops out well below an upload.
  BOARD_PORTRAIT: {width: 640, height: 960, widths: [160, 320, 640]},
}

type MockImage = {
  url: string
  /** Where it is stored, which is what a save points at to put it on a record. */
  path: string
  /** Absent for a vector, whose size the api does not read and whose page does not need it. */
  width: number | null
  height: number | null
  renditions: Array<{url: string; width: number}>
}

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(data),
  })
}

/** The body an SDK operation answers with, as it crosses the wire. */
type Answered<K extends keyof typeof sdk> =
  Wire<NonNullable<Extract<Awaited<ReturnType<(typeof sdk)[K]>>, {error: undefined}>["data"]>>

/** Answers as [operation] does, so what the stand-in sends is checked against the api's own type. */
async function answer<K extends keyof typeof sdk>(route: Route, _operation: K, data: Answered<K>, status = 200) {
  await fulfillJson(route, data, status)
}

const NO_TWO_FACTOR_ASKED = {on: true, backupCodesLeft: 10, required: false, offered: false, mayTurnOff: false}

async function loginAsRoles(context: BrowserContext, roles: string[], twoFactor = NO_TWO_FACTOR_ASKED) {
  const loginCookie = encodeURIComponent(JSON.stringify({
    userId: 1,
    username: "mock-user",
    roles,
    addressId: 10,
    twoFactor,
  }))

  await context.addCookies([
    {
      name: "login",
      value: loginCookie,
      url: "http://127.0.0.1:4173",
    },
  ])
}

/** A board member whose role waits for two-factor, as the api answers somebody without it. */
export async function loginAsDormantBoard(context: BrowserContext) {
  await loginAsRoles(context, ["MEMBER"], {on: false, backupCodesLeft: 0, required: true, offered: false, mayTurnOff: false})
}

export async function loginAsBoard(context: BrowserContext) {
  await loginAsRoles(context, ["BOARD", "MEMBER"])
}

export async function loginAsMember(context: BrowserContext) {
  await loginAsRoles(context, ["MEMBER"])
}

export async function loginAsAdmin(context: BrowserContext) {
  await loginAsRoles(context, ["ADMIN", "MEMBER"])
}

/** Reads as a visitor who chose light. Call after installApiMocks, so this one wins. */
export async function preferLightTheme(page: Page) {
  await page.addInitScript(() => localStorage.setItem("esa-blueshell.nl:darkMode", "false"))
}

/** Api calls the stand-in had no answer for, per page; the `test` fixture fails on any. */
const unmocked = new WeakMap<Page, string[]>()
/** Api requests sent but not yet answered or handed to the stand-in. */
const unsettled = new WeakMap<Page, Set<Request>>()

const isApiRequest = (request: Request) => /^http:\/\/(localhost|127\.0\.0\.1):417[34]\/api\//.test(request.url())

/**
 * The unmocked calls, once every api request in flight has reached the stand-in.
 *
 * A test may end on a request it only waited to see sent, before its route reaches a handler.
 * A request a spec's own route holds open never settles, so the wait gives up after two seconds.
 */
export async function unmockedCalls(page: Page): Promise<string[]> {
  const deadline = Date.now() + 2_000
  while ((unsettled.get(page)?.size ?? 0) > 0 && Date.now() < deadline) {
    await new Promise(resolve => setTimeout(resolve, 20))
  }
  return unmocked.get(page) ?? []
}

export async function installApiMocks(page: Page, fixtures: Fixtures = {}) {
  unmocked.set(page, [])
  const inFlight = new Set<Request>()
  const reached = new WeakSet<Request>()
  unsettled.set(page, inFlight)
  page.on("request", request => {
    if (isApiRequest(request) && !reached.has(request)) inFlight.add(request)
  })
  page.on("requestfinished", request => inFlight.delete(request))
  page.on("requestfailed", request => inFlight.delete(request))
  // Seasons written down during the test. The api shows a season that was asked for even
  // where the game fielded nobody in it, and these are exactly those seasons.
  const written = new Map<number, Wire<SeasonResponse>>()
  // Teams written down and fielded during the test, so a page asked again reports them the
  // way the api would rather than forgetting they were added.
  const teamsMade: Wire<TeamResponse>[] = []
  const casualEdited = new Map<string, Wire<CasualGameResponse>>()
  const casualGone = new Set<string>()
  /**
   * Every game as the casual pages read it. A game is one record, so one the competition pages
   * know and the casual list does not name is answered here too, as the api would.
   */
  const casualNow = () => {
    // A spec's own competition games are the records it is about, so they win over the defaults.
    const listed: Wire<CasualGameResponse>[] = (fixtures.casualGames ?? casualGames)
      .map(one => fixtures.esportsGames?.find(held => held.code === one.code) ?? one)
    const competition = (fixtures.esportsGames ?? esportsGames)
      .filter(one => !listed.some(held => held.code === one.code))
    const known = [...listed, ...competition].map(one => casualEdited.get(String(one.code)) ?? one)
    const added = [...casualEdited.values()].filter(one => !known.some(k => k.code === one.code))
    return [...known, ...added].filter(one => !casualGone.has(String(one.code)) && !gamesGone.has(String(one.code)))
  }
  /** How many teams a game has fielded, as the api counts them before a removal. */
  const teamsHeldBy = (code: string) => {
    const held = new Set(fieldedNow.filter(one => one.game === code).map(one => one.teamId))
    return held.size + (code === "VALORANT" && !fixtures.esportsTeams ? 2 : 0)
  }
  /** Games corrected during the test, which every read then reports as corrected. */
  /** Games removed during the test, which the reads then leave out. */
  const gamesGone = new Set<string>()
  /** Games entered in a season during this test, staffed or not. */
  const gamesEntered: Array<{seasonId: number; game: string}> = []
  const fieldedNow: Array<{
    seasonId: number
    teamId: number
    game: string
    members: Wire<RosterMemberResponse>[]
  }> = []
  // The signup as the api would still be holding it, so a reloaded tab can read it
  // back on its token the way the real GET /signup/session answers.
  let signupInProgress: Wire<SignupResumeResponse> | null = null
  let nextTeamId = 70
  let nextEntryId = 200
  /** Seasons and fieldings taken away during the test, which the reads then leave out. */
  const gone = new Set<number>()
  const dropped: Array<{seasonId: number; teamId: number}> = []
  /** Teams renamed or removed during the test, which the reads then reflect. */
  const renamed = new Map<number, {name: string; banner: Wire<Image> | null; icon: Wire<Image> | null}>()
  const goneTeams = new Set<number>()
  /**
   * The uploaded images, held as the api holds them: a reference per owner rather than bytes.
   * Each upload takes the next file id, so a replacement is visibly a different url.
   *
   * Storing is separate from applying, exactly as the api has it: a picture goes into `stored`
   * when it is uploaded and reaches a poster or an icon only when a save names its path.
   */
  const teamBanners = new Map<number, MockImage>()
  const teamIcons = new Map<number, MockImage>()
  const icons = new Map<number, MockImage>()
  const stored = new Map<string, MockImage>()
  let nextFileId = 500
  /** Members written down during a test, each taking the next id the way the api would. */
  let nextMemberId = 900
  /**
   * An image as the api describes one. The size is that of the picture actually served below,
   * so a page reserving an image's space reserves the right amount of it.
   */
  const nextImage = (kind: string, vector = false): MockImage => {
    nextFileId += 1
    const directory = DIRECTORY_OF[kind] ?? "team-banners"
    const ladder = LADDER_OF[kind] ?? LADDER_OF.TEAM_BANNER!
    // A vector is stored as it arrived and carries no ladder: the browser scales it, so the
    // api answers one url and no widths at all.
    if (vector) {
      const svg = `${directory}/mock-${nextFileId}.svg`
      return {url: `/files/public/${svg}`, path: svg, width: null, height: null, renditions: []}
    }
    const at = `${directory}/mock-${nextFileId}.webp`
    return {
      url: `/files/public/${at}`,
      path: at,
      width: ladder.width,
      height: ladder.height,
      // The widths a picture of this kind is stored at, so a page has a srcset to compose.
      renditions: ladder.widths.map(width => ({
        url: `/files/public/${directory}/mock-${nextFileId}-${width}.webp`,
        width,
      })),
    }
  }

  /** The one endpoint that stores a picture. What it ends up on is a later save's business. */
  const storePicture = (kind: string, vector = false): MockImage => {
    const made = nextImage(kind, vector)
    stored.set(made.path, made)
    return made
  }

  /** The picture a save names, or nothing where the save names none. */
  const pictureNamed = (picture: unknown): MockImage | null =>
    (typeof picture === "string" ? stored.get(picture) ?? null : null)
  /**
   * Boards written down, corrected and removed during a test, so a page that reads again is
   * answered the way the api would rather than told the history never changed.
   */
  const boardsMade: Wire<BoardResponse>[] = []
  const boardsEdited = new Map<number, Wire<BoardResponse>>()
  const boardsGone = new Set<number>()
  let nextBoardId = 700
  /** Every board as it now stands: the fixtures, corrected, plus whatever was added. */
  const boardsNow = (): Wire<BoardResponse>[] =>
    [...(fixtures.boards ?? boardFixtures), ...boardsMade]
      .filter(one => !boardsGone.has(Number(one.id)))
      .map(one => ({...one, ...(boardsEdited.get(Number(one.id)) ?? {})}))
  /**
   * A board as the api answers with one after a write.
   *
   * A write replaces every field the way the api's own does, so a field the save left out is
   * cleared rather than kept — the case a merge would quietly get wrong. The save named where
   * its photograph is stored and the answer carries the picture itself, so the path is
   * resolved here exactly as the api resolves it; naming none clears the photograph.
   */
  const boardWritten = (
    base: Pick<Wire<BoardResponse>, "id" | "members" | "version" | "createdAt">,
    body: Wire<BoardRequest>,
  ): Wire<BoardResponse> => ({
    ...base,
    number: body.number,
    startDate: body.startDate,
    name: body.name ?? null,
    // `NOT NULL` and nothing reads it: filled from the name, or from the number where a board
    // has none, for a write that carries no candidate of its own.
    candidate: body.candidate ?? body.name ?? `Board ${body.number}`,
    cheer: body.cheer ?? null,
    accent: body.accent ?? null,
    description: body.description ?? null,
    endDate: body.endDate ?? null,
    photo: pictureNamed(body.photo),
    updatedAt: "2026-01-02T00:00:00Z",
  })
  /**
   * The line-up of the seeded team, as the admin reads and writes it. The public page builds
   * that team's members from it, so an edit here is visible there — which is the whole of
   * what "the slice shows the change" means.
   */
  const roster: Wire<RosterEntryResponse>[] = [
    {id: 21, teamId: 1, seasonId: 20, role: "PLAYER", handle: "AriosFury", displayName: "Viktor Petrov", userId: 1, sortIndex: 0, roleTitle: "Captain", description: "Holds the **middle** together."},
    {id: 22, teamId: 1, seasonId: 20, role: "PLAYER", handle: "Loafine", displayName: null, userId: null, sortIndex: 1, roleTitle: null, description: null},
    {id: 23, teamId: 1, seasonId: 20, role: "SUBSTITUTE", handle: "Blackout", displayName: null, userId: null, sortIndex: 2, roleTitle: null, description: null},
    // The team that played a season ago and has a line-up worth carrying across.
    {id: 11, teamId: 3, seasonId: 19, role: "PLAYER", handle: "AriosFury", displayName: "Viktor Petrov", userId: 1, sortIndex: 0, roleTitle: null, description: null},
    {id: 12, teamId: 3, seasonId: 19, role: "SUBSTITUTE", handle: "Blackout", displayName: null, userId: null, sortIndex: 1, roleTitle: null, description: null},
  ]
  const asMember = (entry: Wire<RosterEntryResponse>): Wire<RosterMemberResponse> => ({
    role: entry.role,
    handle: entry.handle,
    name: entry.userId != null ? entry.displayName : null,
    roleTitle: entry.roleTitle ?? null,
    description: entry.description ?? null,
    icon: icons.get(Number(entry.id)) ?? null,
  })

  await page.addInitScript((params: {cookieConsentStorageKey: string; cookieConsentPayload: string}) => {
    localStorage.setItem(params.cookieConsentStorageKey, params.cookieConsentPayload)
    // Dark, which is the esports pages' own treatment. Only when unset, so a spec that
    // wants light seeds it first — or calls preferLightTheme after this.
    if (localStorage.getItem("esa-blueshell.nl:darkMode") == null) {
      localStorage.setItem("esa-blueshell.nl:darkMode", "true")
    }
  }, {
    cookieConsentStorageKey: COOKIE_CONSENT_STORAGE_KEY,
    cookieConsentPayload: encodeCookieConsentPayload(),
  })

  // TWIN: `GrantedRoles.ASSIGNABLE` in the api. Change one, change the other.
  const ASSIGNABLE_ROLES: Wire<Role>[] = ["BOARD", "TREASURER", "ADMIN"]

  const baseUsers: Wire<UserDetailResponse>[] = fixtures.users ?? [
    aUser({id: 1, fullName: "Emma Dokter", username: "lyndisluna", email: "emma@example.com", roles: ["MEMBER"]}),
    aUser({id: 2, fullName: "Viktor Petrov", username: "ariosfury", email: "viktor@example.com", enabled: false, roles: ["GUEST"]}),
  ]

  const baseDeletedUsers: Wire<UserDetailResponse>[] = fixtures.deletedUsers ?? [
    aUser({id: 9, fullName: "Deleted User", username: "deleted-user", email: "deleted@example.com", enabled: false, roles: ["GUEST"]}),
  ]

  const baseMemberships: Wire<MembershipResponse>[] = fixtures.memberships ?? [
    aMembership({id: 100, userId: 1, memberType: "REGULAR", startDate: "2025-01-01"}),
  ]

  const basePeriods: Wire<ContributionPeriodResponse>[] = fixtures.contributionPeriods ?? [
    aContributionPeriod({id: 200, startDate: "2025-01-01", endDate: "2025-06-30", halfYearCutoffDate: "2025-04-01", halfYearFee: 10, fullYearFee: 20, alumniFee: 5, contactListId: 7, version: 3}),
    aContributionPeriod({id: 201, startDate: "2025-07-01", endDate: "2025-12-31", halfYearCutoffDate: "2025-10-01", halfYearFee: 10, fullYearFee: 20, alumniFee: 5, contactListId: 8, version: 4}),
  ]

  // What the association says about itself in numbers, which the membership page upgrades to.
  const baseStatistics: Wire<AssociationStatisticsResponse> | null | undefined = "associationStatistics" in fixtures ? fixtures.associationStatistics : {
    boards: 9, committees: 15, eventsLastYear: 63,
    gamesPlayed: 5, seasonsPlayed: 12, teamsThisSeason: 13,
  }

  // The period the fees are quoted from. Its own amounts, so a spec asserting a price is not
  // asserting one of the periods the management pages edit.
  const baseCurrentPeriod: Wire<ContributionPeriodResponse> | null | undefined = "currentContributionPeriod" in fixtures ? fixtures.currentContributionPeriod : aContributionPeriod({
    id: 210, startDate: "2025-09-01", endDate: "2026-08-31", halfYearCutoffDate: "2026-02-01",
    halfYearFee: 15, fullYearFee: 25, alumniFee: 12.5, contactListId: 9, version: 1,
  })

  const baseContributions: Wire<ContributionResponse>[] = fixtures.contributions ?? [
    aContribution({userId: 1, contributionPeriodId: 201}),
  ]

  const baseAddresses: Wire<AddressResponse>[] = fixtures.addresses ?? [
    anAddress({id: 400, userId: 1, street: "Main", city: "Enschede", zipCode: "1234AB", country: "NL"}),
  ]

  const baseEvents: Wire<EventResponse>[] = fixtures.events ?? [
    anEvent({
      id: 500,
      title: "Mock Event",
      description: "Mock event description",
      location: "Discord",
      startTime: "2099-01-01T12:00:00.000Z",
      endTime: "2099-01-01T14:00:00.000Z",
      approved: true,
      signUp: true,
      signUpCount: 1,
      membersOnly: false,
      committeeId: 900,
      banner: null,
      gameCodes: ["VALORANT"],
    }),
  ]

  const baseEventSignUps: Wire<EventSignUpResponse>[] = fixtures.eventSignUps ?? [
    aSignUp({id: 600, eventId: 500, kind: "MEMBER", user: {id: 1, fullName: "Emma Dokter", email: "emma@example.com", ...stampedAt}}),
  ]

  const committeeRecord = (id: number, name: string, slug: string, extra: Partial<Wire<CommitteeResponse>> = {}) =>
    aCommittee({id, name, slug, description: `${name} runs things.`, banner: null, members: [], ...extra})
  const baseCommittees: Wire<CommitteeResponse>[] = fixtures.committees ?? [
    committeeRecord(900, "Events Committee", "events-committee", {description: "Runs the events.", gameCodes: ["CHESS"], members: [{userId: 1, committeeId: 900, role: "Chair", ...stampedAt}]}),
    committeeRecord(901, "LanCie", "lancie", {gameCodes: ["VALORANT"]}),
    committeeRecord(902, "Board", "board"),
    committeeRecord(903, "OldCie", "oldcie", {archived: true}),
  ]
  // The committees, kept per page so a spec sees its own adds, edits and archives.
  const committeesEdited = new Map<number, Wire<CommitteeResponse>>()
  const committeesNow = () => {
    const known = baseCommittees.map(one => committeesEdited.get(Number(one.id)) ?? one)
    const added = [...committeesEdited.values()].filter(one => !known.some(k => k.id === one.id))
    return [...known, ...added]
  }
  /** A picture a save names by path: one stored during the test, or the one already held. */
  const pictureKept = (named: string | null | undefined, held: Wire<Image> | null | undefined): Wire<Image> | null =>
    named == null ? null : pictureNamed(named) ?? (held?.path === named ? held : null)
  /** The committee a save leaves, answered the way the api answers it rather than as the request. */
  const committeeSaved = (
    held: Wire<CommitteeResponse>,
    body: Partial<Omit<Wire<UpdateCommitteeRequest>, "version">>,
    version: number,
  ): Wire<CommitteeResponse> => ({
    ...held,
    name: body.name ?? held.name,
    slug: body.slug || held.slug,
    description: body.description ?? held.description,
    gameCodes: body.gameCodes ?? held.gameCodes,
    banner: body.banner === undefined ? held.banner : pictureKept(body.banner, held.banner),
    icon: body.icon === undefined ? held.icon : pictureKept(body.icon, held.icon),
    members: body.members
      ? body.members.map(one => ({...stampedAt, committeeId: held.id, userId: one.userId, role: one.role ?? null}))
      : held.members,
    version,
  })

  const baseBlogs: Wire<BlogResponse>[] = fixtures.blogs ?? [
    aBlog({
      id: 1,
      title: "Mock Newsletter",
      publishedAt: "2025-01-01T12:00:00.000Z",
      html: "<h1>Mock Newsletter</h1><p>Welcome to Blueshell.</p>",
    }),
  ]

  const blogsById = fixtures.blogsById ?? Object.fromEntries(
    baseBlogs
      .filter((blog) => blog.id != null)
      .map((blog) => [String(blog.id), blog]),
  )

  let exceptionResolvedAt: string | null = null
  const paidPeriods = new Set<number>()
  const incassoRuns: Wire<IncassoRunView>[] = []
  let ownMandate: Wire<OwnMandateResponse> = {standing: "NONE", pending: false}
  const baseAlerts: Wire<Alert>[] = fixtures.alerts ?? []

  const baseJobs: Wire<JobExecution>[] = fixtures.jobs ?? [
    aJob({
      id: 700,
      jobType: "SYNC_DISCORD",
      status: "FAILED",
      attempts: 1,
      payload: {scope: "members"},
      errorType: "RuntimeException",
      errorReason: "Temporary failure",
      queuedAt: "2025-01-01T12:00:00.000Z",
      startedAt: "2025-01-01T12:00:10.000Z",
      finishedAt: "2025-01-01T12:00:11.000Z",
    }),
  ]

  const baseEmails: Wire<Email>[] = fixtures.emails ?? [
    anEmail({
      id: 800,
      recipientEmail: "alice@example.com",
      recipientName: "Alice Example",
      subject: "Welcome to Blueshell",
      emailType: "email.activation",
      deliveryStatus: "DELIVERED",
      messageId: "<msg-800@blueshell.utwente.nl>",
      sentAt: "2025-01-01T12:00:00.000Z",
      deliveredAt: "2025-01-01T12:00:30.000Z",
      openedAt: null,
      attempts: 1,
      jobExecutionId: 700,
      createdAt: "2025-01-01T11:59:00.000Z",
      previewable: true,
    }),
  ]

  const toSearchableString = (value: unknown): string => {
    if (typeof value === "string") return value
    if (typeof value === "number") return String(value)
    return ""
  }

  const jobCategory = (job: Wire<JobExecution>): string => {
    const raw = toSearchableString(job.category).trim().toLowerCase()
    if (raw) return raw

    const type = toSearchableString(job.jobType).trim().toLowerCase()
    if (!type) return "other"
    const separatorPositions = [type.indexOf("."), type.indexOf("_"), type.indexOf("-")].filter((idx) => idx > 0)
    if (separatorPositions.length === 0) return type
    return type.slice(0, Math.min(...separatorPositions))
  }

  const matchesSearch = (job: Wire<JobExecution>, query: string): boolean => {
    const relatedEntities = job.relatedEntities.map(one => one.label).join(" ")

    const haystack = [
      toSearchableString(job.jobType),
      toSearchableString(job.errorType),
      toSearchableString(job.errorMessage),
      toSearchableString(job.errorReason),
      toSearchableString(job.initiatedByDisplay),
      relatedEntities,
    ]
      .join(" ")
      .toLowerCase()

    return haystack.includes(query)
  }

  const parseUserId = (path: string, pattern: RegExp): number | null => {
    const match = path.match(pattern)
    if (match == null) return null
    const id = Number(match[1])
    return Number.isFinite(id) ? id : null
  }

  const parseCookieLogin = (cookieHeader: string): {userId: number; roles: Wire<Role>[]; twoFactor?: Wire<TwoFactorStanding>} | null => {
    try {
      const match = cookieHeader.match(/(?:^|;\s*)login=([^;]+)/)
      if (!match) return null
      const data = JSON.parse(decodeURIComponent(match[1]))
      const userId = Number(data?.userId)
      const roles = Array.isArray(data?.roles) ? (data.roles as Wire<Role>[]) : null
      return Number.isFinite(userId) && roles ? {userId, roles, twoFactor: data?.twoFactor} : null
    } catch {
      return null
    }
  }

  const handleApiRoute = async (route: Route) => {
    const request = route.request()
    reached.add(request)
    inFlight.delete(request)
    const url = new URL(request.url())
    /**
     * What one game fielded in one season.
     *
     * Shared by a game's own page and by a season's band, because the two answering
     * differently about the same game in the same season is the bug this would otherwise hide.
     */
    const teamsOfGameInSeason = (game: string, seasonId: number): Wire<TeamRosterResponse>[] => {
      const page = fixtures.esportsPages?.[String(seasonId)] ?? esportsPageBySeason[String(seasonId)]
      // Teams fielded during this test belong to a season the same way the seeded ones do.
      // Which game a team played is the fielding's to say, not the team's.
      const extra = fieldedNow
        .filter(one => one.seasonId === seasonId && one.game === game)
        .map(one => ({one, team: teamsMade.find(made => made.id === one.teamId)
          ?? [{id: 3, name: "BS Old Guard"}].find(known => known.id === one.teamId)}))
        .filter(row => row.team != null)
        .map(row => ({
          id: row.one.teamId,
          name: row.team!.name,
          members: row.one.members,
        }))
      const fieldsThis = game === "VALORANT" || game === "CS2" || (game === "CSGO" && seasonId === 19)
      const stillFielded = (team: Wire<TeamRosterResponse>) =>
        !dropped.some(one => one.seasonId === seasonId && one.teamId === team.id)
        && !goneTeams.has(Number(team.id))
      const named = (team: Wire<TeamRosterResponse>): Wire<TeamRosterResponse> => {
        const change = renamed.get(Number(team.id))
        return change ? {...team, name: change.name, icon: change.icon} : team
      }
      const seeded = (fieldsThis && page ? page.teams : [])
        .filter(stillFielded).map(named).map(team => (
          team.id === 1
            ? {...team, members: roster
              .filter(one => one.teamId === 1 && one.seasonId === seasonId)
              .sort((a, b) => Number(a.sortIndex) - Number(b.sortIndex))
              .map(asMember)}
            : team
        ))
      // A team's own two pictures and nothing else: the slice draws both, and there is no
      // wider banner for either to be resolved against.
      return [...seeded, ...extra].map(team => ({
        ...team,
        banner: teamBanners.get(team.id) ?? null,
        icon: teamIcons.get(team.id) ?? null,
      }))
    }

    /** Whether the caller may edit, read from the login the browser is carrying. */
    const isBoard = (): boolean => {
      const cookie = request.headers().cookie ?? ""
      const found = /(?:^|;\s*)login=([^;]*)/.exec(cookie)
      if (!found?.[1]) return false
      try {
        const roles = (JSON.parse(decodeURIComponent(found[1])) as {roles?: string[]}).roles ?? []
        return roles.includes("BOARD") || roles.includes("ADMIN")
      } catch {
        return false
      }
    }

    const method = request.method()
    const path = url.pathname.startsWith("/api/")
      ? url.pathname.slice(4)
      : url.pathname

    const cookieLogin = parseCookieLogin(request.headers()["cookie"] ?? "")

    if (method === "GET" && path === "/users") {
      return answer(route, "findUsers", {content: baseUsers})
    }
    if (method === "GET" && path === "/users/me/two-factor") {
      return answer(route, "twoFactorStanding", cookieLogin?.twoFactor ?? NO_TWO_FACTOR_ASKED)
    }
    if (method === "GET" && path === "/users/me/sign-ins") {
      const now = new Date().toISOString()
      return answer(route, "signIns", [{id: "here", browser: "Chrome", platform: "Linux", signedInAt: now, lastSeenAt: now, current: true}])
    }
    if (method === "GET" && path === "/users/me/unlinked-targets") {
      return answer(route, "listMyUnlinkedTargets", [])
    }
    if (method === "GET" && path === "/users/me/trusted-browsers") {
      return answer(route, "trustedBrowsers", [])
    }
    if (method === "POST" && path === "/users/me/two-factor/setup") {
      return answer(route, "setUpTwoFactor", {otpauthUri: "otpauth://totp/ESA%20Blueshell:mock-user?secret=JBSWY3DPEHPK3PXP", key: "JBSWY3DPEHPK3PXP"})
    }
    if (method === "GET" && path === "/users/me/first-contribution") {
      return fixtures.firstContribution ? answer(route, "findOwnFirstContribution", fixtures.firstContribution) : route.fulfill({status: 204, body: ""})
    }
    if (path === "/users/me/mandate" || (method === "PUT" && path === "/signup/mandate")) {
      if (method === "GET") return answer(route, "findOwnMandate", ownMandate)
      const {iban} = request.postDataJSON() as {iban: string}
      const signup = path.startsWith("/signup")
      ownMandate = {
        standing: signup ? "NONE" : "MANDATE_RECORDED", ibanLastFour: iban.replace(/\s/g, "").slice(-4),
        reference: signup ? undefined : "BLUESHELL-1-20260930", signedOn: "2026-09-30", pending: signup,
      }
      return signup ? route.fulfill({status: 204, body: ""}) : answer(route, "setUpOwnMandate", ownMandate)
    }
    if (method === "GET" && path === "/users/me/email") {
      return answer(route, "emailAddress", {email: "mock-user@example.com", pendingEmail: null})
    }
    if (method === "GET" && /^\/users\/\d+\/account-security$/.test(path)) {
      return answer(route, "accountStanding", {twoFactorOn: false, awaitingReenrolment: false, locked: false})
    }
    if (method === "GET" && /^\/users\/\d+\/security-events$/.test(path)) {
      return answer(route, "securityEvents", {events: [], page: 0, totalPages: 0, totalElements: 0})
    }
    if (method === "GET" && path === "/users/me/security-events") {
      return answer(route, "mySecurityEvents", {events: [], page: 0, totalPages: 0, totalElements: 0})
    }
    if (method === "GET" && path === "/recovery/last-emails") {
      return answer(route, "lastRecoveryEmails", {emails: []})
    }
    if (method === "GET" && path === "/users/deleted") {
      return answer(route, "findDeletedUsers", {content: baseDeletedUsers})
    }
    if (method === "GET" && /^\/users\/\d+$/.test(path)) {
      const id = Number(path.split("/").at(-1))
      const user = baseUsers.find((candidate) => Number(candidate.id) === id)
      // Reflect the logged-in user's actual roles so App.vue doesn't overwrite the store with stale mock data
      const roles = (cookieLogin?.userId === id ? cookieLogin.roles : null) ?? user?.roles ?? ["MEMBER"]
      if (user != null) {
        return answer(route, "findUserById", {...user, roles})
      }
      return answer(route, "findUserById", aUser({id, roles}))
    }
    if (method === "GET" && /^\/users\/\d+\/roles$/.test(path)) {
      const id = parseUserId(path, /^\/users\/(\d+)\/roles$/) ?? 0
      const user = baseUsers.find((candidate) => Number(candidate.id) === id)
      const held: Wire<Role>[] = user?.roles ?? ["MEMBER"]
      return answer(route, "findUserRoles", {
        userId: id,
        roles: held,
        granted: held.filter((role) => ASSIGNABLE_ROLES.includes(role)),
        derived: held.filter((role) => role === "MEMBER").map((role) => ({role, source: "MEMBERSHIP" as const})),
        implied: [],
        dormant: [],
        assignable: ASSIGNABLE_ROLES,
      })
    }
    if (method === "PUT" && /^\/users\/\d+\/roles$/.test(path)) {
      const id = parseUserId(path, /^\/users\/(\d+)\/roles$/) ?? 0
      const granted = ((request.postDataJSON() as {roles?: Wire<Role>[]} | null)?.roles) ?? []
      const user = baseUsers.find((candidate) => Number(candidate.id) === id)
      const derived = (user?.roles ?? []).filter((role) => role === "MEMBER")
      if (user != null) user.roles = [...derived, ...granted]
      return answer(route, "setUserRoles", {
        userId: id,
        roles: [...derived, ...granted],
        granted,
        derived: derived.map((role) => ({role, source: "MEMBERSHIP" as const})),
        implied: [],
        dormant: [],
        assignable: ASSIGNABLE_ROLES,
      })
    }
    if (method === "GET" && /^\/users\/\d+\/role-changes$/.test(path)) {
      return answer(route, "findUserRoleChanges", [])
    }
    if (method === "DELETE" && /^\/users\/\d+$/.test(path)) {
      const id = parseUserId(path, /^\/users\/(\d+)$/)
      if (id != null) {
        const activeIndex = baseUsers.findIndex((candidate) => Number(candidate.id) === id)
        if (activeIndex >= 0) {
          const [deletedCandidate] = baseUsers.splice(activeIndex, 1)
          if (!baseDeletedUsers.some((candidate) => Number(candidate.id) === id)) {
            baseDeletedUsers.unshift({
              ...deletedCandidate,
              enabled: Boolean(deletedCandidate.enabled),
            })
          }
        }
      }
      return answer(route, "deleteUserById", {}, 204)
    }
    if (method === "PUT" && /^\/users\/\d+\/restore$/.test(path)) {
      const id = parseUserId(path, /^\/users\/(\d+)\/restore$/)
      if (id != null) {
        const deletedIndex = baseDeletedUsers.findIndex((candidate) => Number(candidate.id) === id)
        if (deletedIndex >= 0) {
          const [restoredCandidate] = baseDeletedUsers.splice(deletedIndex, 1)
          if (!baseUsers.some((candidate) => Number(candidate.id) === id)) {
            baseUsers.unshift({
              ...restoredCandidate,
              roles: Array.isArray(restoredCandidate.roles) ? restoredCandidate.roles : ["MEMBER"],
            })
          }
        }
      }
      return answer(route, "restoreDeletedUserById", {}, 204)
    }
    if (/^\/memberships\/\d+\/mandate$/.test(path)) {
      const membershipId = Number(path.split("/")[2])
      if (method === "PUT") {
        const {accountHolder, signedOn, iban} = request.postDataJSON() as {accountHolder: string; signedOn: string; iban: string}
        const compact = iban.replace(/\s/g, "")
        return answer(route, "recordMandate", {
          membershipId, standing: "MANDATE_RECORDED", accountHolder, ibanLastFour: compact.slice(-4),
          reference: `BLUESHELL-${membershipId}`, signedOn, recordedBy: 1, recordedAt: "2026-09-30T10:00:00.000Z",
        })
      }
      return answer(route, "findMandate", {membershipId, standing: "NONE"})
    }
    if (method === "GET" && path === "/memberships") {
      const userId = url.searchParams.get("userId")
      return answer(route, "findMemberships", userId ? baseMemberships.filter((one) => String(one.userId) === userId) : baseMemberships)
    }
    if (method === "GET" && /^\/users\/\d+\/contributions$/.test(path)) {
      return answer(route, "findMemberContributions", [{
        periodId: 1, startDate: "2025-09-01", endDate: "2026-08-31", feeType: "FULL_YEAR_FEE", fee: 30,
        paid: paidPeriods.has(1), paidAt: paidPeriods.has(1) ? "2025-10-01T10:00:00.000Z" : null,
        lastEmailAt: "2025-09-20T10:00:00.000Z", lastEmailKind: "REMINDER",
      }])
    }
    if (method === "POST" && path === "/contributions") {
      paidPeriods.add((request.postDataJSON() as {contributionPeriodId: number}).contributionPeriodId)
      return route.fulfill({status: 201, contentType: "application/json", body: "{}"})
    }
    if (method === "DELETE" && /^\/contributionPeriods\/\d+\/users\/\d+\/contributions$/.test(path)) {
      paidPeriods.delete(Number(path.split("/")[2]))
      return route.fulfill({status: 204})
    }
    // The bulk membership actions ask the api what they would do before doing it, so the
    // preview decides the rows here the way the server would: a member with an open
    // membership can be ended and cannot be started, and the reverse.
    if (method === "POST" && /^\/memberships\/bulk\/(end|start)(\/preview)?$/.test(path)) {
      const starting = path.startsWith("/memberships/bulk/start")
      const selected: number[] = (route.request().postDataJSON()?.userIds ?? []) as number[]
      const isActive = (userId: number) =>
        baseMemberships.some((m) => m.userId === userId && m.endDate == null)
      const included = selected.filter((userId) => (starting ? !isActive(userId) : isActive(userId)))

      if (path.endsWith("/preview")) {
        return answer(route, starting ? "previewBulkStart" : "previewBulkEnd", {
          effectiveDate: BULK_MEMBERSHIP_EFFECTIVE_DATE,
          rows: selected.map((userId) => {
            const include = included.includes(userId)
            if (include) return {userId, disposition: "INCLUDED" as const, reason: starting ? "WILL_START_NEW" as const : null}
            return {
              userId,
              disposition: "SKIPPED" as const,
              reason: starting ? "ALREADY_ACTIVE" as const : "NO_ACTIVE_MEMBERSHIP" as const,
            }
          }),
        })
      }
      return answer(route, starting ? "startMemberships" : "endMemberships", {
        applied: included.length,
        skipped: selected.length - included.length,
        queued: 0,
      })
    }
    // The api decides these rows, so the mock decides them the same way: hard exclusions
    // first, then the warnings the operator can tick back in.
    if (method === "POST" && path === "/contributions/bulk/email/preview") {
      const body = route.request().postDataJSON() as {contributionPeriodId: number; userIds: number[]}
      const paid = new Set(
        baseContributions
          .filter((c) => c.contributionPeriodId === body.contributionPeriodId)
          .map((c) => c.userId),
      )
      const rows = body.userIds.map((userId): Answered<"previewBulkContributionEmail">["rows"][number] => {
        const membership = baseMemberships.find((m) => m.userId === userId)
        const honorary = membership?.memberType === "HONORARY"
        const [disposition, reason] = honorary
          ? ["EXCLUDED", "HONORARY"] as const
          : paid.has(userId)
            ? ["WARNING", "ALREADY_PAID"] as const
            : ["INCLUDED", null] as const
        return {
          userId,
          name: baseUsers.find((u) => Number(u.id) === userId)?.fullName ?? `#${userId}`,
          memberType: membership?.memberType ?? "NONE",
          memberSince: membership?.startDate ?? null,
          disposition,
          reason,
          defaultKind: membership?.incasso ? "INCASSO_NOTIFICATION" : "REMINDER",
          feeType: honorary ? null : "FULL_YEAR_FEE",
          amount: honorary ? null : 20,
          lastRemindedOn: userId === 2 ? "2025-09-01" : null,
          lastNotifiedOn: null,
        }
      })
      return answer(route, "previewBulkContributionEmail", {contributionPeriodId: body.contributionPeriodId, rows, unknownUserIds: []})
    }
    if (method === "GET" && path === "/contributions/bulk/email/message") {
      const params = new URL(route.request().url()).searchParams
      const incasso = params.get("kind") === "INCASSO_NOTIFICATION"
      const feeTypes = ["FULL_YEAR_FEE", "HALF_YEAR_FEE", "ALUMNI_FEE"] as const
      return answer(route, "readContributionEmail", {
        kind: incasso ? "INCASSO_NOTIFICATION" : "REMINDER",
        feeType: feeTypes.find(one => one === params.get("feeType")) ?? "FULL_YEAR_FEE",
        subject: incasso
          ? "Your Blueshell contribution will be collected automatically (2025)"
          : "Please pay your Blueshell contribution (2025)",
        html: "<p>Amount due: &euro;20,00</p>",
        recipientEmail: "member@example.com",
        recipientName: "A Member",
      })
    }
    if (method === "POST" && path === "/contributions/bulk/email/send") {
      const refusal = fixtures.paymentEmailRefusal
      if (refusal) {
        return fulfillJson(
          route,
          {status: refusal.status, detail: "The send was refused.", errors: refusal.errors},
          refusal.status,
        )
      }
      const body = route.request().postDataJSON() as {userIds: number[]}
      return answer(route, "sendPaymentEmails", {
        remindersSent: body.userIds.length,
        incassoNotificationsSent: 0,
        notWrittenTo: 0,
      })
    }
    if (method === "GET" && path === "/contributionPeriods/current/standing") {
      return answer(route, "findCurrentPeriodStanding", {
        periodId: 1, startDate: "2025-09-01", endDate: "2026-08-31", members: 211, paid: 180, stillToPay: 31, pendingFirstContribution: 9,
      })
    }
    if (method === "GET" && path === "/contributionPeriods/current") {
      // A fixture set to null is a year nobody has recorded a fee for yet, which the api
      // answers with no content rather than with a period.
      if (!baseCurrentPeriod) return route.fulfill({status: 204, contentType: "application/json", body: ""})
      return answer(route, "findCurrentContributionPeriod", baseCurrentPeriod)
    }
    if (method === "GET" && path === "/statistics/association") {
      // Set to null by a spec that wants the read refused, so the page falls back on its floors.
      if (!baseStatistics) return fulfillJson(route, {title: "Server error", status: 500}, 500)
      return answer(route, "associationStatistics", baseStatistics)
    }
    if (method === "GET" && path === "/contributionPeriods") {
      return answer(route, "findContributionPeriods", basePeriods)
    }
    if (method === "POST" && path === "/contributionPeriods") {
      const body = route.request().postDataJSON() as Wire<CreateContributionPeriodRequest>
      const created = aContributionPeriod({...body, id: 202, version: 0})
      // Held, so the list the page reloads after a save shows what was just created.
      basePeriods.push(created)
      return answer(route, "createContributionPeriod", created, 201)
    }
    if (method === "PUT" && /^\/contributionPeriods\/\d+$/.test(path)) {
      const id = Number(path.split("/")[2])
      const body = route.request().postDataJSON() as Wire<UpdateContributionPeriodRequest>
      const period = basePeriods.find((p) => p.id === id)
      // A version the row does not hold is what optimistic locking refuses, so the mock
      // refuses it the way OptimisticLockingProblemDetailsAdvice does.
      if (!period || body.version !== period.version) {
        return fulfillJson(
          route,
          {
            type: "about:blank",
            title: "Conflict",
            status: 409,
            detail: "This resource was modified by someone else. Please refresh the page and try your changes again.",
            instance: path,
            reason: "Optimistic locking conflict",
          },
          409,
        )
      }
      Object.assign(period, body, {version: period.version + 1})
      return answer(route, "updateContributionPeriod", period)
    }
    if (method === "DELETE" && /^\/contributionPeriods\/\d+$/.test(path)) {
      const index = basePeriods.findIndex((one) => one.id === Number(path.split("/")[2]))
      if (index >= 0) basePeriods.splice(index, 1)
      return route.fulfill({status: 204})
    }
    if (method === "GET" && /^\/contributionPeriods\/\d+\/incasso$/.test(path)) {
      return answer(route, "planIncasso", INCASSO_CANDIDATES)
    }
    if (method === "POST" && /^\/contributionPeriods\/\d+\/incassoRuns$/.test(path)) {
      const body = request.postDataJSON() as {userIds: number[]; collectionDate: string; statementText: string}
      const collections = INCASSO_CANDIDATES.filter((one) => body.userIds.includes(one.userId)).map((one) => ({
        userId: one.userId, name: one.name, ingName: one.ingName, ibanLastFour: one.ibanLastFour, mandateReference: one.mandateReference,
        mandateSignedOn: one.mandateSignedOn, feeType: one.feeType ?? "FULL_YEAR_FEE", amount: one.amount ?? 0,
      }))
      const run = {
        id: 70 + incassoRuns.length, contributionPeriodId: Number(path.split("/")[2]), collectionDate: body.collectionDate,
        statementText: body.statementText, collections, total: collections.reduce((sum, one) => sum + one.amount, 0),
        createdAt: "2026-09-30T10:00:00.000Z", submittedAt: null as string | null, fileParts: 1,
      }
      incassoRuns.push(run)
      return answer(route, "startIncassoRun", run, 201)
    }
    if (method === "GET" && /^\/incassoRuns\/\d+\/file$/.test(path)) {
      return route.fulfill({
        status: 200,
        contentType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        headers: {"Content-Disposition": "attachment; filename=\"incassobatch.xlsx\""},
        body: "PK",
      })
    }
    if (method === "POST" && /^\/incassoRuns\/\d+\/submitted$/.test(path)) {
      const run = incassoRuns.find((one) => one.id === Number(path.split("/")[2]))
      if (!run) return route.fulfill({status: 404, body: ""})
      run.submittedAt = "2026-10-20T10:00:00.000Z"
      return answer(route, "markIncassoRunSubmitted", run)
    }
    if (method === "GET" && /^\/incassoRuns\/\d+$/.test(path)) {
      const run = incassoRuns.find((one) => one.id === Number(path.split("/")[2]))
      return run ? answer(route, "findIncassoRun", run) : route.fulfill({status: 404, body: ""})
    }
    if (method === "GET" && /^\/contributionPeriods\/\d+\/members$/.test(path)) {
      const periodId = Number(path.split("/")[2])
      const period = basePeriods.find((one) => one.id === periodId)
      const inPeriod = baseMemberships.filter((one) => period != null
        && one.startDate <= period.endDate && (one.endDate == null || one.endDate >= period.startDate))
      return answer(route, "findPeriodContributions", {
        periodId,
        incassoRuns: incassoRuns.filter((one) => one.contributionPeriodId === periodId).map((one) => ({
          id: one.id, collectionDate: one.collectionDate, collections: one.collections.length, total: one.total, submittedAt: one.submittedAt,
        })),
        members: inPeriod.map((held) => {
          const user = baseUsers.find((one) => one.id === held.userId)
          const honorary = held.memberType === "HONORARY"
          return {
            userId: held.userId,
            name: user?.fullName ?? `User ${held.userId}`,
            username: user?.username ?? `user${held.userId}`,
            feeType: honorary ? null : "FULL_YEAR_FEE" as const,
            fee: honorary ? null : period?.fullYearFee ?? 0,
            incasso: held.incasso,
            paid: baseContributions.some((one) => one.userId === held.userId && one.contributionPeriodId === periodId),
            paidAt: null,
            lastEmailAt: null,
            lastEmailKind: null,
          }
        }),
        runs: [],
      })
    }
    if (method === "GET" && /\/contributionPeriods\/\d+\/contributions$/.test(path)) {
      return answer(route, "findContributionsByPeriodId", baseContributions)
    }
    if (method === "GET" && path === "/addresses") {
      return answer(route, "findAllAddresses", baseAddresses)
    }
    // No bot in the mocked api: the Discord field stays the text field it always was.
    if (method === "GET" && path === "/discord/members") {
      return fulfillJson(route, {status: 503, title: "Service Unavailable"}, 503)
    }
    if (method === "GET" && path === "/discord/mentions") {
      return answer(route, "readDiscordMentions", {users: [], roles: [], channels: []})
    }
    if (method === "GET" && path === "/discord/channels") {
      return answer(route, "listDiscordChannels", [{id: "323456789012345602", name: "events-info"}])
    }
    // The games category's channels, and the esports category's where those are asked for.
    if (method === "GET" && path === "/discord/game-channels" && url.searchParams.get("category") === "ESPORTS") {
      return answer(route, "listGameChannels", [
        {id: "7322", guildId: "324", name: "valorant-esports"},
        {id: "7323", guildId: "324", name: "scrims"},
      ])
    }
    if (method === "GET" && path === "/discord/game-channels") {
      return answer(route, "listGameChannels", [
        {id: "6322", guildId: "324", name: "valorant"},
        {id: "6323", guildId: "324", name: "chess"},
        {id: "6324", guildId: "324", name: "fighting-games"},
      ])
    }
    if (method === "GET" && path === "/discord/emojis") {
      return answer(route, "listDiscordEmojis", [{id: "657733730491826186", name: "POGGERS", animated: false}])
    }
    if (method === "GET" && path === "/discord/starboard") {
      // Most stars first, as the api orders them; the lengths vary so the feed shows whole messages.
      const starredAt = (id: string) => `https://discord.com/channels/324285132133629963/611/${id}`
      return answer(route, "readStarboard", [
        {
          id: "1552240000000000001", authorName: "cawalive", authorNickname: "Cas", avatar: null,
          text: "Reminder for tonight: the **Rocket League** in-house starts at 20:00 in the Gaming Room. "
            + "Bring your own controller if you have one, we have four spare.\n\n"
            + "Sign-ups close at 18:00, so if you have not signed up yet, now is the time.",
          image: null, stars: 21, channel: "events-info", href: starredAt("1552240000000000001"), postedAt: "2026-09-19T15:40:00Z",
        },
        {
          id: "1552233582498676818", authorName: "The Old Man", authorNickname: "Joris", avatar: null,
          text: "Soon to be released **events page** redesigns:", image: null, stars: 12, channel: "general",
          href: starredAt("1552233582498676818"), postedAt: "2026-09-23T08:22:05Z",
        },
        {
          id: "1552240000000000002", authorName: "mirte", authorNickname: null, avatar: null,
          text: "who put the pineapple pizza on the LAN party order form", image: null, stars: 9, channel: "general",
          href: starredAt("1552240000000000002"), postedAt: "2026-09-26T21:12:00Z",
        },
        {
          id: "1552240000000000003", authorName: "Viktor", authorNickname: "Vik", avatar: null,
          text: "GG to everyone who came to the Smash tournament yesterday. Final bracket:\n"
            + "1. Emma\n2. Viktor\n3. Cas\n\nSee you all at the next one!",
          image: null, stars: 7, channel: "smash-bros", href: starredAt("1552240000000000003"), postedAt: "2026-09-14T10:05:00Z",
        },
        {
          id: "1552240000000000004", authorName: "Emma", authorNickname: null, avatar: null,
          text: "The board just approved the new streaming setup for the Gaming Room.", image: null, stars: 4, channel: "general",
          href: starredAt("1552240000000000004"), postedAt: "2026-09-28T12:30:00Z",
        },
      ])
    }
    // No bot in the mocked api: the Discord band falls back to the public widget, mocked below.
    if (method === "GET" && (path === "/discord/live" || path === "/discord/live/mine")) {
      return fulfillJson(route, {status: 503, title: "Service Unavailable"}, 503)
    }
    if (method === "GET" && path === "/events") {
      // Search, paging, time, game, committee and order are answered; other filters get every event.
      const params = new URL(route.request().url()).searchParams
      const title = (params.get("titleContains") ?? "").toLowerCase()
      const game = params.get("gameCode")
      const committee = params.get("committeeId")
      const from = params.get("from")
      const to = params.get("to")
      const newestFirst = params.getAll("sort").some(one => one.includes("desc"))
      const found = baseEvents
        .filter(one => String(one.title ?? "").toLowerCase().includes(title))
        .filter(one => !game || ((one as {gameCodes?: string[]}).gameCodes ?? []).includes(game))
        .filter(one => !committee || String(one.committeeId) === committee)
        .filter(one => !from || new Date(String(one.startTime)) >= new Date(from))
        .filter(one => !to || new Date(String(one.startTime)) <= new Date(to))
        .sort((a, b) => String(a.startTime).localeCompare(String(b.startTime)) * (newestFirst ? -1 : 1))
      const size = Number(params.get("size") ?? found.length)
      const at = Number(params.get("page") ?? "0") * size
      return answer(route, "findEvents", {content: found.slice(at, at + size), page: {totalElements: found.length}})
    }
    if (method === "GET" && path === "/events/signups") {
      return answer(route, "findEventSignUps", baseEventSignUps)
    }
    if (method === "GET" && (path === "/events/signups/byAccessToken" || path.startsWith("/events/signups/byAccessToken/"))) {
      return answer(route, "findEventSignUpsByAccessToken", baseEventSignUps)
    }
    if (method === "GET" && /^\/events\/\d+\/signups$/.test(path)) {
      const eventId = path.split("/")[2]
      return answer(route, "findEventSignUpsByEventId", fixtures.eventSignUpsByEventId?.[eventId] ?? [])
    }
    if (method === "GET" && /^\/events\/\d+$/.test(path)) {
      const eventId = Number(path.split("/").at(-1))
      const detail = fixtures.eventDetailsById?.[String(eventId)]
        ?? baseEvents.find((candidate) => Number(candidate.id) === eventId)
      if (!detail) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      return answer(route, "findEventById", detail)
    }
    if (method === "GET" && path === "/committees") {
      return answer(route, "findCommittees", committeesNow())
    }
    if (method === "POST" && path === "/committees") {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<CreateCommitteeRequest>
      const made = committeeSaved(committeeRecord(990 + committeesEdited.size, body.name, body.slug || addressOf(body.name)), body, 0)
      committeesEdited.set(Number(made.id), made)
      return answer(route, "createCommittee", made, 201)
    }
    const committeeAddress = /^\/committees\/address\/([^/]+)$/.exec(path)
    if (method === "GET" && committeeAddress) {
      const found = committeesNow().find(one => one.slug === decodeURIComponent(committeeAddress[1]!).toLowerCase())
      if (!found) return fulfillJson(route, {code: "UnknownCommitteeAddress", address: committeeAddress[1]}, 404)
      const seats = (found.members ?? []).map((member, at) => (at === 0
        ? {discordName: "Nelly B", avatar: "https://cdn.discordapp.com/embed/avatars/1.png", role: member.role ?? null}
        : {discordName: null, avatar: null, role: member.role ?? null}))
      return answer(route, "findCommitteePage", {...found, members: seats})
    }
    const committeeOwn = /^\/committees\/(\d+)\/(page|archived)$/.exec(path)
    if (method === "PUT" && committeeOwn) {
      const id = Number(committeeOwn[1])
      const body = JSON.parse(request.postData() ?? "{}") as Partial<Wire<CommitteeOwnPageRequest>> & {archived?: boolean}
      const stored = committeesNow().find(one => Number(one.id) === id) ?? committeeRecord(id, `Committee ${id}`, `committee-${id}`)
      const changed = {
        ...committeeSaved(stored, {...body, banner: undefined}, stored.version + 1),
        archived: body.archived ?? stored.archived,
      }
      committeesEdited.set(id, changed)
      return answer(route, committeeOwn[2] === "page" ? "updateCommitteePage" : "archiveCommittee", changed)
    }
    const committeeGame = /^\/committees\/games\/([A-Z0-9_]+)$/.exec(path)
    if (method === "PUT" && committeeGame) {
      const code = committeeGame[1]!
      const {committeeIds} = JSON.parse(request.postData() ?? "{}") as {committeeIds: number[]}
      committeesNow().forEach(one => {
        const codes = one.gameCodes.filter(held => held !== code)
        committeesEdited.set(Number(one.id), {...one, gameCodes: committeeIds.includes(Number(one.id)) ? [...codes, code] : codes})
      })
      return answer(route, "setGameOrganisers", committeesNow().filter(one => one.gameCodes.includes(code)))
    }
    if (method === "PUT" && /^\/committees\/\d+$/.test(path)) {
      const id = Number(path.split("/").at(-1))
      const body = JSON.parse(request.postData() ?? "{}") as Wire<UpdateCommitteeRequest>
      const stored = committeesNow().find((candidate) => Number(candidate.id) === id) ?? committeeRecord(id, body.name, body.slug || addressOf(body.name))
      const changed = committeeSaved(stored, body, body.version + 1)
      committeesEdited.set(id, changed)
      return answer(route, "updateCommittee", changed)
    }
    if (method === "GET" && path === "/committeeMembers/committees") {
      return answer(route, "findCommitteesByUserId", baseCommittees)
    }
    if (method === "GET" && path === "/blogs") {
      return answer(route, "findBlogs", baseBlogs)
    }
    if (method === "GET" && /^\/blogs\/[^/]+$/.test(path)) {
      const id = path.split("/").at(-1) ?? ""
      const forcedStatus = fixtures.blogStatusById?.[id]
      if (forcedStatus != null && forcedStatus !== 200) {
        return fulfillJson(route, {status: forcedStatus, detail: "Blog request failed"}, forcedStatus)
      }
      const blog = blogsById[id]
      if (blog != null) {
        return answer(route, "findBlogById", blog)
      }
      return fulfillJson(route, {status: 404, detail: "Blog not found"}, 404)
    }
    if (method === "GET" && path === "/management/jobs/stats") {
      const counts: Record<string, number> = {SUCCESS: 0, SKIPPED: 0, FAILED: 0, DEAD: 0, QUEUED: 0, RUNNING: 0}
      for (const job of baseJobs) {
        const s = toSearchableString(job.status).toUpperCase()
        if (s in counts) counts[s] = (counts[s] ?? 0) + 1
      }
      return answer(route, "getStats", {
        totalCount: baseJobs.length,
        successCount: counts["SUCCESS"],
        skippedCount: counts["SKIPPED"],
        failedCount: counts["FAILED"],
        deadCount: counts["DEAD"],
        queuedCount: counts["QUEUED"],
        runningCount: counts["RUNNING"],
        deadSinceStartup: 0,
        failedSinceStartup: 0,
        recoveriesSinceStartup: 0,
        avgSuccessDurationSeconds: 0,
      })
    }
    if (method === "GET" && path === "/management/jobs/types") {
      return answer(route, "jobTypes", [
        {type: "contact.sync-all", payloadFields: []},
        {type: "contact.sync", payloadFields: [{name: "userId", type: "Long", kind: "PRIMITIVE", required: true}]},
        {type: "email.recovery", payloadFields: [
          {name: "userId", type: "Long", kind: "PRIMITIVE", required: true},
          {name: "token", type: "String", kind: "PRIMITIVE", required: false},
        ]},
      ])
    }
    if (method === "POST" && path === "/management/jobs/enqueue") {
      const body = (route.request().postDataJSON() ?? {}) as {jobType?: string}
      const enqueued = aJob({
        id: 9000 + baseJobs.length,
        jobType: body.jobType ?? "contact.sync",
        status: "QUEUED",
        attempts: 1,
      })
      baseJobs.unshift(enqueued)
      return answer(route, "enqueue", enqueued)
    }
    if (path.startsWith("/management/alerts")) {
      if (method === "GET") return answer(route, "listAlerts", baseAlerts)
      const {key} = request.postDataJSON() as {key: string}
      const one = baseAlerts.find((alert) => alert.key === key)
      if (!one) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      one.hidden = path.endsWith("/hidden")
      return route.fulfill({status: 204})
    }
    if (path === "/management/exceptions" || path.startsWith("/management/exceptions/")) {
      const fault = {
        id: 3,
        exceptionType: "java.lang.IllegalStateException",
        thrownAt: "net.blueshell.api.contact.Sync.run",
        firstSeenAt: "2025-01-01T12:00:00.000Z",
        lastSeenAt: "2025-01-02T12:00:00.000Z",
        occurrences: 4,
        latestMessage: "Brevo said no",
        latestSource: "JOB" as const,
        latestConcern: "contact.sync",
        latestJobExecutionId: 700,
        latestStackTrace: "java.lang.IllegalStateException: Brevo said no\n\tat net.blueshell.api.contact.Sync.run(Sync.kt:4)",
        resolvedAt: exceptionResolvedAt,
      }
      if (method === "GET" && path === "/management/exceptions") {
        const resolved = url.searchParams.get("resolved")
        const shown = resolved === null || (resolved === "true") === (exceptionResolvedAt !== null)
        return answer(route, "listExceptions", shown ? [{...fault, latestStackTrace: null}] : [])
      }
      if (method === "GET" && path === "/management/exceptions/3") return answer(route, "findException", fault)
      if (method === "POST" && path === "/management/exceptions/3/resolve") {
        exceptionResolvedAt = "2025-01-03T12:00:00.000Z"
        return answer(route, "resolveException", {...fault, resolvedAt: exceptionResolvedAt})
      }
      return fulfillJson(route, {title: "Not Found", status: 404}, 404)
    }
    if (method === "GET" && /^\/management\/jobs\/\d+$/.test(path)) {
      const job = baseJobs.find((one) => Number(one.id) === Number(path.split("/")[3]))
      if (!job) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      return answer(route, "findJobById", job)
    }
    if (method === "GET" && path === "/management/jobs") {
      const page = Number(url.searchParams.get("page") ?? "0")
      const size = Number(url.searchParams.get("size") ?? "50")
      const category = (url.searchParams.get("category") ?? "").trim().toLowerCase()
      const status = (url.searchParams.get("status") ?? "").trim().toUpperCase()
      const search = (url.searchParams.get("search") ?? "").trim().toLowerCase()

      let filtered = [...baseJobs]

      if (category && category !== "all") {
        filtered = filtered.filter((job) => jobCategory(job) === category)
      }

      if (status && status !== "ALL") {
        filtered = filtered.filter((job) => toSearchableString(job.status).toUpperCase() === status)
      }

      if (search) {
        filtered = filtered.filter((job) => matchesSearch(job, search))
      }

      filtered.sort((a, b) => Number(b.id ?? 0) - Number(a.id ?? 0))

      const safePage = Number.isFinite(page) && page >= 0 ? page : 0
      const safeSize = Number.isFinite(size) && size > 0 ? size : 50
      const totalElements = filtered.length
      const totalPages = Math.max(1, Math.ceil(totalElements / safeSize))
      const start = safePage * safeSize
      const content = filtered.slice(start, start + safeSize)

      return answer(route, "list", {
        content,
        page: {
          number: safePage,
          size: safeSize,
          totalElements,
          totalPages,
        },
      })
    }
    // The external target catalogue behind the Brevo targets page.
    if (method === "GET" && path === "/management/cohort-targets/systems") {
      return answer(route, "listCohortTargetSystems", [
        {
          system: "BREVO",
          kind: "LIST",
        },
      ])
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO/folders") {
      // Includes a folder holding nothing, which is exactly where a target tends to head.
      return answer(route, "listCohortTargetFolders", ["Committees", "Contribution periods", "Archive"])
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO/overview") {
      return answer(route, "findTargetOverview", {
        lists: [
          {externalId: "7", label: "Members 2025-2026", folderLabel: "Members", memberCount: 211, targetId: 1, cohortId: 101,
            cohortLabel: "Members 2025-2026", cohortType: "PERIOD_MEMBERS", missing: 0, extra: 0, lastReconciledAt: "2026-10-01T03:00:00.000Z", enforced: false},
          {externalId: "33", label: "Web Cmte", folderLabel: "Committees", memberCount: 9, targetId: 2, cohortId: 102,
            cohortLabel: "Web Cmte", cohortType: "COMMITTEE_MEMBERS", missing: 1, extra: 0, lastReconciledAt: "2026-10-01T03:00:00.000Z", enforced: false},
          {externalId: "9", label: "Old newsletter test", folderLabel: null, memberCount: 4, enforced: false},
          {externalId: "10", label: "LAN party 2024", folderLabel: "Archive", memberCount: 57, enforced: false},
        ],
        missing: [{targetId: 3, cohortId: 103, cohortLabel: "Paid 2026-2027", cohortType: "PERIOD_PAYERS", folder: "Contribution paid",
          memberCount: 142, creating: false}],
        lastReconciledAt: "2026-10-01T03:00:00.000Z",
      })
    }
    const listOf = path.match(/^\/management\/cohort-targets\/BREVO\/lists\/(\w+)$/)
    if (method === "GET" && listOf) {
      const linked: Record<string, {cohortId: number; targetId: number; label: string; cohortType: "PERIOD_MEMBERS" | "COMMITTEE_MEMBERS"}> = {
        "7": {cohortId: 101, targetId: 1, label: "Members 2025-2026", cohortType: "PERIOD_MEMBERS"},
        "33": {cohortId: 102, targetId: 2, label: "Web Cmte", cohortType: "COMMITTEE_MEMBERS"},
      }
      const one = linked[listOf[1]]
      return answer(route, "findListedTarget", one
        ? {externalId: listOf[1], label: one.label, folderLabel: "Members", memberCount: 41, targetId: one.targetId, cohortId: one.cohortId,
          cohortLabel: one.label, cohortType: one.cohortType, missing: 1, extra: 2, lastReconciledAt: "2026-02-10T09:00:00.000Z", enforced: false}
        : {externalId: listOf[1], label: "Old newsletter test", folderLabel: null, memberCount: 4, enforced: false})
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO/tidy") {
      return answer(route, "previewFolderTidy", {
        moves: [{externalId: "33", label: "Web Cmte", from: null, to: "Committees"}],
        foldersToCreate: [],
        lastApplied: {appliedAt: "2026-09-01T10:00:00.000Z", appliedByName: "Mock User", moved: 4, failed: 0},
      })
    }
    if (method === "POST" && path === "/management/cohort-targets/BREVO/tidy") {
      const {externalIds} = request.postDataJSON() as {externalIds: string[]}
      return answer(route, "applyFolderTidy", {
        moved: externalIds.map((externalId) => ({system: "BREVO" as const, externalId, kind: "LIST" as const, label: "Web Cmte", folderLabel: "Committees", path: ["Brevo", "Committees"]})),
        failed: [],
      })
    }
    if (method === "POST" && path === "/management/cohort-targets/BREVO/missing") {
      const {targetIds} = request.postDataJSON() as {targetIds: number[]}
      return answer(route, "createMissingTargets", {queued: targetIds.length === 0 ? 1 : targetIds.length})
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO") {
      return answer(route, "searchCohortTargets", brevoTargets)
    }
    if (method === "GET" && path === "/management/cohorts/targets") {
      return answer(route, "listTargetOptions", [
        {id: 1, system: "BREVO", kind: "LIST", label: "Members 2025-2026", memberCount: 2},
        {id: 2, system: "BREVO", kind: "LIST", label: "Web Cmte", memberCount: 1},
      ])
    }
    if (method === "GET" && path === "/boards") {
      return answer(route, "findAllBoards", boardsNow())
    }
    if (method === "GET" && /^\/boards\/\d+$/.test(path)) {
      const id = Number(path.split("/")[2])
      const board = boardsNow().find((b) => Number(b.id) === id)
      if (!board) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      return answer(route, "findBoardById", board)
    }
    // A board's number is its identity, so the api refuses a number another board holds and
    // says which one it is — the refusal a dialog has to be able to report.
    if ((method === "PUT" || method === "POST") && /^\/boards(\/\d+)?$/.test(path)) {
      const id = method === "PUT" ? Number(path.split("/")[2]) : null
      const body = JSON.parse(request.postData() ?? "{}") as Wire<BoardRequest>
      const taken = boardsNow().some(
        (b) => Number(b.number) === Number(body.number) && Number(b.id) !== id,
      )
      if (taken) {
        return fulfillJson(route, {
          title: "Conflict",
          detail: `Board ${body.number} already exists`,
        }, 409)
      }
      if (id != null) {
        const board = boardsNow().find((b) => Number(b.id) === id)
        const held = board ?? {id, members: [], version: 0, createdAt: "2026-01-02T00:00:00Z"}
        const saved = {...boardWritten(held, body), id, version: 1}
        boardsEdited.set(id, saved)
        return answer(route, "updateBoard", saved)
      }
      nextBoardId += 1
      const made = boardWritten({
        id: nextBoardId,
        members: [],
        version: 0,
        createdAt: "2026-01-02T00:00:00Z",
      }, body)
      boardsMade.push(made)
      return answer(route, "createBoard", made, 201)
    }
    if (method === "DELETE" && /^\/boards\/\d+$/.test(path)) {
      const id = Number(path.split("/")[2])
      const board = boardsNow().find((b) => Number(b.id) === id)
      const held = board?.members
      const members = Array.isArray(held) ? held.length : 0
      if (members > 0) {
        return answer(route, "deleteBoard", {
          detail: "That board cannot be removed.",
          code: "BoardHoldsMembers",
          number: board?.number,
          members,
        }, 409)
      }
      boardsGone.add(id)
      return route.fulfill({status: 204, body: ""})
    }
    /*
     * A member written down, corrected, linked or removed lands on the board it belongs to, so a
     * page that reads again is answered the way the api would rather than told the board never
     * changed. Held on the fixture's own `members` rather than beside it, because that is the
     * one list every read of a board goes through.
     *
     * A fixture a spec shares between tests would carry a write into the next one, so a spec
     * that writes hands `installApiMocks` boards of its own making.
     *
     * The boards themselves rather than `boardsNow()`, which composes a copy per board: a member
     * pushed onto a copy's `members` would reach the board it belongs to and one removed
     * from one would not, because a removal replaces the list rather than adding to it.
     */
    const boardHolding = (boardId: number): Wire<BoardResponse> | undefined =>
      [...(fixtures.boards ?? boardFixtures), ...boardsMade]
        .find((b) => Number(b.id) === boardId)

    const membersOf = (board: Wire<BoardResponse> | undefined): Wire<BoardMemberResponse>[] => board?.members ?? []

    /**
     * A member as the api answers with one after a write.
     *
     * Every field the write carries is replaced the way the api's own does, so a field the save
     * left out is cleared rather than kept. The save named where its portrait is stored and the
     * answer carries the picture itself; naming none clears the portrait. The name arrives as
     * `displayName` and is answered as `name`, which is the api's own asymmetry.
     */
    const memberWritten = (
      base: Pick<Wire<BoardMemberResponse>, "id" | "boardId" | "userId" | "version" | "createdAt">,
      body: Wire<UpdateBoardMemberRequest>,
    ): Wire<BoardMemberResponse> => ({
      ...base,
      role: body.role,
      name: body.displayName ?? null,
      nickname: body.nickname ?? null,
      description: body.description ?? null,
      portrait: pictureNamed(body.portrait),
      startDate: body.startDate,
      endDate: body.endDate ?? null,
      updatedAt: "2026-01-02T00:00:00Z",
    })

    if (method === "POST" && /^\/boards\/\d+\/members$/.test(path)) {
      const boardId = Number(path.split("/")[2])
      const body = JSON.parse(request.postData() ?? "{}") as Wire<AddBoardMemberRequest>
      nextMemberId += 1
      const made = memberWritten({
        id: nextMemberId,
        boardId,
        userId: body.userId ?? null,
        version: 0,
        createdAt: "2026-01-02T00:00:00Z",
      }, body)
      const board = boardHolding(boardId)
      board?.members.push(made)
      return answer(route, "addMember", made, 201)
    }
    if (method === "PUT" && /^\/boards\/\d+\/members\/\d+$/.test(path)) {
      const boardId = Number(path.split("/")[2])
      const id = Number(path.split("/")[4])
      const body = JSON.parse(request.postData() ?? "{}") as Wire<UpdateBoardMemberRequest>
      const members = membersOf(boardHolding(boardId))
      const at = members.findIndex((one) => Number(one.id) === id)
      if (at === -1) return fulfillJson(route, {detail: "No such member."}, 404)
      const saved = {...memberWritten(members[at]!, body), version: 1}
      members[at] = saved
      return answer(route, "updateMember", saved)
    }
    // A null account detaches the member, which keeps standing under its own name.
    if (method === "PUT" && /^\/boards\/\d+\/members\/\d+\/member$/.test(path)) {
      const boardId = Number(path.split("/")[2])
      const id = Number(path.split("/")[4])
      const body = JSON.parse(request.postData() ?? "{}") as Wire<LinkBoardMemberRequest>
      const members = membersOf(boardHolding(boardId))
      const at = members.findIndex((one) => Number(one.id) === id)
      if (at === -1) return fulfillJson(route, {detail: "No such member."}, 404)
      const linked = {...members[at]!, userId: body.userId ?? null, version: 1}
      members[at] = linked
      return answer(route, "linkMember", linked)
    }
    if (method === "DELETE" && /^\/boards\/\d+\/members\/\d+$/.test(path)) {
      const boardId = Number(path.split("/")[2])
      const id = Number(path.split("/")[4])
      const board = boardHolding(boardId)
      if (board) board.members = membersOf(board).filter((one) => Number(one.id) !== id)
      return route.fulfill({status: 204, body: ""})
    }
    // The casual games, kept per page so a spec sees its own adds, archives and removals.
    if (method === "GET" && path === "/games") {
      return answer(route, "findCasualGames", casualNow())
    }
    if (method === "POST" && path === "/games") {
      const body = JSON.parse(request.postData() ?? "{}") as CasualGameBody
      const held = casualNow().find(one => one.slug === body.slug)
      if (held) return fulfillJson(route, addressTaken(String(held.name), body.slug), 409)
      const code = body.name.toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "")
      const made = casualGame(code, body.name, body.slug, body.sortIndex ?? 99, {
        intro: body.intro ?? null, accent: body.accent ?? null, channels: body.channels ?? [],
        banner: pictureNamed(body.banner), icon: pictureNamed(body.icon),
        competitionIntro: body.competitionIntro ?? null, esportsChannels: body.esportsChannels ?? [],
      })
      casualEdited.set(code, made)
      return answer(route, "createCasualGame", made, 201)
    }
    const casualArchive = /^\/games\/([A-Z0-9_]+)\/archived$/.exec(path)
    if (method === "PUT" && casualArchive) {
      const code = casualArchive[1]!
      const now = casualNow().find(one => one.code === code)
      if (!now) return fulfillJson(route, {code: "UnknownGameCode", gameCode: code}, 400)
      const {archived} = JSON.parse(request.postData() ?? "{}") as {archived: boolean}
      const changed = {...now, archived}
      casualEdited.set(code, changed)
      return answer(route, "archiveGame", changed)
    }
    const casualHoldings = /^\/games\/([A-Z0-9_]+)\/holdings$/.exec(path)
    if (method === "GET" && casualHoldings) {
      const teams = teamsHeldBy(casualHoldings[1]!)
      return answer(route, "findGameHoldings", {channels: 1, committees: 0, events: 2, teams, players: teams * 3})
    }
    const casualOne = /^\/games\/([A-Z0-9_]+)$/.exec(path)
    if (method === "PUT" && casualOne) {
      const code = casualOne[1]!
      const now = casualNow().find(one => one.code === code)
      if (!now) return fulfillJson(route, {code: "UnknownGameCode", gameCode: code}, 400)
      const body = JSON.parse(request.postData() ?? "{}") as CasualGameBody
      const held = casualNow().find(one => one.slug === body.slug && one.code !== code)
      if (held) return fulfillJson(route, addressTaken(String(held.name), body.slug), 409)
      const changed = {
        ...now, name: body.name, slug: body.slug, intro: body.intro ?? null, accent: body.accent ?? null,
        banner: pictureNamed(body.banner), icon: pictureNamed(body.icon), sortIndex: body.sortIndex ?? now.sortIndex,
        channels: body.channels ?? now.channels,
        competitionIntro: body.competitionIntro ?? null, esportsChannels: body.esportsChannels ?? now.esportsChannels,
      }
      casualEdited.set(code, changed)
      return answer(route, "updateCasualGame", changed)
    }
    if (method === "DELETE" && casualOne) {
      const teams = teamsHeldBy(casualOne[1]!)
      if (teams > 0) {
        const game = casualNow().find(one => one.code === casualOne[1])
        return fulfillJson(route, {
          detail: "That game cannot be removed.", code: "GameHoldsHistory", gameName: game?.name ?? casualOne[1], teams, players: teams * 3,
        }, 409)
      }
      casualGone.add(casualOne[1]!)
      gamesGone.add(casualOne[1]!)
      return route.fulfill({status: 204, body: ""})
    }
    // [A-Z0-9_]+ rather than [A-Z_]+: a game's enum name can carry a digit, and
    // CS2 is one. With the digit excluded this route never matched, so every CS2
    // page in the suite silently rendered as having no teams.
    if (method === "GET" && /^\/esports\/games\/[A-Z0-9_]+$/.test(path)) {
      const game = path.split("/").pop() ?? ""
      const requested = url.searchParams.get("seasonId")
      const fresh = requested != null ? written.get(Number(requested)) : undefined
      if (fresh) {
        // Nobody has been fielded in it yet, which is the answer rather than a reason to
        // show a different season's teams.
        return answer(route, "findGame", {game, season: fresh, seasons: esportsSeasons, teams: []})
      }
      // Asked about no season in particular, the api answers with the newest season *this
      // game* was fielded in — which is a different season per game, and the whole reason a
      // page has to name the season it means. CS:GO is what proves it: it played the older
      // of the two seasons and has played nothing since.
      const ownNewest = game === "CSGO" ? "19" : "20"
      const page = fixtures.esportsPages?.[requested ?? ownNewest]
        ?? esportsPageBySeason[requested ?? ownNewest]
        ?? esportsPageBySeason["20"]
      const offered = page.seasons
        .filter(one => !gone.has(one.id))
        .filter(one => game !== "CSGO" || one.id === 19)
      const shownSeason = Number(page.season?.id ?? requested ?? 20)
      return answer(route, "findGame", {
        ...page,
        game,
        seasons: offered,
        teams: teamsOfGameInSeason(game, shownSeason),
      })
    }
    if (method === "POST" && path === "/esports/seasons") {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<SeasonRequest>
      const season = aSeason({...body, id: 41})
      written.set(41, season)
      return answer(route, "createSeason", season, 201)
    }
    if (method === "GET" && /^\/esports\/seasons\/\d+\/contents$/.test(path)) {
      const seasonId = Number(path.split("/")[3])
      const held = roster.filter(one => one.seasonId === seasonId)
      const teamsHeld = new Set(held.map(one => one.teamId))
      return answer(route, "findSeasonContents", {teams: teamsHeld.size, players: held.length})
    }
    if (method === "DELETE" && /^\/esports\/seasons\/\d+$/.test(path)) {
      const seasonId = Number(path.split("/").pop())
      gone.add(seasonId)
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "DELETE" && /^\/esports\/seasons\/\d+\/teams\/\d+$/.test(path)) {
      const parts = path.split("/")
      const seasonId = Number(parts[3])
      const teamId = Number(parts[5])
      dropped.push({seasonId, teamId})
      const at = fieldedNow.findIndex(one => one.seasonId === seasonId && one.teamId === teamId)
      if (at >= 0) fieldedNow.splice(at, 1)
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "PUT" && /^\/esports\/seasons\/\d+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<SeasonRequest>
      const season = aSeason({...body, id: Number(path.split("/").pop())})
      written.set(season.id, season)
      return answer(route, "updateSeason", season)
    }
    // The band: the games of one season, and whether a visitor sees each. The rule turns on
    // who is asking, exactly as the api has it.
    if (method === "GET" && /^\/esports\/seasons\/\d+\/games$/.test(path)) {
      const seasonId = Number(path.split("/")[3])
      const board = isBoard()
      const codes = [...new Set([...(fixtures.esportsGames ?? esportsGames), ...casualEdited.values()]
        .map(one => String(one.code)))]
        .filter(code => !gamesGone.has(code))
      const played = codes
        .map(code => ({game: code, teams: teamsOfGameInSeason(code, seasonId)}))
        .filter(one => one.teams.length > 0)
        .map(one => ({...one, public: true}))
      if (!board) return answer(route, "findSeasonGames", played)
      const shown = new Set(played.map(one => one.game))
      const quiet = gamesEntered
        .filter(one => one.seasonId === seasonId && !shown.has(one.game) && !gamesGone.has(one.game))
        .map(one => ({game: one.game, teams: [], public: false}))
      return answer(route, "findSeasonGames", [...played, ...quiet].sort(
        (a, b) => codes.indexOf(a.game) - codes.indexOf(b.game),
      ))
    }
    if (method === "PUT" && /^\/esports\/seasons\/\d+\/games\/[A-Z0-9_]+$/.test(path)) {
      const parts = path.split("/")
      const seasonId = Number(parts[3])
      const game = String(parts[5])
      if (!gamesEntered.some(one => one.seasonId === seasonId && one.game === game)) {
        gamesEntered.push({seasonId, game})
      }
      const teams = teamsOfGameInSeason(game, seasonId)
      return answer(route, "enterGame", {game, teams, public: teams.length > 0})
    }
    if (method === "DELETE" && /^\/esports\/seasons\/\d+\/games\/[A-Z0-9_]+$/.test(path)) {
      const parts = path.split("/")
      const seasonId = Number(parts[3])
      const game = String(parts[5])
      const held = teamsOfGameInSeason(game, seasonId)
      if (held.length > 0) {
        const known = (fixtures.esportsGames ?? esportsGames)
          .find(one => one.code === game)
        return answer(route, "leaveGame", {
          detail: "That game cannot be taken out of the season.",
          code: "GameFieldedInSeason",
          gameName: known?.name ?? game,
          teams: held.length,
        }, 409)
      }
      const at = gamesEntered.findIndex(one => one.seasonId === seasonId && one.game === game)
      if (at >= 0) gamesEntered.splice(at, 1)
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "GET" && path === "/esports/seasons") {
      // A season saved over a fixture one takes its place rather than joining it.
      const all = [
        ...(fixtures.esportsSeasons ?? esportsSeasons).filter(one => !written.has(one.id)),
        ...written.values(),
      ]
      return answer(route, "findSeasons", all.filter(one => !gone.has(one.id)))
    }
    if (method === "POST" && path === "/files/images") {
      // What was chosen, read out of the multipart body: the api tells a vector from a bitmap
      // and answers differently, so a mock that answered the same for both would hide it.
      const chose = request.postDataBuffer()?.includes("image/svg+xml") ?? false
      return answer(route, "uploadPublicImage", storePicture(url.searchParams.get("type") ?? "", chose), 201)
    }
    // A real image rather than an empty body: a url that resolves to nothing still sets an
    // `src`, so only an image that actually decodes proves the page is pointing at the api.
    //
    // 512 square, not 1: with a `srcset` the browser divides the bytes' real width by the
    // chosen candidate's density, so a one-pixel image answering a `128w` candidate has a
    // sub-pixel intrinsic size and Chromium lays it out at no height at all.
    //
    // Never cached, because a mock file's address is the same in every test: Chromium will
    // draw a wider copy it already holds in preference to the one a `srcset` asks for, so a
    // copy cached by an earlier load decides what a later load appears to fetch.
    if (method === "GET" && /^\/files\/public\/[^/]+\/[^/]+$/.test(path)) {
      /*
       * And a portrait comes back taller than it is wide, because that is what a portrait is.
       *
       * A stacked member's slice draws a portrait in a box of the picture's **own** aspect, and
       * the test of that is that the box and the photograph are the same shape. A square answer
       * would pass that assertion whichever way round the aspect had been read, and would pass
       * it just as happily if the aspect were ignored altogether — so the one shape a portrait
       * must not be here is the shape that proves nothing.
       *
       * 512 by 768, which is one and a half times as tall as wide: every portrait the
       * association has recorded is between 1.36 and 1.55, and most are exactly this.
       */
      // A vector comes back as one, under the policy the api serves a public file with: the
      // bytes are the uploader's own, and the header is what stops a browser running them.
      // Stated here as the api states it in FileResponses.kt -- change one, change the other.
      if (path.endsWith(".svg")) {
        return route.fulfill({
          status: 200,
          contentType: "image/svg+xml",
          headers: {
            "cache-control": "no-store",
            "content-security-policy":
              "default-src 'none'; img-src 'self' data:; style-src 'unsafe-inline'; font-src data:; sandbox",
            "x-content-type-options": "nosniff",
          },
          body: `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">` +
            `<path d="M2 2h20v20H2z" fill="#0af"/></svg>`,
        })
      }
      const tall = path.startsWith("/files/public/board-portraits/")
      return route.fulfill({
        status: 200,
        contentType: "image/webp",
        headers: {"cache-control": "no-store"},
        body: Buffer.from(
          tall
            ? "UklGRjQAAABXRUJQVlA4TCgAAAAv/8G/AAdQogI1qv8BgUCyP/gKRfQ/4z//+c9//vOf//znP//5z/8F"
            : "UklGRi4AAABXRUJQVlA4TCIAAAAv/8F/AAdQs840s/8BgUCyv/cMRfQ/4z//+c9//vOf//wf",
          "base64",
        ),
      })
    }
    // The pool is the association's rather than a game's, so it is answered whole. A team
    // that has only ever played another game is still one the board can field in this one.
    if (method === "GET" && path === "/esports/teams") {
      const known = fixtures.esportsTeams ?? [
        {id: 1, name: "BS Waterboarders", icon: null},
        {id: 2, name: "BS SpicyWater", icon: null},
        {id: 3, name: "BS Old Guard", icon: null},
      ]
      return answer(route, "findTeams", [...known, ...teamsMade])
    }
    if (method === "POST" && path === "/esports/teams") {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<TeamRequest>
      nextTeamId += 1
      const icon = pictureNamed(body.icon)
      const team = {id: nextTeamId, name: body.name, icon: icon ?? null}
      teamsMade.push(team)
      if (icon) teamIcons.set(nextTeamId, icon)
      return answer(route, "createTeam", team, 201)
    }
    // The logo is part of this write, so a save naming none takes the team's away — which is
    // what the picker's Remove means once the dialog around it is saved. The banner is not
    // here: it belongs to the fielding, and is written with the season.
    if (method === "PUT" && /^\/esports\/teams\/\d+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<TeamRequest>
      const id = Number(path.split("/").pop())
      const icon = pictureNamed(body.icon)
      renamed.set(id, {name: body.name, banner: teamBanners.get(id) ?? null, icon: icon ?? null})
      if (icon) teamIcons.set(id, icon)
      else teamIcons.delete(id)
      return answer(route, "updateTeam", {id, name: body.name, icon: icon ?? null})
    }
    if (method === "DELETE" && /^\/esports\/teams\/\d+$/.test(path)) {
      goneTeams.add(Number(path.split("/").pop()))
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "GET" && /^\/esports\/teams\/\d+\/seasons$/.test(path)) {
      // Only the one team in these fixtures has a line-up behind it to carry from. A fielding
      // rather than a season, because a team that played two games in one season has two.
      const teamId = Number(path.split("/")[3])
      return answer(route, "findTeamSeasons", teamId === 3 ? [{game: "VALORANT", season: esportsSeasons[1]}] : [])
    }
    // One Save: the team, this season's art, the entries taken off and everybody else in order,
    // applied whole as the api does.
    if (method === "PUT" && /^\/esports\/seasons\/\d+\/lineup$/.test(path)) {
      const seasonId = Number(path.split("/")[3])
      const body = JSON.parse(request.postData() ?? "{}") as Wire<PublishLineupRequest>
      const icon = pictureNamed(body.icon)
      let teamId = body.teamId ?? null
      if (teamId == null) {
        nextTeamId += 1
        teamId = nextTeamId
        teamsMade.push({id: teamId, name: body.name, icon: icon ?? null})
      } else {
        renamed.set(teamId, {name: body.name, banner: teamBanners.get(teamId) ?? null, icon: icon ?? null})
      }
      if (icon) teamIcons.set(teamId, icon)
      else teamIcons.delete(teamId)
      const banner = pictureNamed(body.banner)
      if (banner) teamBanners.set(teamId, banner)
      let seated = fieldedNow.find(one => one.teamId === teamId && one.seasonId === seasonId)
      if (!seated) {
        seated = {seasonId, teamId, game: String(body.game ?? "VALORANT"), members: []}
        fieldedNow.push(seated)
      }
      for (const id of body.removed ?? []) {
        const at = roster.findIndex(one => one.id === id)
        if (at >= 0) roster.splice(at, 1)
      }
      const saved = (body.entries ?? []).map((entry, sortIndex) => {
        const fields = {
          handle: entry.handle, role: entry.role, displayName: entry.displayName ?? null,
          userId: entry.userId ?? null, sortIndex, roleTitle: entry.roleTitle ?? null,
          description: entry.description ?? null,
        }
        let row = entry.id == null ? undefined : roster.find(one => one.id === entry.id)
        if (row) {
          Object.assign(row, fields)
        } else {
          nextEntryId += 1
          row = {id: nextEntryId, teamId, seasonId, ...fields}
          roster.push(row)
          seated?.members.push({role: entry.role, handle: entry.handle, name: null})
        }
        const picture = pictureNamed(entry.icon)
        if (picture) icons.set(Number(row.id), picture)
        else icons.delete(Number(row.id))
        return {...row, icon: picture ?? null}
      })
      return answer(route, "publishLineup", {team: {id: teamId, name: body.name, icon: icon ?? null}, roster: saved})
    }
    if (method === "PUT" && /^\/esports\/seasons\/\d+\/teams\/\d+$/.test(path)) {
      const parts = path.split("/")
      const seasonId = Number(parts[3])
      const teamId = Number(parts[5])
      const body = JSON.parse(request.postData() ?? "{}") as Wire<FieldTeamRequest>
      const team = [...(fixtures.esportsTeams ?? []), ...teamsMade,
        {id: 1, name: "BS Waterboarders"},
        {id: 2, name: "BS SpicyWater"},
        {id: 3, name: "BS Old Guard"},
      ].find(one => one.id === teamId) ?? {id: teamId, name: `Team ${teamId}`}
      // The art is the fielding's, and naming none leaves what it has.
      const banner = pictureNamed(body.banner)
      if (banner) teamBanners.set(teamId, banner)
      // What comes across is the line-up that was asked for, read from the same rows the
      // picker reads. A named source wins over "the most recent in this game"; carrying a
      // line-up the picker showed and a different one arriving is the bug this would hide.
      const from = body.carryFrom
      const source = from?.seasonId != null
        ? roster.filter(one => one.teamId === teamId && one.seasonId === from.seasonId)
        : body.carryLineup === true
          ? roster.filter(one => one.teamId === teamId && one.seasonId !== seasonId)
          : []
      const carried = source.map(asMember)
      fieldedNow.push({seasonId, teamId, game: String(body.game ?? "VALORANT"), members: carried})
      return answer(route, "fieldTeam", {
        team,
        game: body.game ?? "VALORANT",
        banner: teamBanners.get(teamId) ?? null,
        season: written.get(seasonId) ?? esportsSeasons.find(one => one.id === seasonId) ?? esportsSeasons[0],
        carried: carried.map((member, index) => ({
          id: 300 + index, teamId, seasonId, role: member.role, handle: member.handle,
          displayName: null, userId: null, sortIndex: index,
        })),
      })
    }
    if (method === "GET" && /^\/esports\/teams\/\d+\/roster$/.test(path)) {
      if (fixtures.esportsRoster) return answer(route, "findRoster", fixtures.esportsRoster)
      const teamId = Number(path.split("/")[3])
      const seasonId = Number(url.searchParams.get("seasonId"))
      return answer(route, "findRoster", roster
        .filter(one => one.teamId === teamId && one.seasonId === seasonId)
        .map(one => ({...one, icon: icons.get(Number(one.id)) ?? null})))
    }
    if (method === "PUT" && /^\/esports\/roster\/\d+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<UpdateRosterEntryRequest>
      const id = Number(path.split("/").pop())
      const entry = roster.find(one => one.id === id)
      if (entry) Object.assign(entry, {
        handle: body.handle, role: body.role, displayName: body.displayName ?? null,
        sortIndex: body.sortIndex, roleTitle: body.roleTitle ?? null, description: body.description ?? null,
      })
      const icon = pictureNamed(body.icon)
      if (icon) icons.set(id, icon)
      else icons.delete(id)
      if (!entry) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      return answer(route, "updateRosterEntry", {...entry, icon})
    }
    if (method === "DELETE" && /^\/esports\/roster\/\d+$/.test(path)) {
      const id = Number(path.split("/").pop())
      const at = roster.findIndex(one => one.id === id)
      if (at >= 0) roster.splice(at, 1)
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "POST" && /^\/esports\/teams\/\d+\/roster$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<AddRosterEntryRequest>
      const teamId = Number(path.split("/")[3])
      nextEntryId += 1
      const seated = fieldedNow.find(one => one.teamId === teamId && one.seasonId === body.seasonId)
      seated?.members.push({role: body.role, handle: body.handle, name: null})
      const added = {
        id: nextEntryId, teamId, seasonId: body.seasonId, role: body.role, handle: body.handle,
        displayName: body.displayName ?? null, userId: body.userId ?? null,
        sortIndex: roster.length, roleTitle: body.roleTitle ?? null,
        description: body.description ?? null,
      }
      roster.push(added)
      const icon = pictureNamed(body.icon)
      if (icon) icons.set(nextEntryId, icon)
      return answer(route, "addRosterEntry", {...added, icon}, 201)
    }
    if (method === "PUT" && /^\/esports\/roster\/\d+\/member$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Wire<LinkRosterEntryRequest>
      const id = Number(path.split("/")[3])
      const entry = roster.find(one => one.id === id)
      if (!entry) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      entry.userId = body.userId ?? null
      return answer(route, "linkRosterEntry", entry)
    }
    if (method === "GET" && /^\/users\/\d+\/rosters$/.test(path)) {
      return answer(route, "findPlayedRosters", [
        {game: "VALORANT", seasonId: 1, seasonName: "Spring 2026", seasonStart: "2026-02-01", teamId: 3, teamName: "Blue Shells", role: "PLAYER", roleTitle: "Captain"},
      ])
    }
    if (method === "PUT" && /^\/users\/\d+\/name-on-rosters$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as {shown?: boolean}
      const id = Number(path.split("/")[2])
      return answer(route, "setNameOnRosters", aUser({id, fullName: "Mock User", roles: cookieLogin?.roles ?? ["MEMBER"], nameOnRosters: body.shown === true}))
    }
    if (method === "GET" && /^\/users\/\d+\/game-accounts$/.test(path)) {
      return answer(route, "findGameAccounts", [{id: 5, userId: 1, game: "VALORANT", handle: "AriosFury"}])
    }
    if (method === "PUT" && /^\/users\/\d+\/game-accounts\/[A-Z0-9_]+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as {handle: string}
      return answer(route, "setGameAccount", {id: 5, userId: 1, game: path.split("/").pop() ?? "", handle: body.handle})
    }
    if (method === "GET" && path === "/management/cohorts") {
      return answer(route, "findCohorts", fixtures.cohorts ?? [
        {
          id: 101,
          type: "PERIOD_MEMBERS",
          category: "PERIODS",
          label: "Members 2025-2026",
          memberCount: 2,
          mappingCount: 1,
        },
        {
          id: 102,
          type: "COMMITTEE_MEMBERS",
          category: "COMMITTEES",
          label: "Web Cmte",
          memberCount: 1,
          mappingCount: 1,
        },
      ])
    }
    const driftOf = path.match(/^\/management\/cohorts\/\d+\/targets\/\d+\/drift\/(push|remove)$/)
    if (method === "POST" && driftOf) {
      const body = request.postDataJSON() as {userIds?: number[]; externalUserIds?: string[]}
      return answer(route, driftOf[1] === "push" ? "pushDrift" : "removeDrift", {resolved: (body.userIds ?? body.externalUserIds ?? []).length})
    }
    if (method === "GET" && /^\/management\/cohorts\/\d+$/.test(path)) {
      const id = Number(path.split("/")[3] ?? "0")
      const isCommittee = id === 102
      return answer(route, "findCohortById", {
        id,
        type: isCommittee ? "COMMITTEE_MEMBERS" : "PERIOD_MEMBERS",
        category: isCommittee ? "COMMITTEES" : "PERIODS",
        label: isCommittee ? "Web Cmte" : "Members 2025-2026",
        description: null,
        mappings: [
          {
            targetId: isCommittee ? 2 : 1,
            system: "BREVO",
            kind: "LIST",
            label: isCommittee ? "Web Cmte" : "Members 2025-2026",
            path: isCommittee ? ["Brevo", "Committees"] : ["Brevo", "Contribution periods"],
            externalId: isCommittee ? "33" : "7",
            lastReconciledAt: "2026-02-10T09:00:00Z",
            folderKnown: true,
            runs: [
              {startedAt: "2026-02-10T09:00:00Z", trigger: "SCHEDULED_RUN", inSync: 40, oursOnly: 1, theirsOnly: 2, unreachable: 0},
              {startedAt: "2026-02-09T09:00:00Z", trigger: "SCHEDULED_RUN", inSync: 38, oursOnly: 3, theirsOnly: 2, unreachable: 0},
            ],
            enforced: false,
          },
        ],
        definitionKey: isCommittee ? "COMMITTEE_MEMBERS:42" : "PERIOD_MEMBERS:1",
        orphaned: false,
        resolutions: [
          {
            targetId: isCommittee ? 2 : 1,
            system: "BREVO",
            action: "REMOVE",
            externalUserId: "ext-9",
            personName: "old@example.com",
            resolvedByName: "Board Member",
            resolvedAt: "2026-02-10T10:00:00Z",
          },
        ],
        ...(fixtures.cohortDetail ?? {}),
        // One of each state the page draws: in sync, ours-but-not-pushed, and two rows the
        // target has that we do not — one we can name, one we cannot.
        members: fixtures.cohortMembers ?? [
          {
            targetMemberId: 200 + id,
            system: "BREVO",
            state: "VERIFIED",
            userId: 1,
            userFullName: "Emma Dokter",
            userEmail: "emma@example.com",
            isUserDeleted: false,
            externalUserId: "ext-1",
            externalLabel: null,
            joinedAt: "2026-01-15T10:00:00Z",
          },
          {
            targetMemberId: 300 + id,
            system: "BREVO",
            state: "DESIRED",
            userId: 2,
            userFullName: "Bram Boardmade",
            userEmail: "bram@example.com",
            isUserDeleted: false,
            externalUserId: null,
            externalLabel: null,
            joinedAt: "2026-02-01T10:00:00Z",
          },
          {
            targetMemberId: 400 + id,
            system: "BREVO",
            state: "STRANGER",
            userId: 3,
            userFullName: "Casper Known",
            userEmail: "casper@example.com",
            isUserDeleted: false,
            externalUserId: "ext-known",
            externalLabel: "casper@example.com",
            joinedAt: "2026-02-02T10:00:00Z",
          },
          {
            targetMemberId: 500 + id,
            system: "BREVO",
            state: "STRANGER",
            userId: null,
            userFullName: null,
            userEmail: null,
            isUserDeleted: false,
            externalUserId: "ext-unknown",
            externalLabel: "someone@example.com",
            joinedAt: "2026-02-03T10:00:00Z",
          },
        ],
      })
    }
    if (method === "POST" && /^\/management\/jobs\/\d+\/retry$/.test(path)) {
      const rawId = path.split("/")[3]
      const id = Number(rawId)
      const index = baseJobs.findIndex((job) => Number(job.id) === id)
      const existing = index >= 0 ? baseJobs[index] : undefined
      const retried: Wire<JobExecution> = {
        ...(existing ?? aJob({id, jobType: "SYNC_DISCORD"})),
        status: "RUNNING",
        attempts: Number(existing?.attempts ?? 0) + 1,
        errorType: null,
        errorReason: null,
        errorMessage: null,
      }
      if (index >= 0) {
        baseJobs.splice(index, 1, retried)
      } else {
        baseJobs.unshift(retried)
      }
      return answer(route, "retry", retried)
    }
    if (method === "GET" && path === "/management/emails/stats") {
      const counts: Record<string, number> = {QUEUED: 0, SENT: 0, DELIVERED: 0, OPENED: 0, BOUNCED: 0, FAILED: 0}
      for (const email of baseEmails) {
        const s = toSearchableString(email.deliveryStatus).toUpperCase()
        if (s in counts) counts[s] = (counts[s] ?? 0) + 1
      }
      return answer(route, "getStats1", {
        totalCount: baseEmails.length,
        queuedCount: counts["QUEUED"],
        sentCount: counts["SENT"],
        deliveredCount: counts["DELIVERED"],
        openedCount: counts["OPENED"],
        bouncedCount: counts["BOUNCED"],
        failedCount: counts["FAILED"],
      })
    }
    if (method === "GET" && path === "/mail/inbox") {
      return answer(route, "findInbox", {content: [
        {id: 1, fromAddress: "lars@example.com", fromName: "Lars Mulder", senderUserId: 1, senderName: "Lars Mulder", subject: "Re: Your contribution",
          receivedAt: "2026-09-29T11:20:00.000Z", state: "NEW", automatic: false, answers: {emailId: 800, emailType: "email.contribution-reminder"}},
        {id: 2, fromAddress: "info@sponsor.example", subject: "Partnership question", toAddress: "partners@esa-blueshell.nl",
          receivedAt: "2026-09-27T10:02:00.000Z", state: "NEW", automatic: false},
      ], page: {size: 50, number: 0, totalElements: 2, totalPages: 1}})
    }
    if (method === "GET" && path === "/mail/inbox/counts") {
      return answer(route, "findInboxCounts", {new: 2, oldestNewAt: "2026-09-27T10:02:00.000Z", done: 0, automatic: 0})
    }
    const conversationOf = path.match(/^\/mail\/inbox\/(\d+)(?:\/(reply|handled))?$/)
    if (conversationOf) {
      const replied = conversationOf[2] === "reply"
      const handled = conversationOf[2] === "handled"
      const message = {id: Number(conversationOf[1]), fromAddress: "lars@example.com", fromName: "Lars Mulder", senderUserId: 1, senderName: "Lars Mulder",
        subject: "Re: Your contribution", receivedAt: "2026-09-29T11:20:00.000Z", automatic: false,
        answers: {emailId: 800, emailType: "email.contribution-reminder"},
        state: (replied ? "REPLIED" : handled ? "HANDLED" : "NEW") as "NEW" | "REPLIED" | "HANDLED",
        ...(replied || handled ? {handledBy: 1, handledByName: "Mock User", handledAt: "2026-10-01T10:00:00.000Z"} : {})}
      const items = [
        {kind: "SENT" as const, at: "2026-09-29T09:40:00.000Z", subject: "Your contribution", emailId: 800},
        {kind: "RECEIVED" as const, at: "2026-09-29T11:20:00.000Z", subject: "Re: Your contribution", body: "I already paid. Do I still need to do anything?",
          fromAddress: "lars@example.com", inboxMessageId: message.id},
        ...(replied ? [{kind: "REPLY" as const, at: "2026-10-01T10:00:00.000Z", body: (request.postDataJSON() as {message: string}).message,
          inboxMessageId: message.id, writtenByName: "Mock User"}] : []),
      ]
      const earlier = [{kind: "SENT" as const, at: "2026-09-21T10:00:00.000Z", subject: "Welcome to Blueshell", emailId: 801}]
      const operation = replied ? "replyToMessage" : handled ? "markMessageHandled" : "findConversation"
      return answer(route, operation, {message, items, earlier})
    }
    if (method === "GET" && path === "/mail/audiences") {
      return answer(route, "findAudiences", [{key: "ACTIVE_MEMBERS:4", label: "Active members 2026-2027"}])
    }
    if (method === "GET" && path === "/mail/reply-to") {
      return answer(route, "findReplyToOptions", ["board@esa-blueshell.nl", "mock-user@example.com"])
    }
    if (method === "POST" && path === "/mail/reach") {
      const {to} = request.postDataJSON() as {to: unknown[]}
      return answer(route, "findReach", {recipients: to.length === 0 ? 0 : 217, withoutEmail: to.length === 0 ? 0 : 3})
    }
    if (method === "POST" && (path === "/mail/send" || path === "/mail/test")) {
      return answer(route, path === "/mail/send" ? "sendWrittenEmail" : "sendTestEmail", {sent: path === "/mail/send" ? 217 : 1})
    }
    if (method === "POST" && path === "/management/emails/render") {
      const {subject, message} = request.postDataJSON() as {subject: string; message: string}
      return answer(route, "render", {subject, html: `<html><body><h1>${subject}</h1><p>${message}</p></body></html>`})
    }
    if (method === "GET" && /^\/management\/emails\/\d+$/.test(path)) {
      const id = Number(path.split("/")[3])
      const email = baseEmails.find((candidate) => candidate.id === id)
      if (!email) return fulfillJson(route, {message: "Not found"}, 404)
      return answer(route, "findEmail", {email, resends: baseEmails.filter((one) => one.resentFromId === id)})
    }
    if (method === "POST" && /^\/management\/emails\/\d+\/resend$/.test(path)) {
      const id = Number(path.split("/")[3])
      const email = baseEmails.find((candidate) => candidate.id === id)
      if (!email) return fulfillJson(route, {message: "Not found"}, 404)
      const made = {...email, id: Math.max(...baseEmails.map((one) => Number(one.id ?? 0))) + 1, deliveryStatus: "QUEUED" as const,
        sentAt: null, deliveredAt: null, openedAt: null, errorType: null, errorReason: null, attempts: 0, resentFromId: id}
      baseEmails.unshift(made)
      return answer(route, "resend", made)
    }
    if (method === "GET" && /^\/management\/emails\/\d+\/preview$/.test(path)) {
      const id = Number(path.replace(/\D+/g, ""))
      const email = baseEmails.find((candidate) => candidate.id === id)
      if (!email || !email.previewable) {
        return fulfillJson(route, {message: "No stored body"}, 404)
      }
      // As the api answers: already rendered, and already stripped of its urls.
      return answer(route, "previewSentEmail", {
        subject: email.subject ?? "",
        html: `<html><body><p>Hello ${email.recipientName}</p><a href="">Activate your account</a></body></html>`,
        recipientEmail: email.recipientEmail ?? "",
        recipientName: email.recipientName ?? "",
        linksRedacted: true,
      })
    }

    if (method === "GET" && path === "/management/emails") {
      const page = Number(url.searchParams.get("page") ?? "0")
      const size = Number(url.searchParams.get("size") ?? "50")
      const deliveryStatus = (url.searchParams.get("deliveryStatus") ?? "").trim().toUpperCase()
      const search = (url.searchParams.get("search") ?? "").trim().toLowerCase()

      let filtered = [...baseEmails]

      if (deliveryStatus && deliveryStatus !== "ALL") {
        filtered = filtered.filter((email) => toSearchableString(email.deliveryStatus).toUpperCase() === deliveryStatus)
      }

      if (search) {
        filtered = filtered.filter((email) => {
          const haystack = [
            toSearchableString(email.recipientEmail),
            toSearchableString(email.subject),
            toSearchableString(email.recipientName),
          ].join(" ").toLowerCase()
          return haystack.includes(search)
        })
      }

      filtered.sort((a, b) => Number(b.id ?? 0) - Number(a.id ?? 0))

      const safePage = Number.isFinite(page) && page >= 0 ? page : 0
      const safeSize = Number.isFinite(size) && size > 0 ? size : 50
      const totalElements = filtered.length
      const totalPages = Math.max(1, Math.ceil(totalElements / safeSize))
      const start = safePage * safeSize
      const content = filtered.slice(start, start + safeSize)

      return answer(route, "list1", {
        content,
        page: {
          number: safePage,
          size: safeSize,
          totalElements,
          totalPages,
        },
      })
    }
    if (method === "POST" && /^\/management\/emails\/\d+\/retry$/.test(path)) {
      const rawId = path.split("/")[3]
      const id = Number(rawId)
      const index = baseEmails.findIndex((email) => Number(email.id) === id)
      const existing = index >= 0 ? baseEmails[index] : undefined
      if (existing == null) {
        return fulfillJson(route, {detail: "Not found"}, 404)
      }
      const retried: Wire<Email> = {
        ...existing,
        deliveryStatus: "SENT",
        errorType: null,
        errorReason: null,
      }
      baseEmails.splice(index, 1, retried)
      return answer(route, "retry1", retried)
    }
    if (method === "GET" && path === "/signup/session") {
      if (!signupInProgress) return fulfillJson(route, {status: 404, detail: "Invalid or expired recovery token.", code: "RecoveryTokenUnusable"}, 404)
      return answer(route, "resumeSignup", signupInProgress)
    }
    if (method === "POST" && path === "/signup") {
      const payload = route.request().postDataJSON() as Wire<CreateUserRequest>
      const profile = payload.memberProfile
      signupInProgress = {
        userId: 9999,
        email: payload.email,
        username: payload.username,
        initials: payload.initials,
        firstName: payload.firstName,
        prefix: payload.prefix ?? null,
        lastName: payload.lastName,
        discord: payload.discord,
        phoneNumber: payload.phoneNumber,
        newsletter: payload.newsletter,
        photoConsent: payload.photoConsent === true,
        emailConfirmed: false,
        conditionsAccepted: false,
        memberProfile: {
          dateOfBirth: profile?.dateOfBirth ?? null,
          studentNumber: profile?.studentNumber ?? null,
          gender: profile?.gender ?? null,
          nationality: profile?.nationality ?? "NL",
          bhv: profile?.bhv === true,
          ehbo: profile?.ehbo === true,
          nameOnRosters: profile?.nameOnRosters === true,
        },
        address: null,
      }
      const applicant = aUser({
        id: 9999,
        username: payload.username,
        email: payload.email,
        discord: "",
        phoneNumber: "",
        newsletter: true,
        photoConsent: false,
        roles: ["GUEST"],
        enabled: false,
        version: 0,
      })
      baseUsers.push(applicant)
      return answer(route, "signUp", {
        userId: applicant.id,
        email: applicant.email,
        signupToken: "e2e-selector.e2e-verifier",
        expiresAt: "2099-01-01T00:00:00.000Z",
      }, 201)
    }
    if (method === "POST" && path === "/signup/address") {
      const body = route.request().postDataJSON() as Wire<SignupAddressRequest>
      if (signupInProgress) signupInProgress.address = body
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "POST" && path === "/signup/apply") {
      if (signupInProgress) signupInProgress.conditionsAccepted = true
      return answer(route, "apply", {emailConfirmed: false, membershipStarted: false})
    }
    if (method === "PATCH" && path === "/signup/details") {
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "PATCH" && path === "/signup/email") {
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "POST" && path === "/users") {
      return answer(route, "createUser", aUser({id: 999, username: "new-user", email: "new@example.com", discord: "", phoneNumber: "", newsletter: true, photoConsent: false, roles: ["GUEST"], enabled: false, version: 0}))
    }
    if (method === "PUT" && path.endsWith("/approve")) {
      return answer(route, "approveEvent", {...baseEvents[0], approved: true})
    }
    if (method === "DELETE" && /\/events\/\d+$/.test(path)) {
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "GET" && /\/events\/\d+\/banners$/.test(path)) {
      return fulfillJson(route, {}, 404)
    }
    if (method === "POST" && path === "/events") {
      // The request's banner, form and roles are written differently from how they are answered.
      const {banner: _banner, signUpForm: _form, pingedRoles: _roles, ...fields} =
        route.request().postDataJSON() as Wire<CreateEventRequest>
      return answer(route, "createEvent", {...baseEvents[0]!, ...fields, id: 501}, 201)
    }
    if (method === "POST" && path === "/events/banners") {
      return answer(route, "uploadEventBanner", {
        id: 77, name: "banner.png", path: "files/banner.png", mediaType: "image/png", type: "EVENT_BANNER",
        createdAt: "2025-01-01T00:00:00Z", updatedAt: "2025-01-01T00:00:00Z", version: 0,
      })
    }
    if (method === "POST" && /^\/recovery\/users\/\d+\/resend\/recovery$/.test(path)) {
      return route.fulfill({status: 204, contentType: "application/json", body: ""})
    }

    if (method === "POST" && /\/recovery\/user\/activate\/resend\//.test(path)) {
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "POST" && /\/recovery\/password\/reset\//.test(path)) {
      return route.fulfill({status: 204, body: ""})
    }

    if (method === "GET" && path === "/csrf") {
      return answer(route, "csrf", {token: "e2e-csrf-token"})
    }
    // No bot in the stand-in: the committee form's Discord section stays hidden.
    if (method === "GET" && (path === "/management/discord/roles" || path === "/management/discord/channels")) {
      return answer(route, path.endsWith("roles") ? "listKeptRoles" : "listKeptChannels", [])
    }
    if (method === "GET" && /^\/management\/discord\/games\/\w+\/access$/.test(path)) {
      return answer(route, "findGameAccess", {policy: {everyone: "READ", members: "WRITE"}, channels: []})
    }
    if (method === "GET" && /^\/management\/discord\/channels\/\w+\/access$/.test(path)) {
      return answer(route, "findChannelAccess", {kept: {everyone: "READ", members: "WRITE"}, actual: {everyone: "READ", members: "WRITE"}, differs: false})
    }
    if (method === "GET" && /^\/management\/(committees|teams)\/\d+\/discord$/.test(path)) {
      return answer(route, path.includes("/teams/") ? "findTeamDiscord" : "findCommitteeDiscord", {available: false, channels: []})
    }
    if (method === "GET" && path === "/discord/roles") {
      return answer(route, "listDiscordRoles", [])
    }
    if (method === "GET" && path === "/recovery/pending-activations") {
      return answer(route, "pendingActivations", {activations: []})
    }
    if (method === "POST" && path === "/recovery/user/activate") {
      return answer(route, "userActivate", {membershipStarted: false})
    }
    if (method === "DELETE" && /^\/events\/signups\/\d+$/.test(path)) {
      return route.fulfill({status: 204, contentType: "application/json", body: ""})
    }
    const profileOf = /^\/users\/(\d+)\/memberProfiles$/.exec(path)
    if (method === "GET" && profileOf) {
      return answer(route, "findMemberProfileByUserId", {
        id: Number(profileOf[1]), userId: Number(profileOf[1]), bhv: false, ehbo: false, nameOnRosters: true,
        createdAt: "2024-01-01T00:00:00.000Z", updatedAt: "2024-01-01T00:00:00.000Z", version: 0,
      })
    }
    const addressId = /^\/addresses\/(\d+)$/.exec(path)
    if (method === "GET" && addressId) {
      const id = Number(addressId[1])
      // The login cookie names address 10 as the member's own, which the address list does not hold.
      const address = baseAddresses.find(one => one.id === id)
        ?? (id === 10 ? anAddress({id, userId: 1, street: "Main", houseNumber: "1", zipCode: "1234AB", city: "Enschede", country: "NL"}) : null)
      if (!address) return fulfillJson(route, {title: "Not Found", status: 404}, 404)
      return answer(route, "findAddressById", address)
    }

    // An empty 200 let a new endpoint pass here and fail far from its cause.
    unmocked.get(page)?.push(`${method} ${path}`)
    return fulfillJson(route, {title: `The e2e stand-in has no answer for ${method} ${path}`, status: 501}, 501)
  }

  // 4173 is `vite preview`, 4174 the dev server the smoke project drives. Both proxy
  // /api, so an unglobbed call would reach a real socket rather than failing fast.
  const apiGlobs = [
    "http://localhost:4173/api/**",
    "http://127.0.0.1:4173/api/**",
    "http://localhost:4174/api/**",
    "http://127.0.0.1:4174/api/**",
  ]

  for (const glob of apiGlobs) {
    await page.route(glob, handleApiRoute)
  }
  // page.route never sees a socket, so without this one reaches whatever api the proxy finds.
  await page.routeWebSocket(/^ws:\/\/(localhost|127\.0\.0\.1):417[34]\/api\//, socket => {
    const path = new URL(socket.url()).pathname.slice(4)
    // No bot in the mocked api, which the api says by closing with 1013.
    if (path === "/discord/live/socket") return socket.close({code: 1013})
    unmocked.get(page)?.push(`WEBSOCKET ${path}`)
    return socket.close({code: 1011})
  })

  await page.route("https://discordapp.com/api/guilds/**/widget.json", async (route) => {
    return fulfillJson(route, {
      id: "324285132133629963",
      name: "ESA Blueshell",
      instant_invite: null,
      presence_count: 2,
      channels: [{id: "9", name: "AFK", position: 0}, {id: "1", name: "General", position: 1}],
      members: [
        {id: "0", username: "Emma", status: "online", avatar_url: "", channel_id: "1"},
        {id: "1", username: "Viktor", status: "idle", avatar_url: "", channel_id: "1"},
      ],
    } satisfies WidgetResponse)
  })

  await page.route("https://discord.com/api/v10/invites/**", async (route) => {
    return fulfillJson(route, {approximate_member_count: 40, approximate_presence_count: 2})
  })

  await page.route("https://www.google.com/maps/embed**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<html><body>Mock map</body></html>",
    })
  })
}

/**
 * Writes into the markdown editor, which holds the document itself rather than a value.
 *
 * `fill` on a contenteditable leaves what was already written where the editor's own handling
 * of the insert does not clear it, so the text is selected and typed over.
 */
export async function writeMarkdown(page: Page, label: string | Locator, text: string) {
  const editor = typeof label === "string" ? page.getByLabel(label) : label
  await editor.click()
  await page.keyboard.press("ControlOrMeta+a")
  await page.keyboard.type(text)
  // Read back selected, since a span shows its marks only while the selection touches it.
  // MarkdownFieldHelper in the system tests reads it the same way; change one, change the other.
  await page.keyboard.press("ControlOrMeta+a")
  await expect(editor).toContainText(text)
}
