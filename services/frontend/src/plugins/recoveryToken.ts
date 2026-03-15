import type {
  LocationQueryValue,
  RouteLocationNormalizedLoaded,
  Router,
} from "vue-router"

function asSingleQueryValue(value: LocationQueryValue | LocationQueryValue[] | undefined): string {
  if (Array.isArray(value)) {
    const first = value.find((entry) => typeof entry === "string")
    return typeof first === "string" ? first : ""
  }
  return typeof value === "string" ? value : ""
}

function parseHashQuery(hash: string): URLSearchParams {
  if (!hash) return new URLSearchParams()
  return new URLSearchParams(hash.startsWith("#") ? hash.slice(1) : hash)
}

function extractTokenFromRoute(route: RouteLocationNormalizedLoaded): string {
  const hashToken = parseHashQuery(route.hash).get("token")?.trim() ?? ""
  if (hashToken) return hashToken
  return asSingleQueryValue(route.query.token).trim()
}

function stripTokenFromLocation(route: RouteLocationNormalizedLoaded, router: Router) {
  const nextQuery = {...route.query}
  const hasQueryToken = "token" in nextQuery
  if (hasQueryToken) delete nextQuery.token

  const hashParams = parseHashQuery(route.hash)
  const hasHashToken = hashParams.has("token")
  if (hasHashToken) hashParams.delete("token")

  if (!hasQueryToken && !hasHashToken) return

  const nextHash = hashParams.toString()
  const replaceResult = router.replace({
    query: nextQuery,
    hash: nextHash ? `#${nextHash}` : "",
  })

  if (typeof (replaceResult as Promise<unknown> | undefined)?.catch === "function") {
    void (replaceResult as Promise<unknown>).catch(() => undefined)
  }
}

export function clearStoredRecoveryToken(storageKey: string) {
  if (typeof window === "undefined") return
  sessionStorage.removeItem(storageKey)
}

export function loadRecoveryTokenFromRoute(
  route: RouteLocationNormalizedLoaded,
  router: Router,
  storageKey: string
): string {
  const routeToken = extractTokenFromRoute(route)
  if (routeToken) {
    if (typeof window !== "undefined") {
      sessionStorage.setItem(storageKey, routeToken)
    }
    stripTokenFromLocation(route, router)
    return routeToken
  }

  if (typeof window === "undefined") return ""
  return sessionStorage.getItem(storageKey) ?? ""
}
