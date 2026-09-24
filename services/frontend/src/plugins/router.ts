import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router"
import {SECURITY_PAGES} from "@/domains/auth/securityPages"
import store from "./store"
import {tabTitle} from "./tabTitle"

declare module "vue-router" {
  interface RouteMeta {
    /** The page's name in the browser tab; a page that knows a better one sets it once loaded. */
    title?: string
  }
}

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
    meta: {title: "Contact"},
  },
  {
    path: "/committees",
    name: "committees",
    component: () => import("@/pages/Committees.vue"),
    meta: {title: "Committees"},
  },
  {
    path: "/committees/manage",
    name: "committeeManager",
    component: () => import("@/pages/management/CommitteeManager.vue"),
    meta: {title: "Manage committees", requiresAuth: true},
  },
  // The games index, and every game's own page by the address its record names.
  {
    path: "/casual",
    name: "casual",
    component: () => import("@/pages/Casual.vue"),
  },
  {
    path: "/casual/:slug",
    name: "casualGame",
    component: () => import("@/pages/casual/CasualGameBySlug.vue"),
  },
  // Competition is the word on screen for what the code calls esports. The old addresses
  // redirect, so a link somebody saved or shared still lands on the same page.
  {
    path: "/competition",
    name: "esports",
    component: () => import("@/pages/Esports.vue"),
    meta: {title: "Competitive scene"},
  },
  {
    path: "/esports",
    redirect: "/competition",
  },
  {
    path: "/esports/competitive-scene",
    redirect: "/competition",
  },
  {
    path: "/esports/:slug",
    redirect: to => `/competition/${String(to.params.slug)}`,
  },
  {
    path: "/membership",
    name: "membership",
    component: () => import("@/pages/membership/Membership.vue"),
    meta: {title: "Membership"},
  },
  {
    path: "/membership/signup",
    name: "membership/signup",
    component: () => import("@/pages/membership/MembershipSignUp.vue"),
    meta: {title: "Sign up"},
  },
  {
    path: "/documents",
    name: "documents",
    component: () => import("@/pages/Documents.vue"),
    meta: {title: "Documents"},
  },
  {
    path: "/aboutus",
    name: "aboutus",
    component: () => import("@/pages/AboutUs.vue"),
    meta: {title: "About us"},
  },
  {
    path: "/board",
    name: "board",
    component: () => import("@/pages/Board.vue"),
    meta: {title: "Board"},
  },
  // Every game's competition page, found by the address its record names. Adding a game
  // needs no route written.
  {
    path: "/competition/:slug",
    name: "game",
    component: () => import("@/pages/esports/GameBySlug.vue"),
    meta: {title: "Competition"},
  },
  {
    // Nothing links here, but a hand-typed /partners is a reasonable guess and reached the
    // not-found page.
    path: "/partners",
    redirect: "/partners/become-a-partner",
  },
  {
    path: "/partners/become-a-partner",
    name: "becomeapartner",
    component: () => import("@/pages/partners/Partners.vue"),
    meta: {title: "Become a partner"},
  },
  {
    path: "/partners/el-nino",
    name: "elnino",
    component: () => import("@/pages/partners/ElNino.vue"),
    meta: {title: "El Niño"},
  },
  {
    path: "/partners/marketing-maatwerk",
    name: "marketingmaatwerk",
    component: () => import("@/pages/partners/MarketingMaatwerk.vue"),
    meta: {title: "Marketing Maatwerk"},
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
    meta: {title: "Log in"},
  },
  {
    path: "/login/forgor",
    name: "forgotPassword",
    component: () => import("@/pages/login/ForgotPassword.vue"),
    meta: {title: "Forgot password"},
  },
  {
    path: "/login/confirm",
    name: "resendConfirmation",
    component: () => import("@/pages/login/ResendConfirmation.vue"),
    meta: {title: "Resend confirmation"},
  },
  {
    path: "/account",
    name: "account",
    component: () => import("@/pages/login/Account.vue"),
    meta: {title: "Account", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.hub,
    name: "accountSecurity",
    component: () => import("@/pages/login/Security.vue"),
    meta: {title: "Security", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.password,
    name: "accountPassword",
    component: () => import("@/pages/login/security/Password.vue"),
    meta: {title: "Password", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.email,
    name: "accountEmail",
    component: () => import("@/pages/login/security/Email.vue"),
    meta: {title: "Email address", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.twoFactor,
    name: "accountTwoFactor",
    component: () => import("@/pages/login/security/TwoFactor.vue"),
    meta: {title: "Two-factor", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.setUp,
    name: "accountTwoFactorSetUp",
    component: () => import("@/pages/login/security/SetUp.vue"),
    meta: {title: "Set up two-factor", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.signIns,
    name: "accountSignIns",
    component: () => import("@/pages/login/security/SignIns.vue"),
    meta: {title: "Where you are signed in", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.log,
    name: "accountSecurityLog",
    component: () => import("@/pages/login/security/Log.vue"),
    meta: {title: "Security log", requiresAuth: true},
  },
  {
    path: SECURITY_PAGES.required,
    name: "twoFactorRequired",
    component: () => import("@/pages/login/SetUpRequired.vue"),
    meta: {title: "Set up two-factor", requiresAuth: true},
    // Only a granted role waiting on two-factor sets up without the password; everybody else gives it.
    beforeEnter: (to) =>
      store.getters.twoFactorRequired ? true : {path: SECURITY_PAGES.setUp, query: to.query},
  },
  {
    path: "/account/two-factor",
    name: "twoFactorOffer",
    component: () => import("@/pages/login/TwoFactorOffer.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/account/lock",
    name: "lockAccount",
    component: () => import("@/pages/login/LockAccount.vue"),
  },
  {
    path: "/account/confirm-email",
    name: "confirmEmail",
    component: () => import("@/pages/login/ConfirmEmail.vue"),
  },
  {
    path: "/account/re-enrol",
    name: "reenrol",
    component: () => import("@/pages/login/Reenrol.vue"),
  },
  {
    path: "/account/games",
    name: "accountGames",
    component: () => import("@/pages/login/AccountGames.vue"),
    meta: {title: "Esports Teams", requiresAuth: true},
  },
  {
    path: "/account/create",
    name: "accountCreation",
    component: () => import("@/pages/login/CreateAccount.vue"),
    meta: {title: "Create account"},
  },
  {
    path: "/account/reset-password",
    name: "resetPassword",
    component: () => import("@/pages/login/ResetPassword.vue"),
    meta: {title: "Reset password"},
  },
  {
    path: "/account/activate/member",
    name: "activateMember",
    component: () => import("@/pages/activate/ActivateMember.vue"),
    meta: {title: "Activate membership"},
  },
  {
    path: "/account/activate/user",
    name: "activateUser",
    component: () => import("@/pages/activate/ActivateUser.vue"),
    meta: {title: "Activate account"},
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
    meta: {title: "Address", requiresAuth: true},
  },
  {
    path: "/events",
    name: "events",
    component: () => import("@/pages/Events.vue"),
    meta: {title: "Events"},
    // An event had no page of its own, so links named it by `#<id>` or `?event=<id>`.
    beforeEnter: (to) => {
      const asked = /^#(\d+)$/u.exec(to.hash)?.[1] ?? (typeof to.query.event === "string" ? to.query.event : "")
      return /^\d+$/u.test(asked) ? {path: `/events/${asked}`, replace: true} : true
    },
  },
  {
    path: "/events/:id(\\d+)",
    name: "event",
    component: () => import("@/pages/events/EventPage.vue"),
    meta: {title: "Event"},
  },
  {
    path: "/events/past",
    name: "events/past",
    component: () => import("@/pages/events/PastEvents.vue"),
    meta: {title: "Past events"},
  },
  {
    path: "/events/calendar",
    redirect: "/events",
  },
  {
    path: "/events/create",
    name: "createEvent",
    component: () => import("@/pages/events/EditEvent.vue"),
    meta: {title: "Create event", requiresAuth: true},
  },
  {
    path: "/events/edit/:id",
    name: "editEvent",
    component: () => import("@/pages/events/EditEvent.vue"),
    meta: {title: "Edit event", requiresAuth: true},
  },
  {
    path: "/events/signups/:id",
    name: "eventSignUps",
    component: () => import("@/pages/events/EventSignUps.vue"),
    meta: {title: "Sign-ups", requiresAuth: true},
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
    meta: {title: "Circuit Showdown"},
  },
  {
    path: "/user-manager",
    name: "userManager",
    component: () => import("@/pages/management/UserManager.vue"),
    meta: {title: "Manage users", requiresAuth: true},
  },
  {
    path: "/addresses/manage",
    name: "addressManager",
    component: () => import("@/pages/management/AddressManager.vue"),
    meta: {title: "Manage addresses", requiresAuth: true},
  },
  {
    path: "/recovery/manage",
    name: "recoveryManager",
    component: () => import("@/pages/management/RecoveryManager.vue"),
    meta: {title: "Manage account recovery", requiresAuth: true},
  },
  {
    path: "/management/jobs",
    name: "jobManager",
    component: () => import("@/pages/management/JobManager.vue"),
    meta: {title: "Manage jobs", requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/emails",
    name: "emailManager",
    component: () => import("@/pages/management/EmailManager.vue"),
    meta: {title: "Manage emails", requiresAuth: true, requiresBoard: true},
  },
  {
    // The esports manager is gone: seasons, teams and line-ups are edited on the pages that
    // show them. A bookmark to it lands on those pages rather than on nothing.
    path: "/management/esports",
    redirect: "/competition",
  },
  {
    path: "/management/cohorts",
    name: "cohortDashboard",
    component: () => import("@/pages/management/CohortDashboard.vue"),
    meta: {title: "Manage cohorts", requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/targets",
    name: "cohortTargets",
    component: () => import("@/pages/management/CohortTargets.vue"),
    meta: {title: "Cohort targets", requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/subjects/:id",
    name: "cohortSubjectDetail",
    component: () => import("@/pages/management/CohortSubjectDetail.vue"),
    meta: {title: "Cohort subject", requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/management/cohorts/:category",
    name: "cohortCategory",
    component: () => import("@/pages/management/CohortCategory.vue"),
    meta: {title: "Cohort category", requiresAuth: true, requiresAdmin: true},
  },
  {
    path: "/blogs",
    name: "BlogList",
    component: () => import("@/pages/blogs/BlogsView.vue"),
    meta: {title: "Newsletters"},
  },
  {
    path: "/blogs/:id",
    name: "BlogView",
    component: () => import("@/pages/blogs/BlogView.vue"),
    meta: {title: "Newsletter"},
  },
  {
    path: "/myapps",
    name: "myApps",
    component: () => import("@/pages/MyApps.vue"),
    meta: {title: "My apps", requiresAuth: true},
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
    meta: {title: "Unauthorized", bare: true},
  },
  // Dev only: the fields and the parts drawn on one page each, so they can be argued over away
  // from the page that needed them. The routes are registered nowhere else, so nothing ships.
  ...(import.meta.env.DEV
    ? [{
        path: "/design/fields",
        name: "design/fields",
        component: () => import("@/pages/design/FieldGallery.vue"),
        meta: {title: "Fields"},
      }, {
        path: "/design/parts",
        name: "design/parts",
        component: () => import("@/pages/design/PartsGallery.vue"),
        meta: {title: "Parts"},
      }]
    : []),
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: () => import("@/pages/NotFound.vue"),
    meta: {title: "Page not found"},
  },
]

const router = createRouter({
  history: createWebHistory("/"),
  /**
   * Where a page opens: at the top, unless the reader is already standing somewhere.
   *
   * A saved position is answered first, being the only one of the three that knows where the
   * reader had got to. Changing the query on the page already open is not arriving anywhere: the
   * season lives in the url, so choosing one is a navigation to the router, and scrolling to the
   * top would throw the reader back up the page each time. `false` rather than the offset they
   * are already at — nothing to do, rather than a scroll that cancels out and is visible under a
   * smooth-scrolling setting.
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

/**
 * Whether the reader has signed in on this browser, which is not the same question as whether
 * their auth token is still inside its own 24h life. The api rebuilds the security context from
 * the `SESSION` cookie once the token lapses, and keeps a sign-in for `session.timeout` — 30 days
 * — so a guard reading the token's expiry sends readers to the login page a day into a session
 * every request would still have been answered. The api decides; a refusal arrives as a 401 and
 * is said in a snackbar with a Login action, which is the one place that decision is made.
 */
router.beforeEach((to) => {
  const login = store.getters.getLogin
  if (to.meta.requiresAuth && login == null) {
    return {
      path: "/login",
      query: {redirect: to.fullPath},
    }
  }
  if (store.getters.twoFactorRequired && !TWO_FACTOR_SET_UP_OPEN.has(to.path)) {
    return {path: SECURITY_PAGES.required, query: {redirect: to.fullPath}}
  }
  if (to.meta.requiresAdmin && !store.getters.isAdmin) {
    return {path: "/"}
  }
  if (to.meta.requiresBoard && !(store.getters.isBoard || store.getters.isAdmin)) {
    return {path: "/"}
  }
  // Nothing returned is the navigation going ahead.
  return true
})

const TWO_FACTOR_SET_UP_OPEN = new Set([SECURITY_PAGES.required, "/login", "/account/lock", "/account/re-enrol"])

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

// A query change on the open page keeps the title a page set for itself, such as an event's name.
router.afterEach((to, from, failure) => {
  if (failure || to.path === from.path) return
  document.title = tabTitle(to.meta.title)
})

export default router
