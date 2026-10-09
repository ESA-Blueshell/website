/*
 * The temporary public SNTPings tab, behind one flag. The nav entry and the route both read this,
 * and nothing else reaches the page: flip SNTPINGS_ENABLED to false after the event and the tab
 * goes in one edit.
 */

export const SNTPINGS_ENABLED = true

/** The tab's address, shared by the nav entry and the route so the two cannot drift. */
export const SNTPINGS_PATH = "/sntpings"
