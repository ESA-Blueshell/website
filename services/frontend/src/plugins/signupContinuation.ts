/**
 * The signup continuation token: where it is kept, and how a tab learns it is gone.
 *
 * Kept together because the form and the activation pages otherwise each hold half
 * the rule and disagree about the name it goes by (ADR-025).
 */

const STORAGE_KEY = "signup:continuation:token"

const ACTIVATION_CHANNEL = "blueshell:account-activation"

/** Mirrored to localStorage so tabs still hear it without BroadcastChannel. */
const ACTIVATION_MIRROR_KEY = "account:activation:announced"

/**
 * The code the api answers for a token that is malformed, expired or already
 * spent. Mirrors AuthProblemDetailsAdvice.RECOVERY_TOKEN_UNUSABLE_CODE.
 */
const REJECTED_TOKEN_CODE = "RecoveryTokenUnusable"

export type AccountActivation = {
  /** The account that came alive, when the announcing tab knows it. */
  username?: string
  /** Milliseconds since the epoch, which is what makes each announcement distinct. */
  at: number
}

// --- The stored token -------------------------------------------------------
// Per tab on purpose: the token belongs to the tab filling the form. Guarded
// because a privacy mode throws on storage rather than answering empty.

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

  // The announcement travels both ways at once, because neither transport is
  // available everywhere. A tab that has both hears the same activation twice,
  // and acting on it twice means two messages and two navigations, so the
  // timestamp that distinguishes announcements is also what collapses them.
  let lastSeenAt = 0
  const deliverOnce = (activation: AccountActivation | null | undefined) => {
    if (!activation || typeof activation.at !== "number") return
    if (activation.at <= lastSeenAt) return
    lastSeenAt = activation.at
    handler(activation)
  }

  const channel = openChannel()
  const onMessage = (event: MessageEvent) => {
    deliverOnce(event.data as AccountActivation | null)
  }
  channel?.addEventListener("message", onMessage)

  const onStorage = (event: StorageEvent) => {
    if (event.key !== ACTIVATION_MIRROR_KEY || !event.newValue) return
    try {
      deliverOnce(JSON.parse(event.newValue) as AccountActivation)
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

/** Header lookup that does not depend on how the sender cased the name. */
function carriedSignupToken(err: unknown): boolean {
  const headers = (err as {config?: {headers?: Record<string, unknown>}}).config?.headers
  if (!headers || typeof headers !== "object") return false
  return Object.keys(headers).some((key) => key.toLowerCase() === "x-signup-token")
}

/**
 * Whether a failed request failed because the signup token is no longer usable.
 *
 * Read from the code rather than the sentence (ADR-026). The api answers one code
 * whether the token expired, was already spent or never existed, which is enough:
 * all three mean this tab cannot save anything more. The header is what says the
 * refusal was about *this* token and not some other credential on the page.
 */
export function isSignupTokenRejection(err: unknown): boolean {
  if (!err || typeof err !== "object") return false
  const response = (err as {response?: {data?: {code?: string}}}).response
  if (!response) return false
  if (response.data?.code !== REJECTED_TOKEN_CODE) return false
  return carriedSignupToken(err)
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
