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
  committees?: Array<Record<string, unknown>>
  blogs?: Array<Record<string, unknown>>
  blogsById?: Record<string, Record<string, unknown>>
  blogStatusById?: Record<string, number>
  jobs?: Array<Record<string, unknown>>
  emails?: Array<Record<string, unknown>>
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

export async function loginAsAdmin(context: BrowserContext) {
  await loginAsRoles(context, ["ADMIN", "MEMBER"])
}

export async function installApiMocks(page: Page, fixtures: Fixtures = {}) {
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
