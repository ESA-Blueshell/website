import type {RouteRecordRaw} from "vue-router"

/*
 * One game edit page for both areas. The casual and competition addresses stay, so every link
 * that reaches them keeps working, and /games is the page's own address.
 */
const page = () => import("@/pages/games/GameEdit.vue")
const board = {requiresAuth: true}

export const routes: RouteRecordRaw[] = [
  {path: "/games/new", name: "gameNew", component: page, meta: {...board, area: "games"}},
  {path: "/games/:slug/edit", name: "gameEdit", component: page, meta: {...board, area: "games"}},
  {path: "/casual/new", name: "casualGameNew", component: page, meta: {...board, area: "casual"}},
  {path: "/casual/:slug/edit", name: "casualGameEdit", component: page, meta: {...board, area: "casual"}},
  {path: "/competition/new", name: "competitionGameNew", component: page, meta: {...board, area: "competition"}},
  {path: "/competition/:slug/edit", name: "competitionGameEdit", component: page, meta: {...board, area: "competition"}},
]
