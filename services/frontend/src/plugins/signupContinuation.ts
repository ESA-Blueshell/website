/**
 * The signup continuation token: where it is kept, and how a tab learns it is gone.
 *
 * An applicant fills the form in one tab and opens the activation link in another,
 * so the two facts that finish a signup can land in either order and in either
 * place. This owns all three sides of that — the stored token, what other tabs
 * announce, and what the api says when the token no longer works — because the
 * form and the activation pages otherwise each keep their own half of the rule and
 * disagree about which name it goes by.
 */

const STORAGE_KEY = "signup:continuation:token"

const ACTIVATION_CHANNEL = "blueshell:account-activation"

/** Mirrored to localStorage so tabs still hear it without BroadcastChannel. */
const ACTIVATION_MIRROR_KEY = "account:activation:announced"

/** The api's one message for a token that is malformed, expired or already spent. */
const REJECTED_TOKEN_DETAIL = "Invalid or expired recovery token."

export type AccountActivation = {
  /** The account that came alive, when the announcing tab knows it. */
  username?: string
  /** Milliseconds since the epoch, which is what makes each announcement distinct. */
  at: number
}

// --- The stored token -------------------------------------------------------
// Session storage is per tab, which is the point: the token belongs to the tab
// that is filling the form. Every access is guarded because a browser in a
// privacy mode throws on it rather than answering empty, and an applicant whose
// first step threw here got no further and saw no reason why.

export function readSignupToken(): string | undefined {
  if (typeof window === "undefined") return undefined
  try {
    return sessionStorage.getItem(STORAGE_KEY) ?? undefined
  } catch {
    return undefined
  }
}

export function rememberSignupToken(token: string): void {
  if (typeof window === "undefined") return
  try {
    sessionStorage.setItem(STORAGE_KEY, token)
  } catch {
    // The signup still works from the token held in memory for this page.
  }
}

export function forgetSignupToken(): void {
  if (typeof window === "undefined") return
  try {
    sessionStorage.removeItem(STORAGE_KEY)
  } catch {
    // Nothing to undo: a store that cannot be written was never read either.
  }
}

// --- What other tabs announce ----------------------------------------------

function openChannel(): BroadcastChannel | undefined {
  if (typeof BroadcastChannel === "undefined") return undefined
  try {
    return new BroadcastChannel(ACTIVATION_CHANNEL)
  } catch {
    return undefined
  }
}

export function announceAccountActivation(username?: string): void {
  if (typeof window === "undefined") return
  const activation: AccountActivation = {username, at: Date.now()}

  const channel = openChannel()
  if (channel) {
    try {
      channel.postMessage(activation)
    } finally {
      channel.close()
    }
  }

  // A same-value write raises no storage event, so the timestamp is what carries it.
  try {
    localStorage.setItem(ACTIVATION_MIRROR_KEY, JSON.stringify(activation))
  } catch {
    // Storage is unavailable in some privacy modes. The channel already spoke.
  }
}

/** Subscribes to activations announced by other tabs. Returns the unsubscribe. */
export function onAccountActivated(handler: (activation: AccountActivation) => void): () => void {
  if (typeof window === "undefined") return () => undefined

  const channel = openChannel()
  const onMessage = (event: MessageEvent) => {
    const activation = event.data as AccountActivation | null
    if (activation && typeof activation.at === "number") handler(activation)
  }
  channel?.addEventListener("message", onMessage)

  const onStorage = (event: StorageEvent) => {
    if (event.key !== ACTIVATION_MIRROR_KEY || !event.newValue) return
    try {
      const activation = JSON.parse(event.newValue) as AccountActivation
      if (typeof activation?.at === "number") handler(activation)
    } catch {
      // A value this tab cannot read is not an activation it can act on.
    }
  }
  window.addEventListener("storage", onStorage)

  return () => {
    channel?.removeEventListener("message", onMessage)
    channel?.close()
    window.removeEventListener("storage", onStorage)
  }
}

// --- What the api says about the token -------------------------------------

/**
 * Whether a failed request failed because the signup token is no longer usable.
 *
 * The api answers the same way whether the token expired, was already spent or
 * never existed, and deliberately says no more than that. That is enough: all
 * three mean this tab cannot save anything more, whatever it puts on screen.
 */
export function isSignupTokenRejection(err: unknown): boolean {
  if (!err || typeof err !== "object") return false
  const response = (err as {response?: {status?: number; data?: {detail?: string}}}).response
  if (!response) return false
  const carriedToken = Boolean(
    (err as {config?: {headers?: Record<string, unknown>}}).config?.headers?.["X-Signup-Token"]
  )
  if (!carriedToken) return false
  return response.data?.detail === REJECTED_TOKEN_DETAIL
}

const tokenRejectionHandlers = new Set<() => void>()

/** Called by the api client when a request on the signup token comes back refused. */
export function notifySignupTokenRejected(): void {
  for (const handler of [...tokenRejectionHandlers]) handler()
}

/** Subscribes to this tab's own token being refused. Returns the unsubscribe. */
export function onSignupTokenRejected(handler: () => void): () => void {
  tokenRejectionHandlers.add(handler)
  return () => {
    tokenRejectionHandlers.delete(handler)
  }
}
