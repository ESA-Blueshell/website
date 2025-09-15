import router from "@/plugins/router";
import type { RouteLocationRaw } from "vue-router";

/**
 * Navigate to a url or route.
 * - If the input is a string starting with http(s)://, it opens in a new tab.
 * - Otherwise, it scrolls to top and uses the Vue Router to navigate.
 */
export function $goto(url: string | RouteLocationRaw): void {
  if (typeof url === "string" && /^https?:\/\//i.test(url)) {
    const win = window.open(url, "_blank");
    // Focus may fail if blocked by the browser; guard it.
    try {
      win?.focus();
    } catch {
      // noop
    }
    return;
  }

  window.scrollTo({ top: 0, left: 0, behavior: "auto" });
  router.push(url);
}
