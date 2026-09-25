import type {RouteRecordRaw} from "vue-router"

/** Canvas-only pages for the committees preview: a committee added or corrected on its own page. */
export const routes: RouteRecordRaw[] = [
  {
    path: "/committees/new",
    name: "committeeNew",
    component: () => import("@/pages/committees/CommitteeEdit.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/committees/:address/edit",
    name: "committeeEdit",
    component: () => import("@/pages/committees/CommitteeEdit.vue"),
    meta: {requiresAuth: true},
  },
]
