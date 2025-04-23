import router from "@/plugins/router";

/**
 * Navigates to a given url. If it starts with https:// or http://, it will open in a new tab.
 *
 * @param {string} url url to navigate to
 */
export function $goto(url) {
  if (url.includes('https://') || url.includes('http://')) {
    window.open(url, '_blank').focus();
  } else {
    window.scrollTo(0, 0);
    router.push(url);
  }
}
