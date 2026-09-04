import router from "./router"
import store from "@/plugins/store"
import type {AxiosError} from "axios"
import {isSignupTokenRejection, notifySignupTokenRejected} from "@/plugins/signupContinuation"


function isAxiosError(e: unknown): e is AxiosError {
  return !!(e && typeof e === "object" && (e as AxiosError).response?.status)
}

/**
 * The api's own sentence for a refusal, when there is one worth reading.
 *
 * A message keyed on the status alone renames every deliberate refusal the api makes: a taken
 * username reads as an edit conflict, and the applicant is told to reload, which is the one action
 * that loses a signup. This is the fallback ADR-026 names — a refusal the frontend has not been
 * taught a sentence for falls through to `detail` rather than to nothing. Two kinds of body are
 * left alone: one carrying `errors` is already attached to its fields by the form, and a 5xx detail
 * describes a fault the reader cannot act on. Which statuses read it is decided at the branches
 * below.
 */
function refusalDetail(err: AxiosError): string | null {
  const status = err.response?.status ?? 0
  if (status < 400 || status >= 500) return null
  const data = err.response?.data as {detail?: unknown; errors?: unknown} | undefined
  if (!data || typeof data !== "object" || "errors" in data) return null
  const detail = data.detail
  return typeof detail === "string" && detail.trim().length > 0 ? detail.trim() : null
}

/** How long the api asked the caller to wait, in whole seconds. */
function retryAfterSeconds(err: AxiosError): number | null {
  const headers = err.response?.headers as Record<string, unknown> | undefined
  const raw = headers?.["retry-after"] ?? headers?.["Retry-After"]
  const seconds = Number(raw)
  return Number.isFinite(seconds) && seconds > 0 ? Math.ceil(seconds) : null
}

/** Says something to the user, without an error to derive it from. */
export function $showStatusMessage(message: string): void {
  store.commit("setStatusSnackbarMessage", message)
}

/**
 * Handles network errors from axios requests and shows appropriate user feedback
 * @param err The axios error object
 */
export function $handleNetworkError(err: unknown): void {
  // Said once for the whole signup: every step reports its refusals through here,
  // and none of them can tell a retired token from any other rejection.
  if (isSignupTokenRejection(err)) {
    notifySignupTokenRejected()
    return
  }

  if (!isAxiosError(err)) {
    const errorMessage = "An unknown error occurred. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
    store.commit("setStatusSnackbarMessage", errorMessage)
    console.log(err)
    return
  }

  let error = err as AxiosError

  let errorMessage: string
  const currentRoute = router.currentRoute.value
  // Read once: every 4xx branch below prefers it over its own canned text, except the
  // 401, whose Login action is the point rather than its wording.
  const refusal = refusalDetail(error)

  if (error.response) {
    switch (error.response.status) {
      case 400:
        errorMessage = refusal
          ?? "Uhhhh, looks like a bad request (error 400)... Not sure how this happened. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      case 401: {
        // Don't auto-logout or auto-redirect on a 401: the user might
        // genuinely still be signed in (the Vault OIDC popup chain hit
        // this path repeatedly, blowing away live sessions). Surface a
        // snackbar with a Login action instead and let the user decide.
        // /login renders inside the regular site chrome by default
        // (App.vue only goes bare for the OIDC popup path), so the
        // user can navigate elsewhere if they didn't actually mean to
        // sign in.
        const redirectTarget = (currentRoute.query.redirect as string)
          || currentRoute.fullPath
        const loginHref = `/login?redirect=${encodeURIComponent(redirectTarget)}`
        errorMessage = "Woah there, looks like you're not logged in (anymore)."
        store.commit("setStatusSnackbarMessage", errorMessage)
        store.commit("setStatusSnackbarAction", {label: "Login", to: loginHref})
        return
      }
      case 403:
        errorMessage = refusal
          ?? "Woah there, you don't have enough authority to access this. Go to jail and DO NOT PASS GO, DO NOT COLLECT $200."
        break
      case 404:
        errorMessage = "Uhhhhhhh 404 moment. This resource doesn't exist anymore. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a> if you think this is an error."
        break
      case 408:
        errorMessage = "Zzzzzzzzzzzz... there seems to have been a request timeout (error code 408). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      case 409:
        errorMessage = refusal
          ?? "Woopsie daysies, it seems somebody else changed the same thing as you... You'll have to reload the page and make your changes again ;-;"
        break
      case 413:
        errorMessage = "Your file is too large. Please compress it and try again"
        break
      case 500:
        errorMessage = "Hm. okay. seems like the server is very confused (error code 500). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      case 429: {
        const wait = retryAfterSeconds(error)
        errorMessage = wait
          ? `That was a lot of tries in a row. Give it ${wait} seconds and go again.`
          : refusal ?? "That was a lot of tries in a row. Give it a minute and go again."
        break
      }
      case 502:
        errorMessage = "Uh oh, the server seems to be down (error code 502). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      default:
        errorMessage = refusal
          ?? `Oh no. An error happened that we don't know about (error code ${error.response.status}). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target="_blank" class="text-decoration-none">Sitecie suggestions channel on discord</a>.`
        if (!refusal) console.log(error)
        break
    }
  } else if (error.request) {
    errorMessage = "Oh no. The request was made but no response was received. Please check your internet connection."
  } else {
    errorMessage = "Oh no. An error happened that we don't know about. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
    console.log(error)
  }

  store.commit("setStatusSnackbarMessage", errorMessage)
}
