import {Buffer} from "node:buffer"
import type {BrowserContext, Page, Route} from "@playwright/test"
import {
  COOKIE_CONSENT_STORAGE_KEY,
  encodeCookieConsentPayload,
} from "@/config/policies.ts"

type Fixtures = {
  users?: Array<Record<string, unknown>>
  deletedUsers?: Array<Record<string, unknown>>
  memberships?: Array<Record<string, unknown>>
  contributionPeriods?: Array<Record<string, unknown>>
  contributions?: Array<Record<string, unknown>>
  addresses?: Array<Record<string, unknown>>
  events?: Array<Record<string, unknown>>
  eventSignUps?: Array<Record<string, unknown>>
  eventDetailsById?: Record<string, Record<string, unknown>>
  eventSignUpsByEventId?: Record<string, Array<Record<string, unknown>>>
  committees?: Array<Record<string, unknown>>
  blogs?: Array<Record<string, unknown>>
  blogsById?: Record<string, Record<string, unknown>>
  blogStatusById?: Record<string, number>
  jobs?: Array<Record<string, unknown>>
  emails?: Array<Record<string, unknown>>
  cohortSubjects?: Array<Record<string, unknown>>
  cohortMembers?: Array<Record<string, unknown>>
  esportsPages?: Record<string, Record<string, unknown>>
  esportsSeasons?: Array<Record<string, unknown>>
  esportsTeams?: Array<Record<string, unknown>>
  esportsRoster?: Array<Record<string, unknown>>
  esportsGames?: Array<Record<string, unknown>>
  boards?: Array<Record<string, unknown>>
  cohortSubjectDetail?: Record<string, unknown>
}

/** What Brevo reports it holds, for the target catalogue page. */
const brevoTargets = [
  {system: "BREVO", externalId: "7", kind: "LIST", label: "Members 2025-2026", folderLabel: "Contribution periods", path: ["Brevo", "Contribution periods"], memberCount: 2, linkedCohortId: 1},
  {system: "BREVO", externalId: "33", kind: "LIST", label: "Web Cmte", folderLabel: "Committees", path: ["Brevo", "Committees"], memberCount: 1, linkedCohortId: 2},
  {system: "BREVO", externalId: "34", kind: "LIST", label: "Board", folderLabel: "Committees", path: ["Brevo", "Committees"], memberCount: 5, linkedCohortId: null},
  // Same name as the committee list above, filed somewhere else: only the path tells them apart.
  {system: "BREVO", externalId: "88", kind: "LIST", label: "Web Cmte", folderLabel: "Archive", path: ["Brevo", "Archive"], memberCount: 0, linkedCohortId: null},
  {system: "BREVO", externalId: "50", kind: "LIST", label: "Loose ends", folderLabel: null, path: ["Brevo"], memberCount: null, linkedCohortId: null},
]

/**
 * The games themselves, as their records hold them: what each is called, the address its page
 * answers to, and the art it is drawn with. The pages read every one of these from here.
 */
const esportsGames = [
  {game: "VALORANT", name: "Valorant", slug: "valorant", accent: "#ff4655", mark: "valorant.png", banner: null, intro: "Shooters, and plenty of them.", sortIndex: 1, fielded: true},
  {game: "CS2", name: "Counter-Strike 2", slug: "counter-strike-2", accent: "#e8842a", mark: "cs2.png", banner: null, intro: "Those sweet headshots.", sortIndex: 2, fielded: true},
  {game: "LEAGUE_OF_LEGENDS", name: "League of Legends", slug: "league-of-legends", accent: "#c8963c", mark: "league.png", banner: null, intro: "A special place.", sortIndex: 3, fielded: true},
  {game: "ROCKET_LEAGUE", name: "Rocket League", slug: "rocketleague", accent: "#1183d6", mark: "rocketleague.png", banner: null, intro: "Football, with rocket cars.", sortIndex: 4, fielded: true},
  {game: "GEOGUESSR", name: "GeoGuessr", slug: "geoguessr", accent: "#6cbf3f", mark: "geoguessrlogo.webp", banner: null, intro: "Guessing where.", sortIndex: 5, fielded: true},
  // No accent or mark has ever been written for Trackmania: it reads on the island's own blue.
  {game: "TRACKMANIA", name: "Trackmania", slug: "trackmania", accent: null, mark: null, banner: null, intro: "Driving, fast.", sortIndex: 6, fielded: true},
  {game: "CSGO", name: "CS:GO", slug: "counter-strike-global-offensive", accent: "#e8842a", mark: "cs2.png", banner: null, intro: null, sortIndex: 7, fielded: false},
]

/** Two seasons of one game, so a page has both a roster and something to switch to. */
const esportsSeasons = [
  {id: 20, name: "Autumn 2025/26", startDate: "2025-09-01", endDate: "2026-01-31"},
  {id: 19, name: "Spring 2024/25", startDate: "2025-02-01", endDate: "2025-08-31"},
]

