import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router"
import store from "./store"

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "home",
    component: () => import("@/pages/Home.vue"),
  },
  {
    path: "/contact",
    name: "contact",
    component: () => import("@/pages/Contact.vue"),
  },
  {
    path: "/committees",
    name: "committees",
    component: () => import("@/pages/Committees.vue"),
  },
  {
    path: "/committees/manage",
    name: "committeeManager",
    component: () => import("@/pages/management/CommitteeManager.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/esports",
    redirect: "/esports/competitive-scene",
  },
  {
    path: "/esports/competitive-scene",
    name: "esports",
    component: () => import("@/pages/Esports.vue"),
  },
  {
    path: "/membership",
    name: "membership",
    component: () => import("@/pages/membership/Membership.vue"),
  },
  {
    path: "/membership/signup",
    name: "membership/signup",
    component: () => import("@/pages/membership/MembershipSignUp.vue"),
  },
  {
    path: "/documents",
    name: "documents",
    component: () => import("@/pages/Documents.vue"),
  },
  {
    path: "/aboutus",
    name: "aboutus",
    component: () => import("@/pages/AboutUs.vue"),
  },
  {
    path: "/board",
    name: "board",
    component: () => import("@/pages/Board.vue"),
  },
  // Every game's page, found by the address its record names. Declared after the fixed
  // esports paths above so those keep winning, and adding a game needs no route written.
  {
    path: "/esports/:slug",
    name: "game",
    component: () => import("@/pages/esports/GameBySlug.vue"),
  },
  {
    path: "/partners/become-a-partner",
    name: "becomeapartner",
    component: () => import("@/pages/partners/Partners.vue"),
  },
  {
    path: "/partners/el-nino",
    name: "elnino",
    component: () => import("@/pages/partners/ElNino.vue"),
  },
  {
    path: "/partners/marketing-maatwerk",
    name: "marketingmaatwerk",
    component: () => import("@/pages/partners/MarketingMaatwerk.vue"),
  },
  {
    // Login / forgot-password / account-create render inside the full
    // site chrome by default: they're regular pages a logged-out user
    // navigates between. App.vue flips them to a bare layout only when
    // the OIDC popup chain has redirected here (the Spring Authorization
    // Server hop at /api/oauth2/authorize... → /login?redirect=...);
    // see `isBareLayout` in App.vue for the detection logic.
    path: "/login",
    name: "login",
    component: () => import("@/pages/login/Login.vue"),
  },
  {
    path: "/login/forgor",
    name: "forgotPassword",
    component: () => import("@/pages/login/ForgotPassword.vue"),
  },
  {
    path: "/login/confirm",
    name: "resendConfirmation",
    component: () => import("@/pages/login/ResendConfirmation.vue"),
  },
  {
    path: "/account",
    name: "account",
    component: () => import("@/pages/login/Account.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/account/create",
    name: "accountCreation",
    component: () => import("@/pages/login/CreateAccount.vue"),
  },
  {
    path: "/account/reset-password",
    name: "resetPassword",
    component: () => import("@/pages/login/ResetPassword.vue"),
  },
  {
    path: "/account/activate/member",
    name: "activateMember",
    component: () => import("@/pages/activate/ActivateMember.vue"),
  },
  {
    path: "/account/activate/user",
    name: "activateUser",
    component: () => import("@/pages/activate/ActivateUser.vue"),
  },
  {
    path: "/account/reset-password/:username/:token",
    redirect: (to) => ({
      name: "resetPassword",
      hash: `#token=${String(to.params.token ?? "")}`,
    }),
  },
  {
    path: "/account/activate/member/:token",
    redirect: (to) => ({
      name: "activateMember",
      hash: `#token=${String(to.params.token ?? "")}`,
    }),
  },
  {
    path: "/account/activate/user/:username/:token",
    redirect: (to) => ({
      name: "activateUser",
      hash: `#token=${String(to.params.token ?? "")}`,
    }),
  },
  {
    path: "/account/addresses/:id?",
    name: "editAddress",
    component: () => import("@/pages/login/Address.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/events",
    name: "events",
    component: () => import("@/pages/Events.vue"),
  },
  {
    path: "/events/calendar",
    redirect: "/events",
  },
  {
    path: "/events/create",
    name: "createEvent",
    component: () => import("@/pages/events/EditEvent.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/events/edit/:id",
    name: "editEvent",
    component: () => import("@/pages/events/EditEvent.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/events/signups/:id",
    name: "eventSignUps",
    component: () => import("@/pages/events/EventSignUps.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/events/signups/edit",
    name: "editSignUp",
    redirect: (to) => ({
      path: "/events",
      hash: to.hash,
    }),
  },
  {
    path: "/events/signups/edit/:accessToken",
    redirect: (to) => ({
      path: "/events",
      hash: `#accessToken=${String(to.params.accessToken ?? "")}`,
    }),
  },
  {
    path: "/events/circuitShowdown",
    name: "circuitShowdown",
    component: () => import("@/pages/events/CircuitShowdown.vue"),
  },
  {
    path: "/user-manager",
    name: "userManager",
    component: () => import("@/pages/management/UserManager.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/addresses/manage",
    name: "addressManager",
    component: () => import("@/pages/management/AddressManager.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/recovery/manage",
    name: "recoveryManager",
    component: () => import("@/pages/management/RecoveryManager.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/management/jobs",
    name: "jobManager",
    component: () => import("@/pages/management/JobManager.vue"),
    meta: {requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/emails",
    name: "emailManager",
    component: () => import("@/pages/management/EmailManager.vue"),
    meta: {requiresAuth: true, requiresBoard: true},
  },
  {
    // The esports manager is gone: seasons, teams and line-ups are edited on the pages that
    // show them. A bookmark to it lands on those pages rather than on nothing.
    path: "/management/esports",
    redirect: "/esports/competitive-scene",
  },
  {
    path: "/management/cohorts",
    name: "cohortDashboard",
    component: () => import("@/pages/management/CohortDashboard.vue"),
    meta: {requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/targets",
    name: "cohortTargets",
    component: () => import("@/pages/management/CohortTargets.vue"),
    meta: {requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/subjects/:id",
    name: "cohortSubjectDetail",
    component: () => import("@/pages/management/CohortSubjectDetail.vue"),
    meta: {requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/:category",
    name: "cohortCategory",
    component: () => import("@/pages/management/CohortCategory.vue"),
    meta: {requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/blogs",
    name: "BlogList",
    component: () => import("@/pages/blogs/BlogsView.vue"),
  },
  {
    path: "/blogs/:id",
    name: "BlogView",
    component: () => import("@/pages/blogs/BlogView.vue"),
  },
  {
    path: "/myapps",
    name: "myApps",
    component: () => import("@/pages/MyApps.vue"),
    meta: {requiresAuth: true},
  },
  {
    // Landing page when Traefik forwardAuth refuses an authenticated user
    // because their role isn't high enough for the requested admin host
    // (vault, headlamp, stalwart, traefik). The api redirects
    // here with `?service=<host>` so the page can name what was blocked.
    // `meta.bare` because this page is reached from a popup / new tab
    // that has no business showing the full site chrome.
    path: "/unauthorized",
    name: "unauthorized",
    component: () => import("@/pages/Unauthorized.vue"),
    meta: {bare: true},
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/pages/NotFound.vue"),
  },
]

const router = createRouter({
  history: createWebHistory("/"),
  /**
   * Where a page opens: at the top, unless the reader is already standing somewhere.
   *
   * A saved position is one the browser recorded itself, going back or forward to somewhere
   * that was read before, and it is answered first because it is the only one of the three
   * that knows where that reader had got to.
   *
   * Changing the query on the page already open is not arriving anywhere. The season on the
   * esports pages lives in the url, so choosing one is a navigation as far as the router is
   * concerned, and scrolling to the top of it threw the reader back up the page every time
   * they picked a season, away from the thing they had scrolled down to read. `false` rather
   * than the offset they are already at: nothing to do at all, rather than a scroll that
   * happens to cancel out, which under a smooth-scrolling setting is a movement you can see.
   */
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    }
    if (to.path === from.path) {
      return false
    }
    return {left: 0, top: 0}
  },
  routes,
})

router.beforeEach((to, from, next) => {
  const login = store.getters.getLogin
  if (to.meta.requiresAuth && (login == null || store.getters.tokenExpired)) {
    next({
      path: "/login",
      query: {redirect: to.fullPath},
    })
  } else if (to.meta.requiresAdmin && !store.getters.isAdmin) {
    next({path: "/"})
  } else if (to.meta.requiresBoard && !(store.getters.isBoard || store.getters.isAdmin)) {
    next({path: "/"})
  } else {
    next()
  }
})

const RELOADED_FOR_CHUNK_KEY = "router:reloaded-for-chunk"

/**
 * A route whose code could not be fetched, which a stale page is what causes.
 *
 * Chunks are named by their contents, so a page open across a release asks for
 * files that no longer exist, and the router abandons the navigation in silence.
 * Only the module-loading messages count: a bare network failure means the reader
 * is offline, and reloading takes them to the browser's offline page instead.
 */
router.onError((error, to) => {
  const message = (error as Error)?.message ?? ""
  const isChunkFailure =
    /dynamically imported module|Importing a module script failed|ChunkLoadError/i.test(message)
  if (!isChunkFailure || typeof window === "undefined") return

  let alreadyReloaded: boolean
  try {
    alreadyReloaded = sessionStorage.getItem(RELOADED_FOR_CHUNK_KEY) === to.fullPath
    if (!alreadyReloaded) sessionStorage.setItem(RELOADED_FOR_CHUNK_KEY, to.fullPath)
  } catch {
    // Without storage there is no way to count, so this takes no attempt at all.
    alreadyReloaded = true
  }

  if (alreadyReloaded) {
    store.commit("setStatusSnackbarMessage", "this page could not load, so reload to get the current version")
    return
  }
  // Replace, so the abandoned navigation leaves no entry to go back to.
  window.location.replace(to.fullPath)
})

// A route that arrived is a route whose chunks are current, so the next failure on
// it is a new one rather than the same one repeating.
router.afterEach(() => {
  try {
    sessionStorage.removeItem(RELOADED_FOR_CHUNK_KEY)
  } catch {
    // Nothing was recorded, so there is nothing to forget.
  }
})

export default router
