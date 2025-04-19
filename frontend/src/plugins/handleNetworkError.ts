import { AxiosError } from 'axios';
// @ts-ignore
import router from './router';
import store from '@/plugins/store';
import type { RouteLocationRaw } from 'vue-router';

/**
 * Handles network errors from axios requests and shows appropriate user feedback
 * @param error The axios error object
 */
export function $handleNetworkError(error: any): void {
  let errorMessage: string;
  const currentRoute = router.currentRoute.value;

  if (error.response) {
    switch (error.response.status) {
      case 400:
        errorMessage = "Uhhhh, looks like a bad request (error 400)... Not sure how this happened. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>.";
        break;
      case 401:
        errorMessage = "Woah there, looks like you're not logged in (anymore). Just log in and try again.";
        if (!currentRoute.fullPath.startsWith("/login")) {
          const redirect: RouteLocationRaw = {
            path: '/login',
            query: {
              redirect: currentRoute.query.redirect || currentRoute.fullPath
            }
          };
          router.push(redirect);
        }
        break;
      case 403:
        errorMessage = "Woah there, you don't have enough authority to access this. Go to jail and DO NOT PASS GO, DO NOT COLLECT $200.";
        router.push({ path: '/account' });
        break;
      case 404:
        errorMessage = "Uhhhhhhh 404 moment. This resource doesn't exist anymore. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a> if you think this is an error.";
        break;
      case 408:
        errorMessage = "Zzzzzzzzzzzz... there seems to have been a request timeout (error code 408). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>.";
        break;
      case 413:
        errorMessage = "Your file is too large. Please compress it and try again";
        break;
      case 500:
        errorMessage = "Hm. okay. seems like the server is very confused (error code 500). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>.";
        break;
      case 502:
        errorMessage = "Uh oh, the server seems to be down (error code 502). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>.";
        break;
      default:
        errorMessage = `Oh no. An error happened that we don't know about (error code ${error.response.status}). Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target="_blank" class="text-decoration-none">Sitecie suggestions channel on discord</a>.`;
        break;
    }
  } else if (error.request) {
    errorMessage = "Oh no. The request was made but no response was received. Please check your internet connection.";
  } else {
    errorMessage = "Oh no. An error happened that we don't know about. Please report this in the <a href='https://discord.com/channels/324285132133629963/1020245710987350047' target=\"_blank\" class=\"text-decoration-none\">Sitecie suggestions channel on discord</a>.";
  }

  store.commit('setStatusSnackbarMessage', errorMessage);
}
