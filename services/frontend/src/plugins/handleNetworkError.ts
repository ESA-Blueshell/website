import router from "./router"
import store from "@/plugins/store"
import type {AxiosError} from "axios"


function isAxiosError(e: unknown): e is AxiosError {
  return !!(e && typeof e === "object" && (e as AxiosError).response?.status)
}

/**
 * Handles network errors from axios requests and shows appropriate user feedback
 * @param err The axios error object
 */
export function $handleNetworkError(err: unknown): void {
  if (!isAxiosError(err)) {
    const errorMessage = "An unknown error occurred. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
    store.commit("setStatusSnackbarMessage", errorMessage)
    console.log(err)
    return
  }

  let error = err as AxiosError

  let errorMessage: string
  const currentRoute = router.currentRoute.value

  if (error.response) {
    switch (error.response.status) {
      case 400:
        errorMessage = "Uhhhh, looks like a bad request (error 400)... Not sure how this happened. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
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
        errorMessage = "Woah there, you don't have enough authority to access this. Go to jail and DO NOT PASS GO, DO NOT COLLECT $200."
        break
      case 404:
        errorMessage = "Uhhhhhhh 404 moment. This resource doesn't exist anymore. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a> if you think this is an error."
        break
      case 408:
        errorMessage = "Zzzzzzzzzzzz... there seems to have been a request timeout (error code 408). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      case 409:
        errorMessage = "Woopsie daysies, it seems somebody else changed the same thing as you... You'll have to reload the page and make your changes again ;-;"
        break
      case 413:
        errorMessage = "Your file is too large. Please compress it and try again"
        break
      case 500:
        errorMessage = "Hm. okay. seems like the server is very confused (error code 500). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      case 502:
        errorMessage = "Uh oh, the server seems to be down (error code 502). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>."
        break
      default:
        errorMessage = `Oh no. An error happened that we don't know about (error code ${error.response.status}). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target="_blank" class="text-decoration-none">Sitecie suggestions channel on discord</a>.`
        console.log(error)
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
