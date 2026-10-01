import {createRouter, createWebHistory, type RouteLocationNormalized, type RouteRecordRaw} from "vue-router"
import {SECURITY_PAGES} from "@/domains/auth/securityPages"
import store from "./store"
import {tabTitle} from "./tabTitle"

declare module "vue-router" {
  interface RouteMeta {
    /** The page's name in the browser tab; a page that knows a better one sets it once loaded. */
    title?: string
    /** Where an editor the site and Management share goes back to when it is opened inside Management. */
    portal?: string
  }
}

/** The Brevo list a link naming a cohort means, or Brevo where the cohort has none. */
export async function cohortListRoute(to: RouteLocationNormalized): Promise<string> {
  const {TargetSystem, fetchCohort} = await import("@/domains/cohorts")
  const cohort = await fetchCohort(Number(to.params.id)).catch(() => null)
  const externalId = cohort?.mappings.find((mapping) => mapping.system === TargetSystem.BREVO)?.externalId
  return externalId ? `/management/platforms/brevo/lists/${externalId}` : "/management/platforms/brevo"
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
  // The manager's work happens on each committee's own page now.
  {
    path: "/committees/manage",
    redirect: "/committees",
  },
  {
    path: "/committees/new",
    name: "committeeNew",
    component: () => import("@/pages/committees/CommitteeEdit.vue"),
    meta: {title: "Add a committee", requiresAuth: true},
  },
  {
    path: "/committees/:address/edit",
    name: "committeeEdit",
    component: () => import("@/pages/committees/CommitteeEdit.vue"),
    meta: {title: "Edit committee", requiresAuth: true},
  },
  {
    path: "/committees/:address",
    name: "committee",
    component: () => import("@/pages/committees/CommitteeByAddress.vue"),
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
  // A game is one record, so both areas edit it on the same page; each goes back to itself.
  {
    path: "/casual/new",
    name: "casualGameNew",
    component: () => import("@/pages/games/GameEdit.vue"),
    meta: {requiresAuth: true, area: "casual"},
  },
  {
    path: "/casual/:slug/edit",
    name: "casualGameEdit",
    component: () => import("@/pages/games/GameEdit.vue"),
    meta: {requiresAuth: true, area: "casual"},
  },
  {
    path: "/competition/new",
    name: "competitionGameNew",
    component: () => import("@/pages/games/GameEdit.vue"),
    meta: {requiresAuth: true, area: "competition"},
  },
  {
    path: "/competition/:slug/edit",
    name: "competitionGameEdit",
    component: () => import("@/pages/games/GameEdit.vue"),
    meta: {requiresAuth: true, area: "competition"},
  },
  {
    path: "/competition/seasons/new",
    name: "seasonNew",
    component: () => import("@/pages/competition/SeasonEdit.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/competition/seasons/:id/edit",
    name: "seasonEdit",
    component: () => import("@/pages/competition/SeasonEdit.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/competition/:slug/teams/new",
    name: "teamNew",
    component: () => import("@/pages/competition/TeamEdit.vue"),
    meta: {requiresAuth: true},
  },
  {
    path: "/competition/:slug/teams/:team/edit",
    name: "teamEdit",
    component: () => import("@/pages/competition/TeamEdit.vue"),
    meta: {requiresAuth: true},
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
  {
    path: "/board/new",
    name: "boardNew",
    component: () => import("@/pages/board/BoardEdit.vue"),
    meta: {title: "Add a board", requiresAuth: true},
  },
  {
    path: "/board/:number(\\d+)/edit",
    name: "boardEdit",
    component: () => import("@/pages/board/BoardEdit.vue"),
    meta: {title: "Edit board", requiresAuth: true},
  },
  {
    path: "/board/:number(\\d+)/members/new",
    name: "boardMemberNew",
    component: () => import("@/pages/board/BoardMemberEdit.vue"),
    meta: {title: "Add a member", requiresAuth: true},
  },
  {
    path: "/board/:number(\\d+)/members/:member(\\d+)/edit",
    name: "boardMemberEdit",
    component: () => import("@/pages/board/BoardMemberEdit.vue"),
    meta: {title: "Edit member", requiresAuth: true},
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
    component: () => import("@/pages/partners/Partner.vue"),
    props: {slug: "el-nino"},
    meta: {title: "El Niño"},
  },
  {
    path: "/partners/marketing-maatwerk",
    name: "marketingmaatwerk",
    component: () => import("@/pages/partners/Partner.vue"),
    props: {slug: "marketing-maatwerk"},
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
    // Management is a portal of its own (frontend ADR-009): one layout route, and each child
    // still carries its own role for the guard.
    path: "/management",
    component: () => import("@/components/management/ManagementShell.vue"),
    meta: {requiresAuth: true, requiresBoard: true, management: true},
    children: [
      {path: "", name: "management", component: () => import("@/pages/management/ManagementDashboard.vue"), meta: {title: "Overview"}},
      {path: "alerts", name: "alertList", component: () => import("@/pages/management/AlertList.vue"), meta: {title: "Alerts"}},
      {path: "more", name: "managementMore", component: () => import("@/pages/management/ManagementMore.vue"), meta: {title: "Management"}},
      {path: "users", name: "userManager", component: () => import("@/pages/management/UserManager.vue"), meta: {title: "Users"}},
      {
        path: "contributions/periods/new",
        name: "contributionPeriodNew",
        component: () => import("@/pages/management/ContributionPeriodPage.vue"),
        meta: {title: "New contribution period"},
      },
      {
        path: "contributions/periods/:id(\\d+)",
        name: "contributionPeriod",
        component: () => import("@/pages/management/ContributionPeriodPage.vue"),
        meta: {title: "Contribution period"},
      },
      {
        path: "contributions/:periodId(\\d+)/reminders",
        name: "paymentReminders",
        component: () => import("@/pages/management/PaymentReminders.vue"),
        meta: {title: "Payment reminders"},
      },
      {
        path: "contributions/:periodId(\\d+)/incasso/:runId(\\d+)?",
        name: "incassoRun",
        component: () => import("@/pages/management/IncassoRun.vue"),
        meta: {title: "Incassos"},
      },
      {
        path: "contributions/:periodId(\\d+)?",
        name: "contributions",
        component: () => import("@/pages/management/ContributionsPage.vue"),
        meta: {title: "Contributions"},
      },
      {
        path: "users/bulk/:action(start|end|paid|unpaid)",
        name: "bulkTask",
        component: () => import("@/pages/management/BulkTask.vue"),
        meta: {title: "Many members"},
      },
      {
        path: "users/:id(\\d+)/:tab(membership|contributions|profile|account|roles)?",
        name: "userDetail",
        component: () => import("@/pages/management/UserDetail.vue"),
        meta: {title: "User"},
      },
      {
        path: "recovery",
        name: "recoveryManager",
        component: () => import("@/pages/management/RecoveryManager.vue"),
        meta: {title: "Account recovery"},
      },
      // People without an address are found on Users, under Needs a look.
      {path: "addresses", redirect: "/management/users"},
      {path: "mail/sent", name: "emailManager", component: () => import("@/pages/management/SentEmails.vue"), meta: {title: "Sent"}},
      {path: "mail/inbox", name: "inbox", component: () => import("@/pages/management/InboxPage.vue"), meta: {title: "Inbox"}},
      {
        path: "mail/inbox/:id(\\d+)",
        name: "inboxMessage",
        component: () => import("@/pages/management/InboxMessage.vue"),
        meta: {title: "Inbox"},
      },
      {path: "mail/write", name: "writeEmail", component: () => import("@/pages/management/WriteEmail.vue"), meta: {title: "Write an email"}},
      {
        path: "mail/sent/:id(\\d+)",
        name: "sentEmail",
        component: () => import("@/pages/management/SentEmail.vue"),
        meta: {title: "Email"},
      },
      {
        path: "jobs",
        name: "jobManager",
        component: () => import("@/pages/management/JobManager.vue"),
        meta: {title: "Jobs", requiresAdmin: true},
      },
      {
        path: "jobs/:id(\\d+)",
        name: "jobDetail",
        component: () => import("@/pages/management/JobDetail.vue"),
        meta: {title: "Job", requiresAdmin: true},
      },
      {
        path: "exceptions",
        name: "exceptionList",
        component: () => import("@/pages/management/ExceptionList.vue"),
        meta: {title: "Exceptions", requiresAdmin: true},
      },
      {
        path: "exceptions/:id(\\d+)",
        name: "exceptionDetail",
        component: () => import("@/pages/management/ExceptionDetail.vue"),
        meta: {title: "Exception", requiresAdmin: true},
      },
      {
        path: "platforms/brevo",
        name: "brevo",
        component: () => import("@/pages/management/BrevoPage.vue"),
        meta: {title: "Brevo"},
      },
      {path: "committees", name: "managementCommittees", component: () => import("@/pages/management/CommitteeList.vue"), meta: {title: "Committees"}},
      // The site's own editor, rendered inside the portal.
      {
        path: "committees/new",
        name: "managementCommitteeNew",
        component: () => import("@/pages/committees/CommitteeEdit.vue"),
        meta: {title: "Add a committee", portal: "/management/committees"},
      },
      {
        path: "committees/:address",
        name: "managementCommittee",
        component: () => import("@/pages/committees/CommitteeEdit.vue"),
        meta: {title: "Edit committee", portal: "/management/committees"},
      },
      {path: "boards", name: "managementBoards", component: () => import("@/pages/management/BoardList.vue"), meta: {title: "Boards"}},
      {
        path: "boards/new",
        name: "managementBoardNew",
        component: () => import("@/pages/board/BoardEdit.vue"),
        meta: {title: "Add a board", portal: "/management/boards"},
      },
      {
        path: "boards/:number(\\d+)",
        name: "managementBoard",
        component: () => import("@/pages/board/BoardEdit.vue"),
        meta: {title: "Edit board", portal: "/management/boards"},
      },
      {path: "games", name: "managementGames", component: () => import("@/pages/management/GameList.vue"), meta: {title: "Games"}},
      {
        path: "games/new",
        name: "managementGameNew",
        component: () => import("@/pages/games/GameEdit.vue"),
        meta: {title: "Add a game", portal: "/management/games"},
      },
      {
        path: "games/:slug",
        name: "managementGame",
        component: () => import("@/pages/games/GameEdit.vue"),
        meta: {title: "Edit game", portal: "/management/games"},
      },
      {path: "competition", name: "managementTeams", component: () => import("@/pages/management/TeamList.vue"), meta: {title: "Competition"}},
      {
        path: "competition/:slug/teams/:team",
        name: "managementTeam",
        component: () => import("@/pages/competition/TeamEdit.vue"),
        meta: {title: "Edit team", portal: "/management/competition"},
      },
      {
        path: "platforms/discord",
        name: "discord",
        component: () => import("@/pages/management/DiscordPage.vue"),
        meta: {title: "Discord"},
      },
      {
        path: "platforms/discord/channels",
        name: "discordChannels",
        component: () => import("@/pages/management/DiscordPage.vue"),
        meta: {title: "Discord channels"},
      },
      {
        path: "platforms/discord/roles/:roleId",
        name: "discordRole",
        component: () => import("@/pages/management/DiscordRole.vue"),
        meta: {title: "Discord role"},
      },
      // Brevo's lists and cohort categories were pages of their own; every list is on Brevo now.
      {path: "platforms/brevo/lists", redirect: "/management/platforms/brevo"},
      {
        path: "platforms/brevo/lists/:externalId",
        name: "brevoList",
        component: () => import("@/pages/management/BrevoList.vue"),
        meta: {title: "Brevo list"},
      },
      // A cohort's page was its own; alerts and old links name the cohort, so they land on its list.
      {path: "platforms/brevo/cohort/:id", component: () => import("@/pages/management/BrevoList.vue"), beforeEnter: cohortListRoute},
      {path: "platforms/brevo/:category(committees|periods|members)", redirect: "/management/platforms/brevo"},
    ],
  },
  // The addresses management had before the portal; bookmarks and old emails keep working.
  {path: "/user-manager", redirect: "/management/users"},
  {path: "/addresses/manage", redirect: "/management/users"},
  {path: "/recovery/manage", redirect: "/management/recovery"},
  {path: "/management/emails", redirect: "/management/mail/sent"},
  {path: "/management/cohorts", redirect: "/management/platforms/brevo"},
  {path: "/management/cohorts/targets", redirect: "/management/platforms/brevo"},
  {path: "/management/cohort/:id", redirect: (to) => `/management/platforms/brevo/cohort/${String(to.params.id)}`},
  {path: "/management/cohorts/:category", redirect: "/management/platforms/brevo"},
  {
    // The esports manager is gone: seasons, teams and line-ups are edited on the pages that
    // show them. A bookmark to it lands on those pages rather than on nothing.
    path: "/management/esports",
    redirect: "/competition",
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
      }, {
        path: "/design/management",
        name: "design/management",
        component: () => import("@/pages/design/ManagementGallery.vue"),
        meta: {title: "Management parts"},
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
  // Inside Management a refusal says so; elsewhere the reader goes home, as before.
  const refused = to.meta.management ? {path: "/unauthorized"} : {path: "/"}
  if (to.meta.requiresAdmin && !store.getters.isAdmin) {
    return refused
  }
  if (to.meta.requiresBoard && !(store.getters.isBoard || store.getters.isAdmin)) {
    return refused
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
