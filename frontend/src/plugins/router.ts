import Home from "@/pages/Home.vue"
import Events from "@/pages/Events.vue"
import Contact from "@/pages/Contact.vue"
import Committees from "@/pages/Committees.vue"
import Membership from "@/pages/membership/Membership.vue"
import MembershipSignUp from "@/pages/membership/MembershipSignUp.vue"
import Esports from "@/pages/Esports.vue"
import AboutUs from "@/pages/AboutUs.vue"
import Board from "@/pages/Board.vue"
import League from "@/pages/esports/League.vue"
import Cs2 from "@/pages/esports/Cs2.vue"
import Valorant from "@/pages/esports/Valorant.vue"
import Documents from "@/pages/Documents.vue"
import ElNino from "@/pages/partners/ElNino.vue"
import MarketingMaatwerk from "@/pages/partners/MarketingMaatwerk.vue"
import Partners from "@/pages/partners/Partners.vue"
import NotFound from "@/pages/NotFound.vue"
import Login from "@/pages/login/Login.vue"
import Account from "@/pages/account/Account.vue"
import EditEvent from "@/pages/events/EditEvent.vue"
import EventSignUps from "@/pages/events/EventSignUps.vue"
import CommitteeManager from "@/pages/management/CommitteeManager.vue"
import CreateAccount from "@/pages/account/CreateAccount.vue"
import MemberManager from "@/pages/management/MemberManager.vue"
import ContributionManager from "@/pages/management/ContributionManager.vue"
import RocketLeague from "@/pages/esports/RocketLeague.vue"
import ResetPassword from "@/pages/login/ResetPassword.vue"
import {createRouter, createWebHistory, type RouteRecordRaw} from "vue-router"
import store from "./store"
import CircuitShowdown from "@/pages/events/CircuitShowdown.vue"
import BlogView from "@/pages/blogs/BlogView.vue"
import BlogsView from "@/pages/blogs/BlogsView.vue"
import ActivateMember from "@/pages/activate/ActivateMember.vue"
import ActivateUser from "@/pages/activate/ActivateUser.vue"
import Geoguessr from "@/pages/esports/Geoguessr.vue"
import ForgotPassword from "@/pages/login/ForgotPassword.vue"
import EventSignUpForm from "@/components/form/EventSignUpForm.vue"
import RecoveryManager from "@/pages/management/RecoveryManager.vue"
import AddressManager from "@/pages/management/AddressManager.vue"

const routes: RouteRecordRaw[] = [
  {
    path: "/",
    name: "home",
    component: Home,
  },
  {
    path: "/contact",
    name: "contact",
    component: Contact,
  },
  {
    path: "/committees",
    name: "committees",
    component: Committees,
  },
  {
    path: "/committees/manage",
    name: "committeeManager",
    component: CommitteeManager,
    meta: {requiresAuth: true},
  },
  {
    path: "/esports",
    redirect: "/esports/competitive-scene",
  },
  {
    path: "/esports/competitive-scene",
    name: "esports",
    component: Esports,
  },
  {
    path: "/membership",
    name: "membership",
    component: Membership,
  },
  {
    path: "/membership/signup",
    name: "membership/signup",
    component: MembershipSignUp,
  },
  {
    path: "/documents",
    name: "documents",
    component: Documents,
  },
  {
    path: "/aboutus",
    name: "aboutus",
    component: AboutUs,
  },
  {
    path: "/board",
    name: "board",
    component: Board,
  },
  {
    path: "/esports/league-of-legends",
    name: "league",
    component: League,
  },
  {
    path: "/esports/counter-strike-2",
    name: "cs2",
    component: Cs2,
  },
  {
    path: "/esports/valorant",
    name: "valorant",
    component: Valorant,
  },
  {
    path: "/esports/rocketleague",
    name: "rocketleague",
    component: RocketLeague,
  },
  {
    path: "/esports/geoguessr",
    name: "geoguessr",
    component: Geoguessr,
  },
  {
    path: "/partners/become-a-partner",
    name: "becomeapartner",
    component: Partners,
  },
  {
    path: "/partners/el-nino",
    name: "elnino",
    component: ElNino,
  },
  {
    path: "/partners/marketing-maatwerk",
    name: "marketingmaatwerk",
    component: MarketingMaatwerk,
  },
  {
    path: "/login",
    name: "login",
    component: Login,
  },
  {
    path: "/login/forgor",
    name: "forgotPassword",
    component: ForgotPassword,
  },
  {
    path: "/account",
    name: "account",
    component: Account,
    meta: {requiresAuth: true},
  },
  {
    path: "/account/create",
    name: "accountCreation",
    component: CreateAccount,
  },
  {
    path: "/account/reset-password",
    name: "resetPassword",
    component: ResetPassword,
  },
  {
    path: "/account/activate/member",
    name: "activateMember",
    component: ActivateMember,
  },
  {
    path: "/account/activate/user",
    name: "activateUser",
    component: ActivateUser,
  },
  // TODO: Remove the below redirects if you encounter this text after 01-01-2025, as all links will have expired
  //       LEGACY: Redirect old path-param links to the new query-param routes
  {
    path: "/account/reset-password/:username/:token",
    redirect: (to) => ({
      name: "resetPassword",
      query: {
        username: String(to.params.username ?? ""),
        token: String(to.params.token ?? ""),
      },
    }),
  },
  {
    path: "/account/activate/member/:token",
    redirect: (to) => ({
      name: "activateMember",
      query: {token: String(to.params.token ?? "")},
    }),
  },
  {
    path: "/account/activate/user/:username/:token",
    redirect: (to) => ({
      name: "activateUser",
      query: {
        username: String(to.params.username ?? ""),
        token: String(to.params.token ?? ""),
      },
    }),
  },
  {
    path: "/events",
    name: "events",
    component: Events,
  },
  {
    path: "/events/calendar",
    redirect: "/events",
  },
  {
    path: "/events/create",
    name: "createEvent",
    component: EditEvent,
    meta: {requiresAuth: true},
  },
  {
    path: "/events/edit/:id",
    name: "editEvent",
    component: EditEvent,
    meta: {requiresAuth: true},
  },
  {
    path: "/events/signups/:id",
    name: "eventSignUps",
    component: EventSignUps,
    meta: {requiresAuth: true},
  },
  {
    path: "/events/signups/edit/:accessToken",
    name: "editSignUp",
    component: EventSignUpForm,
  },
  {
    path: "/events/circuitShowdown",
    name: "circuitShowdown",
    component: CircuitShowdown,
  },
  {
    path: "/members/manage",
    name: "memberManager",
    component: MemberManager,
    meta: {requiresAuth: true},
  },
  {
    path: "/contributions/manage",
    name: "contributionManager",
    component: ContributionManager,
    meta: {requiresAuth: true},
  },
  {
    path: "/addresses/manage",
    name: "addressManager",
    component: AddressManager,
    meta: {requiresAuth: true},
  },
  {
    path: "/recovery/manage",
    name: "recoveryManager",
    component: RecoveryManager,
    meta: {requiresAuth: true},
  },
  {
    path: "/blogs",
    name: "BlogList",
    component: BlogsView,
  },
  {
    path: "/blogs/:id",
    name: "BlogView",
    component: BlogView,
  },
  {
    path: "/:pathMatch(.*)*",
    name: "NotFound",
    component: NotFound,
  },
]

const router = createRouter({
  history: createWebHistory("/"),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return {left: 0, top: 0}
    }
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
  } else {
    next()
  }
})

export default router
