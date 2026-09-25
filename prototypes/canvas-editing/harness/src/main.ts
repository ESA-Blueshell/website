import "./sandbox"
import {installMocks} from "./mockLayer"

/*
 * A canvas board carries one element naming the page it opens on, who is looking and in which
 * theme. The app is started on it once the canvas has drawn it.
 */
// The canvas draws the board's markup twice: once as written, then again inside its own root.
// Only the second one stays, so that is the one the app waits for; a page without the canvas
// runtime is given a moment before its only copy is taken.
const started = Date.now()
const find = () => document.querySelector<HTMLElement>("#dc-root [data-blueshell-board]")
  ?? (Date.now() - started > 3000 ? document.querySelector<HTMLElement>("[data-blueshell-board]") : null)

const whenBoard = () => new Promise<HTMLElement>(resolve => {
  const found = find()
  if (found) return resolve(found)
  const watcher = new MutationObserver(() => {
    const now = find()
    if (now) {
      watcher.disconnect()
      resolve(now)
    }
  })
  watcher.observe(document.documentElement, {childList: true, subtree: true})
  setTimeout(() => { const late = find(); if (late) { watcher.disconnect(); resolve(late) } }, 3100)
})

void whenBoard().then(async element => {
  const {route = "/", viewer = "visitor", theme = "dark", steps = "[]"} = element.dataset
  await installMocks(viewer, theme)
  const {startApp} = await import("./startApp")
  await startApp(element, route, JSON.parse(steps))
})
