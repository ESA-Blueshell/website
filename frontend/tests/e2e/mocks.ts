import type {BrowserContext, Page, Route} from "@playwright/test"

type Fixtures = {
  users?: Array<Record<string, unknown>>
  memberships?: Array<Record<string, unknown>>
  contributionPeriods?: Array<Record<string, unknown>>
  contributions?: Array<Record<string, unknown>>
  addresses?: Array<Record<string, unknown>>
  events?: Array<Record<string, unknown>>
  eventSignUps?: Array<Record<string, unknown>>
  committees?: Array<Record<string, unknown>>
}

async function fulfillJson(route: Route, data: unknown, status = 200) {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(data),
  })
}

export async function loginAsBoard(context: BrowserContext) {
  const loginCookie = encodeURIComponent(JSON.stringify({
    userId: 1,
    username: "board-user",
    token: "mock-token",
    roles: ["BOARD", "MEMBER"],
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

export async function installApiMocks(page: Page, fixtures: Fixtures = {}) {
  const baseUsers = fixtures.users ?? [
    {id: 1, fullName: "Emma Dokter", username: "lyndisluna", enabled: true, roles: ["MEMBER"]},
    {id: 2, fullName: "Viktor Petrov", username: "ariosfury", enabled: false, roles: ["USER"]},
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

  const handleApiRoute = async (route: Route) => {
    const request = route.request()
    const url = new URL(request.url())
    const method = request.method()
    const path = url.pathname.startsWith("/api/")
      ? url.pathname.slice(4)
      : url.pathname

    if (method === "GET" && path === "/users") {
      return fulfillJson(route, {content: baseUsers})
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
    if (method === "GET" && path.startsWith("/events/signups/byAccessToken/")) {
      return fulfillJson(route, baseEventSignUps)
    }
    if (method === "GET" && path === "/committees") {
      return fulfillJson(route, baseCommittees)
    }
    if (method === "GET" && path === "/committeeMembers/committees") {
      return fulfillJson(route, baseCommittees)
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
}
