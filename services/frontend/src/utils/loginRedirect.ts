// Absolute targets are honoured only for the admin hosts behind Traefik's
// forwardAuth, which bounces an anonymous user to /login?redirect=<original
// url>. Keep in lockstep with ForwardAuthController.HOST_ROLE on the api side.
const TRUSTED_REDIRECT_HOSTS = [
  "traefik.esa-blueshell.nl",
  "vault.esa-blueshell.nl",
  "headlamp.esa-blueshell.nl",
  "stalwart.esa-blueshell.nl",
]

// Same-origin prefixes that sit outside the SPA router: the api, the Spring
// Authorization Server and its discovery documents.
const OFF_SPA_PREFIXES = ["/api/", "/oauth2/", "/.well-known/"]

export type LoginRedirect = {
  /** Where to send the browser — always a value resolved here, never raw input. */
  target: string
  /** True when the target needs a full page load rather than router.push. */
  offSpa: boolean
}

const HOME: LoginRedirect = {target: "/", offSpa: false}

/**
 * Resolve a `?redirect=` value into a target that is safe to navigate to.
 *
 * Off-SPA targets need location.assign, since router.push only understands
 * SPA routes — which makes the value a navigation sink. Anything that does
 * not land on our own origin or a trusted admin host resolves to "/", so a
 * crafted ?redirect=https://evil.com cannot turn login into an open redirect.
 */
export function resolveLoginRedirect(
  raw: string | null | undefined,
  origin: string,
): LoginRedirect {
  if (!raw) {
    return HOME
  }

  let url: URL
  try {
    url = new URL(raw, origin)
  } catch {
    return HOME
  }

  if (url.origin === origin) {
    return {
      target: `${url.pathname}${url.search}${url.hash}`,
      offSpa: OFF_SPA_PREFIXES.some((prefix) => url.pathname.startsWith(prefix)),
    }
  }

  // Cross-origin is https-only: a trusted host reached over http would be a
  // downgrade, and forwardAuth always hands us https.
  if (url.protocol === "https:" && TRUSTED_REDIRECT_HOSTS.includes(url.hostname.toLowerCase())) {
    return {target: url.toString(), offSpa: true}
  }

  return HOME
}
