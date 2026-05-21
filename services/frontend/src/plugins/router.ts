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
  {
    path: "/esports/league-of-legends",
    name: "league",
    component: () => import("@/pages/esports/League.vue"),
  },
  {
    path: "/esports/counter-strike-2",
    name: "cs2",
    component: () => import("@/pages/esports/Cs2.vue"),
  },
  {
    path: "/esports/valorant",
    name: "valorant",
    component: () => import("@/pages/esports/Valorant.vue"),
  },
  {
    path: "/esports/rocketleague",
    name: "rocketleague",
    component: () => import("@/pages/esports/RocketLeague.vue"),
  },
  {
    path: "/esports/geoguessr",
    name: "geoguessr",
    component: () => import("@/pages/esports/Geoguessr.vue"),
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
    // site chrome by default — they're regular pages a logged-out user
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
    path: "/members/manage",
    name: "memberManager",
    component: () => import("@/pages/management/MemberManager.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/contributions/manage",
    name: "contributionManager",
    component: () => import("@/pages/management/ContributionManager.vue"),
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
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
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
  } else {
    next()
  }
})

export default router