const esportsPageBySeason: Record<string, Record<string, unknown>> = {
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
const boardFixtures = [
  {
    id: 9,
    name: "9th Board",
    candidate: "9th Board",
    startDate: "2025-09-01",
    endDate: "2026-08-31",
    image: "board9/board9.jpg",
    version: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    members: [
      {
        id: 91, boardId: 9, userId: 1, role: "Chair", name: "Emma Dokter",
        description: "Chairing the ninth board.", image: "board9/Emma.jpg",
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
      {
        id: 92, boardId: 9, userId: null, role: "Secretary", name: "Viktor Petrov",
        description: null, image: null,
        startDate: "2025-09-01", endDate: "2026-08-31", version: 0,
        createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z",
      },
    ],
  },
  {
    id: 1,
    name: "1st Board",
    candidate: "1st Board",
    startDate: "2017-09-01",
    endDate: "2018-08-31",
    image: null,
    version: 0,
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    members: [
      {
        id: 11, boardId: 1, userId: null, role: "Chairman", name: "Thijs Lieverse",
        description: null, image: null,
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
  ROSTER_ICON: "roster-icons",
  GAME_MARK: "game-marks",
  GAME_BANNER: "game-banners",
}

type MockImage = {
  url: string
  /** Where it is stored, which is what a save points at to put it on a record. */
  path: string
  width: number
  height: number
  renditions: Array<{url: string; width: number}>
}

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(data),
  })
}

async function loginAsRoles(context: BrowserContext, roles: string[]) {
  const loginCookie = encodeURIComponent(JSON.stringify({
    userId: 1,
    username: "mock-user",
    token: "",
    roles,
    expiration: Date.now() + 1000 * 60 * 60 * 24,
    addressId: 10,
  }))

  await context.addCookies([
    {
      name: "login",
      value: loginCookie,
      url: "http://127.0.0.1:4173",
    },
  ])
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

export async function installApiMocks(page: Page, fixtures: Fixtures = {}) {
  // Seasons written down during the test. The api shows a season that was asked for even
  // where the game fielded nobody in it, and these are exactly those seasons.
  const written = new Map<number, Record<string, unknown>>()
  // Teams written down and fielded during the test, so a page asked again reports them the
  // way the api would rather than forgetting they were added.
  const teamsMade: Array<Record<string, unknown>> = []
  /** Games added during the test, which every read then reports as one of the games. */
  const gamesMade: Array<Record<string, string | number | boolean | null>> = []
  /** Games corrected during the test, which every read then reports as corrected. */
  const gamesEdited = new Map<string, Record<string, unknown>>()
  /** Games removed during the test, which the reads then leave out. */
  const gamesGone = new Set<string>()
  const fieldedNow: Array<{seasonId: number; teamId: number; members: Array<Record<string, unknown>>}> = []
  let nextTeamId = 70
  let nextEntryId = 200
  /** Seasons and fieldings taken away during the test, which the reads then leave out. */
  const gone = new Set<number>()
  const dropped: Array<{seasonId: number; teamId: number}> = []
  /** Teams renamed or removed during the test, which the reads then reflect. */
  const renamed = new Map<number, {name: unknown; banner: unknown}>()
  const goneTeams = new Set<number>()
  /**
   * The uploaded images, held as the api holds them: a reference per owner rather than bytes.
   * Each upload takes the next file id, so a replacement is visibly a different url.
   *
   * Storing is separate from applying, exactly as the api has it: a picture goes into `stored`
   * when it is uploaded and reaches a poster or an icon only when a save names its path.
   */
  const teamBanners = new Map<number, MockImage>()
  const icons = new Map<number, MockImage>()
  const stored = new Map<string, MockImage>()
  let nextFileId = 500
  /**
   * An image as the api describes one. The size is that of the picture actually served below,
   * so a page reserving an image's space reserves the right amount of it.
   */
  const nextImage = (directory: string): MockImage => {
    nextFileId += 1
    const at = `${directory}/mock-${nextFileId}.webp`
    return {
      url: `/files/public/${at}`,
      path: at,
      width: 640,
      height: 360,
      // The widths a picture of this size is stored at, so a page has a srcset to compose.
      renditions: [320, 640].map(width => ({url: `/files/public/${directory}/mock-${nextFileId}-${width}.webp`, width})),
    }
  }

  /** The one endpoint that stores a picture. What it ends up on is a later save's business. */
  const storePicture = (kind: string): MockImage => {
    const made = nextImage(DIRECTORY_OF[kind] ?? "team-banners")
    stored.set(made.path, made)
    return made
  }

  /** The picture a save names, or nothing where the save names none. */
  const pictureNamed = (picture: unknown): MockImage | null =>
    (typeof picture === "string" ? stored.get(picture) ?? null : null)
  /**
   * The line-up of the seeded team, as the admin reads and writes it. The public page builds
   * that team's members from it, so an edit here is visible there — which is the whole of
   * what "the slice shows the change" means.
   */
  const roster: Array<Record<string, unknown>> = [
    {id: 21, teamId: 1, seasonId: 20, role: "PLAYER", handle: "AriosFury", displayName: "Viktor Petrov", userId: 1, sortIndex: 0, roleTitle: "Captain", description: "Holds the **middle** together."},
    {id: 22, teamId: 1, seasonId: 20, role: "PLAYER", handle: "Loafine", displayName: null, userId: null, sortIndex: 1, roleTitle: null, description: null},
    {id: 23, teamId: 1, seasonId: 20, role: "SUBSTITUTE", handle: "Blackout", displayName: null, userId: null, sortIndex: 2, roleTitle: null, description: null},
    // The team that played a season ago and has a line-up worth carrying across.
    {id: 11, teamId: 3, seasonId: 19, role: "PLAYER", handle: "AriosFury", displayName: "Viktor Petrov", userId: 1, sortIndex: 0, roleTitle: null, description: null},
    {id: 12, teamId: 3, seasonId: 19, role: "SUBSTITUTE", handle: "Blackout", displayName: null, userId: null, sortIndex: 1, roleTitle: null, description: null},
  ]
  const asMember = (entry: Record<string, unknown>) => ({
    role: entry.role,
    handle: entry.handle,
    name: entry.userId != null ? entry.displayName : null,
    roleTitle: entry.roleTitle ?? null,
    description: entry.description ?? null,
    icon: icons.get(Number(entry.id)) ?? null,
  })

  await page.addInitScript((params: {cookieConsentStorageKey: string; cookieConsentPayload: string}) => {
    localStorage.setItem(params.cookieConsentStorageKey, params.cookieConsentPayload)
    if (localStorage.getItem("esa-blueshell.nl:darkMode") == null) {
      localStorage.setItem("esa-blueshell.nl:darkMode", "false")
    }
  }, {
    cookieConsentStorageKey: COOKIE_CONSENT_STORAGE_KEY,
    cookieConsentPayload: encodeCookieConsentPayload(),
  })

  const baseUsers = fixtures.users ?? [
    {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: true, roles: ["MEMBER"]},
    {id: 2, fullName: "Viktor Petrov", username: "ariosfury", enabled: false, roles: ["USER"]},
  ]

  const baseDeletedUsers = fixtures.deletedUsers ?? [
    {
      id: 9,
      fullName: "Deleted User",
      username: "deleted-user",
      email: "deleted@example.com",
      enabled: false,
    },
  ]

  const baseMemberships = fixtures.memberships ?? [
    {id: 100, userId: 1, memberType: "REGULAR", startDate: "2025-01-01"},
  ]

  const basePeriods = fixtures.contributionPeriods ?? [
    {id: 200, startDate: "2025-01-01", endDate: "2025-06-30", halfYearFee: 10, fullYearFee: 20, alumniFee: 5},
    {id: 201, startDate: "2025-07-01", endDate: "2025-12-31", halfYearFee: 10, fullYearFee: 20, alumniFee: 5},
  ]

  const baseContributions = fixtures.contributions ?? [
    {id: 300, userId: 1, contributionPeriodId: 201},
  ]

  const baseAddresses = fixtures.addresses ?? [
    {id: 400, userId: 1, street: "Main", city: "Enschede", zipcode: "1234AB", countryCode: "NL"},
  ]

  const baseEvents = fixtures.events ?? [
    {
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
      banner: false,
    },
  ]

  const baseEventSignUps = fixtures.eventSignUps ?? [
    {id: 600, eventId: 500, userId: 1},
  ]

  const baseCommittees = fixtures.committees ?? [
    {id: 900, name: "Events Committee"},
  ]

  const baseBlogs = fixtures.blogs ?? [
    {
      id: 1,
      title: "Mock Newsletter",
      publishedAt: "2025-01-01T12:00:00.000Z",
      html: "<h1>Mock Newsletter</h1><p>Welcome to Blueshell.</p>",
    },
  ]

  const blogsById = fixtures.blogsById ?? Object.fromEntries(
    baseBlogs
      .filter((blog) => blog.id != null)
      .map((blog) => [String(blog.id), blog]),
  )

  const baseJobs = fixtures.jobs ?? [
    {
      id: 700,
      jobType: "SYNC_DISCORD",
      status: "FAILED",
      attempts: 1,
      payload: "{\"scope\":\"members\"}",
      errorType: "RuntimeException",
      errorReason: "Temporary failure",
      queuedAt: "2025-01-01T12:00:00.000Z",
      startedAt: "2025-01-01T12:00:10.000Z",
      finishedAt: "2025-01-01T12:00:11.000Z",
    },
  ]

  const baseEmails = fixtures.emails ?? [
    {
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
    },
  ]

  const toSearchableString = (value: unknown): string => {
    if (typeof value === "string") return value
    if (typeof value === "number") return String(value)
    return ""
  }

  const jobCategory = (job: Record<string, unknown>): string => {
    const raw = toSearchableString(job.category).trim().toLowerCase()
    if (raw) return raw

    const type = toSearchableString(job.jobType).trim().toLowerCase()
    if (!type) return "other"
    const separatorPositions = [type.indexOf("."), type.indexOf("_"), type.indexOf("-")].filter((idx) => idx > 0)
    if (separatorPositions.length === 0) return type
    return type.slice(0, Math.min(...separatorPositions))
  }

  const matchesSearch = (job: Record<string, unknown>, query: string): boolean => {
    const relatedEntities = Array.isArray(job.relatedEntities)
      ? job.relatedEntities
        .map((value) => {
          if (value == null || typeof value !== "object") return ""
          return toSearchableString((value as Record<string, unknown>).label)
        })
        .join(" ")
      : ""

    const haystack = [
      toSearchableString(job.summary),
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

  const parseCookieLogin = (cookieHeader: string): {userId: number; roles: string[]} | null => {
    try {
      const match = cookieHeader.match(/(?:^|;\s*)login=([^;]+)/)
      if (!match) return null
      const data = JSON.parse(decodeURIComponent(match[1]))
      const userId = Number(data?.userId)
      const roles = Array.isArray(data?.roles) ? (data.roles as string[]) : null
      return Number.isFinite(userId) && roles ? {userId, roles} : null
    } catch {
      return null
    }
  }

  const handleApiRoute = async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    const path = url.pathname.startsWith("/api/")
      ? url.pathname.slice(4)
      : url.pathname

    const cookieLogin = parseCookieLogin(request.headers()["cookie"] ?? "")

    if (method === "GET" && path === "/users") {
      return fulfillJson(route, {content: baseUsers})
    }
    if (method === "GET" && path === "/users/deleted") {
      return fulfillJson(route, {content: baseDeletedUsers})
    }
    if (method === "GET" && /^\/users\/\d+$/.test(path)) {
      const id = Number(path.split("/").at(-1))
      const user = baseUsers.find((candidate) => Number(candidate.id) === id)
      // Reflect the logged-in user's actual roles so App.vue doesn't overwrite the store with stale mock data
      const roles = (cookieLogin?.userId === id ? cookieLogin.roles : null) ?? (user?.roles as string[] | undefined) ?? ["MEMBER"]
      if (user != null) {
        return fulfillJson(route, {...user, roles})
      }
      return fulfillJson(route, {id, roles})
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
      return fulfillJson(route, {}, 204)
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
      return fulfillJson(route, {}, 204)
    }
    if (method === "GET" && path === "/memberships") {
      return fulfillJson(route, baseMemberships)
    }
    if (method === "GET" && path === "/contributionPeriods") {
      return fulfillJson(route, basePeriods)
    }
    if (method === "GET" && /\/contributionPeriods\/\d+\/contributions$/.test(path)) {
      return fulfillJson(route, baseContributions)
    }
    if (method === "GET" && path === "/addresses") {
      return fulfillJson(route, baseAddresses)
    }
    if (method === "GET" && path === "/events") {
      return fulfillJson(route, {content: baseEvents})
    }
    if (method === "GET" && path === "/events/signups") {
      return fulfillJson(route, baseEventSignUps)
    }
    if (method === "GET" && (path === "/events/signups/byAccessToken" || path.startsWith("/events/signups/byAccessToken/"))) {
      return fulfillJson(route, baseEventSignUps)
    }
    if (method === "GET" && /^\/events\/\d+\/signups$/.test(path)) {
      const eventId = path.split("/")[2]
      return fulfillJson(route, fixtures.eventSignUpsByEventId?.[eventId] ?? [])
    }
    if (method === "GET" && /^\/events\/\d+$/.test(path)) {
      const eventId = Number(path.split("/").at(-1))
      const detail = fixtures.eventDetailsById?.[String(eventId)]
        ?? baseEvents.find((candidate) => Number(candidate.id) === eventId)
        ?? {id: eventId}
      return fulfillJson(route, detail)
    }
    if (method === "GET" && path === "/committees") {
      return fulfillJson(route, baseCommittees)
    }
    if (method === "GET" && path === "/committeeMembers/committees") {
      return fulfillJson(route, baseCommittees)
    }
    if (method === "GET" && path === "/blogs") {
      return fulfillJson(route, baseBlogs)
    }
    if (method === "GET" && /^\/blogs\/[^/]+$/.test(path)) {
      const id = path.split("/").at(-1) ?? ""
      const forcedStatus = fixtures.blogStatusById?.[id]
      if (forcedStatus != null && forcedStatus !== 200) {
        return fulfillJson(route, {status: forcedStatus, detail: "Blog request failed"}, forcedStatus)
      }
      const blog = blogsById[id]
      if (blog != null) {
        return fulfillJson(route, blog)
      }
      return fulfillJson(route, {status: 404, detail: "Blog not found"}, 404)
    }
    if (method === "GET" && path === "/management/jobs/stats") {
      const counts: Record<string, number> = {SUCCESS: 0, FAILED: 0, DEAD: 0, QUEUED: 0, RUNNING: 0}
      for (const job of baseJobs) {
        const s = toSearchableString(job.status).toUpperCase()
        if (s in counts) counts[s] = (counts[s] ?? 0) + 1
      }
      return fulfillJson(route, {
        totalCount: baseJobs.length,
        successCount: counts["SUCCESS"],
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
      return fulfillJson(route, [
        {type: "contact.sync-all", payloadFields: []},
        {type: "contact.sync", payloadFields: [{name: "userId", type: "Long", required: true}]},
        {type: "email.recovery", payloadFields: [
          {name: "userId", type: "Long", required: true},
          {name: "token", type: "String", required: false},
        ]},
      ])
    }
    if (method === "POST" && path === "/management/jobs/enqueue") {
      const body = (route.request().postDataJSON() ?? {}) as {jobType?: string}
      const enqueued = {
        id: 9000 + baseJobs.length,
        jobType: body.jobType ?? "contact.sync",
        status: "QUEUED",
        attempts: 1,
      }
      baseJobs.unshift(enqueued)
      return fulfillJson(route, enqueued)
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

      return fulfillJson(route, {
        content,
        page: {
          number: safePage,
          size: safeSize,
          totalElements,
          totalPages,
        },
      })
    }
    // The external target catalogue behind the Brevo targets page. Brevo is the one system
    // that can file a target elsewhere, so its descriptor is the one carrying MOVE.
    if (method === "GET" && path === "/management/cohort-targets/systems") {
      return fulfillJson(route, [
        {
          system: "BREVO",
          kind: "LIST",
          systemLabel: "Brevo",
          targetLabel: "Brevo list",
          idLabel: "List id",
          folderLabel: "Folder",
          capabilities: ["CATALOG", "CREATE", "MOVE"],
        },
      ])
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO/folders") {
      // Includes a folder holding nothing, which is exactly where a target tends to head.
      return fulfillJson(route, ["Committees", "Contribution periods", "Archive"])
    }
    if (method === "PUT" && path === "/management/cohort-targets/BREVO/folder") {
      const body = request.postDataJSON() as {externalIds: string[]; folder: string}
      // `99` stands for a target the catalogue still lists but the system no longer has.
      const gone = body.externalIds.filter((id) => id === "99")
      if (gone.length) {
        return fulfillJson(route, {
          type: "about:blank",
          title: "Conflict",
          status: 409,
          detail: "The selection no longer matches the current data.",
          errors: [{
            objectName: "BulkMoveTargetsRequest",
            field: "externalIds",
            code: "UnknownTargetIds",
            message: `${gone.length} of the selected targets no longer exist in BREVO.`,
            refs: gone,
          }],
        }, 409)
      }
      const moved = body.externalIds.map((id) => ({
        system: "BREVO",
        externalId: id,
        kind: "LIST",
        label: brevoTargets.find((t) => t.externalId === id)?.label ?? `List ${id}`,
        folderLabel: body.folder,
        path: ["Brevo", body.folder].filter(Boolean),
        memberCount: brevoTargets.find((t) => t.externalId === id)?.memberCount ?? null,
        linkedCohortId: brevoTargets.find((t) => t.externalId === id)?.linkedCohortId ?? null,
      }))
      return fulfillJson(route, {moved, failed: []})
    }
    if (method === "GET" && path === "/management/cohort-targets/BREVO") {
      return fulfillJson(route, brevoTargets)
    }
    // Legacy /management/cohorts list (still used by CohortPicker until
    // the engine is fully on subjects).
    if (method === "GET" && path === "/management/cohorts") {
      return fulfillJson(route, [
        {id: 1, system: "BREVO", kind: "LIST", label: "Members 2025-2026", memberCount: 2, externalId: "7", folder: "Periods"},
        {id: 2, system: "BREVO", kind: "LIST", label: "Web Cmte", memberCount: 1, externalId: "33", folder: "Committees"},
      ])
    }
    if (method === "GET" && path === "/boards") {
      return fulfillJson(route, fixtures.boards ?? boardFixtures)
    }
    if (method === "GET" && /^\/boards\/\d+$/.test(path)) {
      const id = Number(path.split("/")[2])
      const board = (fixtures.boards ?? boardFixtures).find((b) => b.id === id)
      return fulfillJson(route, board ?? {}, board ? 200 : 404)
    }
    if (method === "POST" && /^\/boards\/\d+\/members$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      return fulfillJson(route, {id: 99, boardId: Number(path.split("/")[2]), userId: body.userId ?? null, role: body.role, name: body.displayName ?? null, description: body.description ?? null, image: body.image ?? null, startDate: body.startDate, endDate: body.endDate ?? null, version: 0, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z"}, 201)
    }
    if (method === "PUT" && /^\/boards\/\d+\/members\/\d+\/member$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      return fulfillJson(route, {id: 92, boardId: 9, userId: body.userId ?? null, role: "Secretary", name: "Viktor Petrov", description: null, image: null, startDate: "2025-09-01", endDate: "2026-08-31", version: 1, createdAt: "2026-01-01T00:00:00Z", updatedAt: "2026-01-01T00:00:00Z"})
    }
    // A game added during a test is one of the games from then on, the way the api has it.
    if (method === "POST" && path === "/esports/games") {
      const body = JSON.parse(request.postData() ?? "{}") as {name?: string; slug?: string}
      const slug = String(body.slug ?? "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "")
      const known = [...(fixtures.esportsGames ?? esportsGames), ...gamesMade]
      const held = known.find(one => one.slug === slug)
      if (held) {
        return fulfillJson(route, {detail: `${held.name} already answers to '${slug}'`}, 409)
      }
      const code = String(body.name ?? "").toUpperCase().replace(/[^A-Z0-9]+/g, "_").replace(/^_+|_+$/g, "")
      const made = {
        game: code, name: String(body.name ?? ""), slug, accent: null, mark: null, banner: null,
        intro: null, sortIndex: known.length + 1, fielded: true,
      }
      gamesMade.push(made)
      return fulfillJson(route, made, 201)
    }
    if (method === "GET" && /^\/esports\/games\/[A-Z0-9_]+\/contents$/.test(path)) {
      const code = path.split("/")[3] as string
      const held = [...(fixtures.esportsTeams ?? []), ...teamsMade].filter(one => one.game === code)
      const seeded = code === "VALORANT" && !fixtures.esportsTeams ? 2 : 0
      return fulfillJson(route, {teams: held.length + seeded, players: (held.length + seeded) * 3})
    }

    if (method === "DELETE" && /^\/esports\/games\/[A-Z0-9_]+$/.test(path)) {
      const code = path.split("/").pop() as string
      const known = [...(fixtures.esportsGames ?? esportsGames), ...gamesMade]
      const held = [...(fixtures.esportsTeams ?? []), ...teamsMade].filter(one => one.game === code)
      const seeded = code === "VALORANT" && !fixtures.esportsTeams ? 2 : 0
      if (held.length + seeded > 0) {
        const game = known.find(one => one.game === code)
        return fulfillJson(route, {
          detail: `${game?.name ?? code} holds ${held.length + seeded} teams and 6 roster places. `
            + "Mark it as no longer fielded instead, and everything it played stays readable.",
        }, 409)
      }
      gamesGone.add(code)
      return route.fulfill({status: 204, body: ""})
    }

    // A game corrected during a test reads corrected from then on.
    if (method === "PUT" && /^\/esports\/games\/[A-Z0-9_]+$/.test(path)) {
      const code = path.split("/").pop() as string
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const slug = String(body.slug ?? "").trim().toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-+|-+$/g, "")
      const known = [...(fixtures.esportsGames ?? esportsGames), ...gamesMade]
      const held = known.find(one => one.slug === slug && one.game !== code)
      if (held) {
        return fulfillJson(route, {detail: `${held.name} already answers to '${slug}'`}, 409)
      }
      const was = known.find(one => one.game === code)
      const now = {
        ...(was ?? {game: code}),
        name: body.name ?? was?.name ?? code,
        slug,
        intro: body.intro ?? null,
        accent: body.accent ?? null,
        mark: body.mark ?? null,
        // Named by where it is stored and answered as the picture itself, the way the api does.
        banner: pictureNamed(body.banner) ?? null,
        sortIndex: body.sortIndex ?? was?.sortIndex ?? 0,
        fielded: body.fielded ?? true,
      } as Record<string, unknown>
      gamesEdited.set(code, now)
      return fulfillJson(route, now)
    }
    // The api answers in the order the records put the games in, and so does this.
    if (method === "GET" && path === "/esports/games") {
      const all = [...(fixtures.esportsGames ?? esportsGames), ...gamesMade]
        .filter(one => !gamesGone.has(String(one.game)))
        .map(one => gamesEdited.get(String(one.game)) ?? one)
      return fulfillJson(route, all)
    }
    // [A-Z0-9_]+ rather than [A-Z_]+: a game's enum name can carry a digit, and
    // CS2 is one. With the digit excluded this route never matched, so every CS2
    // page in the suite silently rendered as having no teams.
    if (method === "GET" && /^\/esports\/games\/[A-Z0-9_]+$/.test(path)) {
      const requested = url.searchParams.get("seasonId")
      const fresh = requested != null ? written.get(Number(requested)) : undefined
      if (fresh) {
        // Nobody has been fielded in it yet, which is the answer rather than a reason to
        // show a different season's teams.
        return fulfillJson(route, {game: path.split("/").pop(), season: fresh, seasons: esportsSeasons, teams: []})
      }
      const page = fixtures.esportsPages?.[requested ?? "20"]
        ?? esportsPageBySeason[requested ?? "20"]
        ?? esportsPageBySeason["20"]
      const offered = (page.seasons as Array<{id: number}>).filter(one => !gone.has(one.id))
      // Teams fielded during this test belong to the page the same way the seeded ones do.
      const game = path.split("/").pop()
      const shownSeason = Number((page.season as {id: number} | undefined)?.id ?? requested ?? 20)
      const extra = fieldedNow
        .filter(one => one.seasonId === shownSeason)
        .map(one => ({one, team: teamsMade.find(made => made.id === one.teamId)
          ?? [{id: 3, game: "VALORANT", name: "BS Old Guard", banner: null}].find(known => known.id === one.teamId)}))
        .filter(row => row.team != null && row.team.game === game)
        .map(row => ({id: row.one.teamId, name: row.team!.name, banner: row.team!.banner ?? null, members: row.one.members}))
      const fieldsThis = game === "VALORANT" || game === "CS2"
      // The seeded team's members come from the same line-up the admin edits, so a change
      // made there is a change here.
      const stillFielded = (team: Record<string, unknown>) =>
        !dropped.some(one => one.seasonId === shownSeason && one.teamId === team.id)
        && !goneTeams.has(Number(team.id))
      const named = (team: Record<string, unknown>) => {
        const change = renamed.get(Number(team.id))
        return change ? {...team, name: change.name, banner: change.banner} : team
      }
      const seeded = (fieldsThis ? page.teams as Array<Record<string, unknown>> : []).filter(stillFielded).map(named).map(team => (
        team.id === 1
          ? {...team, members: roster
            .filter(one => one.teamId === 1 && one.seasonId === shownSeason)
            .sort((a, b) => Number(a.sortIndex) - Number(b.sortIndex))
            .map(asMember)}
          : team
      ))
      // A team's own picture and nothing else: the page draws it in the slice for that team,
      // and there is no wider banner for it to be resolved against.
      const teams = [...seeded, ...extra].map(team => ({
        ...team,
        banner: teamBanners.get(Number((team as {id: number}).id)) ?? null,
      }))
      return fulfillJson(route, {...page, game, seasons: offered, teams})
    }
    if (method === "POST" && path === "/esports/seasons") {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const season = {id: 41, ...body}
      written.set(41, season)
      return fulfillJson(route, season, 201)
    }
    if (method === "GET" && /^\/esports\/seasons\/\d+\/contents$/.test(path)) {
      const seasonId = Number(path.split("/")[3])
      const held = roster.filter(one => one.seasonId === seasonId)
      const teamsHeld = new Set(held.map(one => one.teamId))
      return fulfillJson(route, {teams: teamsHeld.size, players: held.length})
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
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      return fulfillJson(route, {id: Number(path.split("/").pop()), ...body})
    }
    if (method === "GET" && path === "/esports/seasons") {
      const all = [...(fixtures.esportsSeasons ?? esportsSeasons), ...written.values()]
      return fulfillJson(route, all.filter(one => !gone.has(Number((one as {id: number}).id))))
    }
    if (method === "POST" && path === "/files/images") {
      return fulfillJson(route, storePicture(url.searchParams.get("type") ?? ""), 201)
    }
    // A real image rather than an empty body: a url that resolves to nothing still sets an
    // `src`, so only an image that actually decodes proves the page is pointing at the api.
    if (method === "GET" && /^\/files\/public\/[^/]+\/[^/]+$/.test(path)) {
      return route.fulfill({
        status: 200,
        contentType: "image/webp",
        body: Buffer.from(
          "UklGRiIAAABXRUJQVlA4IBYAAAAwAQCdASoBAAEADsD+JaQAA3AAAAAA",
          "base64",
        ),
      })
    }
    if (method === "GET" && path === "/esports/teams") {
      const forGame = url.searchParams.get("game")
      const known = fixtures.esportsTeams ?? [
        {id: 1, game: "VALORANT", name: "BS Waterboarders", banner: null},
        {id: 2, game: "VALORANT", name: "BS SpicyWater", banner: null},
        {id: 3, game: "VALORANT", name: "BS Old Guard", banner: null},
      ]
      const all = [...known, ...teamsMade]
      return fulfillJson(route, forGame ? all.filter(team => team.game === forGame) : all)
    }
    if (method === "POST" && path === "/esports/teams") {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      nextTeamId += 1
      const banner = pictureNamed(body.banner)
      const team = {id: nextTeamId, game: body.game, name: body.name, banner: banner ?? null}
      teamsMade.push(team)
      if (banner) teamBanners.set(nextTeamId, banner)
      return fulfillJson(route, team, 201)
    }
    // The banner is part of this write, so a save with none takes the team's away — which is
    // what the picker's Remove means once the dialog around it is saved.
    if (method === "PUT" && /^\/esports\/teams\/\d+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const id = Number(path.split("/").pop())
      const banner = pictureNamed(body.banner)
      renamed.set(id, {name: body.name, banner: banner ?? null})
      if (banner) teamBanners.set(id, banner)
      else teamBanners.delete(id)
      return fulfillJson(route, {id, game: "VALORANT", name: body.name, banner: banner ?? null})
    }
    if (method === "DELETE" && /^\/esports\/teams\/\d+$/.test(path)) {
      goneTeams.add(Number(path.split("/").pop()))
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "GET" && /^\/esports\/teams\/\d+\/seasons$/.test(path)) {
      // Only the one team in these fixtures has a season behind it to carry from.
      const teamId = Number(path.split("/")[3])
      return fulfillJson(route, teamId === 3 ? [esportsSeasons[1]] : [])
    }
    if (method === "PUT" && /^\/esports\/seasons\/\d+\/teams\/\d+$/.test(path)) {
      const parts = path.split("/")
      const seasonId = Number(parts[3])
      const teamId = Number(parts[5])
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const team = [...(fixtures.esportsTeams ?? []), ...teamsMade,
        {id: 1, game: "VALORANT", name: "BS Waterboarders", image: "valorantesports1.jpg"},
        {id: 2, game: "VALORANT", name: "BS SpicyWater", image: "valorantesports2.jpg"},
        {id: 3, game: "VALORANT", name: "BS Old Guard", image: null},
      ].find(one => one.id === teamId) ?? {id: teamId, game: "VALORANT", name: `Team ${teamId}`, image: null}
      const carried = body.carryLineup === true && teamId === 3
        ? [{role: "PLAYER", handle: "veteran", name: null}]
        : []
      fieldedNow.push({seasonId, teamId, members: carried})
      return fulfillJson(route, {
        team,
        season: written.get(seasonId) ?? esportsSeasons.find(one => one.id === seasonId) ?? esportsSeasons[0],
        carried: carried.map((member, index) => ({
          id: 300 + index, teamId, seasonId, role: member.role, handle: member.handle,
          displayName: null, userId: null, sortIndex: index,
        })),
      })
    }
    if (method === "GET" && /^\/esports\/teams\/\d+\/roster$/.test(path)) {
      if (fixtures.esportsRoster) return fulfillJson(route, fixtures.esportsRoster)
      const teamId = Number(path.split("/")[3])
      const seasonId = Number(url.searchParams.get("seasonId"))
      return fulfillJson(route, roster
        .filter(one => one.teamId === teamId && one.seasonId === seasonId)
        .map(one => ({...one, icon: icons.get(Number(one.id)) ?? null})))
    }
    if (method === "PUT" && /^\/esports\/roster\/\d+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const id = Number(path.split("/").pop())
      const entry = roster.find(one => one.id === id)
      if (entry) Object.assign(entry, {
        handle: body.handle, role: body.role, displayName: body.displayName ?? null,
        sortIndex: body.sortIndex, roleTitle: body.roleTitle ?? null, description: body.description ?? null,
      })
      const icon = pictureNamed(body.icon)
      if (icon) icons.set(id, icon)
      else icons.delete(id)
      return fulfillJson(route, {...(entry ?? {}), icon})
    }
    if (method === "DELETE" && /^\/esports\/roster\/\d+$/.test(path)) {
      const id = Number(path.split("/").pop())
      const at = roster.findIndex(one => one.id === id)
      if (at >= 0) roster.splice(at, 1)
      return route.fulfill({status: 204, body: ""})
    }
    if (method === "POST" && /^\/esports\/teams\/\d+\/roster$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const teamId = Number(path.split("/")[3])
      nextEntryId += 1
      const seated = fieldedNow.find(one => one.teamId === teamId && one.seasonId === body.seasonId)
      seated?.members.push({role: body.role, handle: body.handle, name: null})
      roster.push({
        id: nextEntryId, teamId, seasonId: body.seasonId, role: body.role, handle: body.handle,
        displayName: body.displayName ?? null, userId: body.userId ?? null,
        sortIndex: body.sortIndex ?? roster.length, roleTitle: body.roleTitle ?? null,
        description: body.description ?? null,
      })
      const icon = pictureNamed(body.icon)
      if (icon) icons.set(nextEntryId, icon)
      return fulfillJson(route, {id: nextEntryId, teamId, seasonId: body.seasonId, role: body.role, handle: body.handle, displayName: body.displayName ?? null, userId: null, sortIndex: 2, icon}, 201)
    }
    if (method === "PUT" && /^\/esports\/roster\/\d+\/member$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      const id = Number(path.split("/")[3])
      const entry = roster.find(one => one.id === id)
      if (entry) entry.userId = body.userId ?? null
      return fulfillJson(route, entry ?? {id, userId: body.userId ?? null})
    }
    if (method === "GET" && /^\/users\/\d+\/game-accounts$/.test(path)) {
      return fulfillJson(route, [{id: 5, userId: 1, game: "VALORANT", handle: "AriosFury"}])
    }
    if (method === "PUT" && /^\/users\/\d+\/game-accounts\/[A-Z0-9_]+$/.test(path)) {
      const body = JSON.parse(request.postData() ?? "{}") as Record<string, unknown>
      return fulfillJson(route, {id: 5, userId: 1, game: path.split("/").pop(), handle: body.handle})
    }
    if (method === "GET" && path === "/management/cohort-subjects") {
      return fulfillJson(route, fixtures.cohortSubjects ?? [
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
    if (method === "GET" && /^\/management\/cohort-subjects\/\d+$/.test(path)) {
      const id = Number(path.split("/")[3] ?? "0")
      const isCommittee = id === 102
      return fulfillJson(route, {
        id,
        type: isCommittee ? "COMMITTEE_MEMBERS" : "PERIOD_MEMBERS",
        category: isCommittee ? "COMMITTEES" : "PERIODS",
        label: isCommittee ? "Web Cmte" : "Members 2025-2026",
        description: null,
        mappings: [
          {
            cohortId: isCommittee ? 2 : 1,
            system: "BREVO",
            kind: "LIST",
            label: isCommittee ? "Web Cmte" : "Members 2025-2026",
            path: isCommittee ? ["Brevo", "Committees"] : ["Brevo", "Contribution periods"],
            externalId: isCommittee ? "33" : "7",
            lastReconciledAt: "2026-02-10T09:00:00Z",
          },
        ],
        definitionKey: isCommittee ? "COMMITTEE_MEMBERS:42" : "PERIOD_MEMBERS:1",
        orphaned: false,
        ...(fixtures.cohortSubjectDetail ?? {}),
        // One of each state the page draws: in sync, ours-but-not-pushed, and two rows the
        // target has that we do not — one we can name, one we cannot.
        members: fixtures.cohortMembers ?? [
          {
            cohortMemberId: 200 + id,
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
            cohortMemberId: 300 + id,
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
            cohortMemberId: 400 + id,
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
            cohortMemberId: 500 + id,
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
      const retried = {
        ...(existing ?? {id, jobType: "SYNC_DISCORD"}),
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
      return fulfillJson(route, retried)
    }
    if (method === "GET" && path === "/management/emails/stats") {
      const counts: Record<string, number> = {PENDING: 0, SENT: 0, DELIVERED: 0, OPENED: 0, BOUNCED: 0, FAILED: 0}
      for (const email of baseEmails) {
        const s = toSearchableString(email.deliveryStatus).toUpperCase()
        if (s in counts) counts[s] = (counts[s] ?? 0) + 1
      }
      return fulfillJson(route, {
        totalCount: baseEmails.length,
        pendingCount: counts["PENDING"],
        sentCount: counts["SENT"],
        deliveredCount: counts["DELIVERED"],
        openedCount: counts["OPENED"],
        bouncedCount: counts["BOUNCED"],
        failedCount: counts["FAILED"],
      })
    }
    if (method === "GET" && /^\/management\/emails\/\d+\/preview$/.test(path)) {
      const id = Number(path.replace(/\D+/g, ""))
      const email = baseEmails.find((candidate) => Number(candidate["id"]) === id)
      if (!email || email["previewable"] === false) {
        return fulfillJson(route, {message: "No stored body"}, 404)
      }
      // As the api answers: already rendered, and already stripped of its urls.
      return fulfillJson(route, {
        subject: email["subject"],
        html: `<html><body><p>Hello ${email["recipientName"]}</p><a href="">Activate your account</a></body></html>`,
        recipientEmail: email["recipientEmail"],
        recipientName: email["recipientName"],
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

      return fulfillJson(route, {
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
      const retried = {
        ...existing,
        deliveryStatus: "SENT",
        errorType: null,
        errorReason: null,
      }
      baseEmails.splice(index, 1, retried)
      return fulfillJson(route, retried)
    }
    if (method === "POST" && path === "/signup") {
      const payload = route.request().postDataJSON() as Record<string, unknown> | null
      const applicant = {
        id: 9999,
        username: String(payload?.username ?? "new-applicant"),
        email: String(payload?.email ?? "applicant@example.com"),
        discord: "",
        phoneNumber: "",
        newsletter: true,
        consentPrivacy: true,
        photoConsent: false,
        roles: ["GUEST"],
        enabled: false,
        version: 0,
      }
      baseUsers.push(applicant)
      return fulfillJson(route, {
        userId: applicant.id,
        email: applicant.email,
        signupToken: "e2e-selector.e2e-verifier",
        expiresAt: "2099-01-01T00:00:00.000Z",
      }, 201)
    }
    if (method === "POST" && path === "/signup/address") {
      return fulfillJson(route, {}, 204)
    }
    if (method === "POST" && path === "/signup/apply") {
      return fulfillJson(route, {emailConfirmed: false, membershipStarted: false})
    }
    if (method === "PATCH" && path === "/signup/details") {
      return fulfillJson(route, {}, 204)
    }
    if (method === "PATCH" && path === "/signup/email") {
      return fulfillJson(route, {}, 204)
    }
    if (method === "POST" && path === "/users") {
      return fulfillJson(route, {id: 999, username: "new-user", email: "new@example.com", discord: "", phoneNumber: "", newsletter: true, consentPrivacy: true, photoConsent: false, roles: ["USER"], enabled: false, version: 0})
    }
    if (method === "PUT" && path.endsWith("/approve")) {
      return fulfillJson(route, {...baseEvents[0], approved: true})
    }
    if (method === "DELETE" && /\/events\/\d+$/.test(path)) {
      return fulfillJson(route, {}, 204)
    }
    if (method === "GET" && /\/events\/\d+\/banners$/.test(path)) {
      return fulfillJson(route, {}, 404)
    }
    if (method === "POST" && /^\/recovery\/users\/\d+\/resend\/recovery$/.test(path)) {
      return route.fulfill({status: 204, contentType: "application/json", body: ""})
    }

    if (method === "POST" && /\/recovery\/user\/activate\/resend\//.test(path)) {
      return fulfillJson(route, {}, 200)
    }
    if (method === "POST" && /\/recovery\/password\/reset\//.test(path)) {
      return fulfillJson(route, {}, 200)
    }

    return fulfillJson(route, {}, 200)
  }

  const apiGlobs = [
    "http://localhost:8080/**",
    "http://127.0.0.1:8080/**",
    "http://localhost:4173/api/**",
    "http://127.0.0.1:4173/api/**",
  ]

  for (const glob of apiGlobs) {
    await page.route(glob, handleApiRoute)
  }

  await page.route("https://discordapp.com/api/guilds/**/widget.json", async (route) => {
    return fulfillJson(route, {
      presence_count: 2,
      channels: [{id: "1", name: "General"}],
      members: [
        {username: "Emma", status: "online", avatar_url: "", channel_id: "1"},
        {username: "Viktor", status: "idle", avatar_url: "", channel_id: "1"},
      ],
    })
  })

  await page.route("https://www.google.com/maps/embed**", async (route) => {
    await route.fulfill({
      status: 200,
      contentType: "text/html",
      body: "<html><body>Mock map</body></html>",
    })
  })
}
