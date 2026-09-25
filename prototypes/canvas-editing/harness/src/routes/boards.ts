import type {RouteRecordRaw} from "vue-router"

/** Canvas-only pages for the board preview: boards and their members edited on their own pages. */
export const routes: RouteRecordRaw[] = [
  {path: "/board/new", name: "boardNew", component: () => import("@/pages/board/BoardEdit.vue"), meta: {requiresAuth: true}},
  {path: "/board/:number(\\d+)/edit", name: "boardEdit", component: () => import("@/pages/board/BoardEdit.vue"), meta: {requiresAuth: true}},
  {path: "/board/:number(\\d+)/members/new", name: "boardMemberNew", component: () => import("@/pages/board/BoardMemberEdit.vue"), meta: {requiresAuth: true}},
  {path: "/board/:number(\\d+)/members/:member(\\d+)/edit", name: "boardMemberEdit", component: () => import("@/pages/board/BoardMemberEdit.vue"), meta: {requiresAuth: true}},
]
