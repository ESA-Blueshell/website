import Home from '@/views/Home.vue'
import Events from '@/views/Events.vue'
import Contact from '@/views/Contact.vue'
import Committees from '@/views/Committees.vue'
import Membership from '@/views/membership/Membership.vue'
import MembershipForm from "@/views/membership/MembershipForm.vue";
import Esports from '@/views/Esports.vue'
import AboutUs from "@/views/AboutUs.vue";
import Board from "@/views/Board.vue";
import League from "@/views/esports/League.vue";
import Cs2 from "@/views/esports/Cs2.vue";
import Trackmania from "@/views/esports/Trackmania.vue";
import Valorant from "@/views/esports/Valorant.vue";
import Documents from "@/views/Documents.vue";
import ElNino from "@/views/partners/ElNino.vue";
import Partners from "@/views/partners/Partners.vue";
import NotFound from "@/views/NotFound.vue";
import Login from "@/views/login/Login.vue";
import Account from "@/views/login/Account.vue";
import ArticleEditor from "@/views/ArticleEditor.vue";
import EventManager from "@/views/events/EventManager.vue";
import EditEvent from "@/views/events/EditEvent.vue";
import EventSignUps from "@/views/events/EventSignUps.vue";
import CommitteeManager from "@/views/committee/CommitteeManager.vue";
import CreateAccount from "@/views/login/CreateAccount.vue";
import ActivateAccount from "@/views/login/ActivateAccount.vue";
import MemberManager from "@/views/login/member/MemberManager.vue";
import RocketLeague from "@/views/esports/RocketLeague.vue";
import ForgotPassword from "@/views/login/ForgotPassword.vue";
import ResetPassword from "@/views/login/ResetPassword.vue";
import EditSignUp from "@/views/EditSignUp.vue";

import {createRouter, createWebHistory} from "vue-router";
import store from './store'
import CircuitShowdown from "@/views/events/CircuitShowdown.vue";
import {useStore} from "vuex";
import BlogView from "@/views/blogs/BlogView.vue";
import BlogsView from "@/views/blogs/BlogsView.vue";


const router = createRouter({
  history: createWebHistory('/'),
  scrollBehavior(to, from, savedPosition) {
    if (savedPosition) {
      return savedPosition
    } else {
      return {x: 0, y: 0}
    }
  },
  routes: [
    {
      path: '/',
      name: 'home',
      component: Home
    },
    {
      path: '/contact',
      name: 'contact',
      component: Contact
    },
    {
      path: '/committees',
      name: 'committees',
      component: Committees
    },
    {
      path: '/committees/manage',
      name: 'committeeManager',
      component: CommitteeManager,
      meta: {requiresAuth: true}
    },
    {
      path: '/esports',
      redirect: '/esports/competitive-scene'
    },
    {
      path: '/esports/competitive-scene',
      name: 'esports',
      component: Esports
    },
    {
      path: '/membership',
      name: 'membership',
      component: Membership
    },
    {
      path: '/membership/signup',
      name: 'membership/signup',
      component: MembershipForm
    },
    {
      path: '/documents',
      name: 'documents',
      component: Documents
    },
    {
      path: '/aboutus',
      name: 'aboutus',
      component: AboutUs
    },
    {
      path: '/board',
      name: 'board',
      component: Board
    },
    {
      path: '/esports/league-of-legends',
      name: 'league',
      component: League
    },
    {
      path: '/esports/counter-strike-2',
      name: 'cs2',
      component: Cs2
    },
    {
      path: '/esports/valorant',
      name: 'valorant',
      component: Valorant
    },
    {
      path: '/esports/rocketleague',
      name: 'rocketleague',
      component: RocketLeague
    },
    {
      path: '/esports/trackmania',
      name: 'trackmania',
      component: Trackmania
    },
    {
      path: '/partners/become-a-partner',
      name: 'becomeapartner',
      component: Partners
    },
    {
      path: '/partners/el-nino',
      name: 'elnino',
      component: ElNino
    },
    {
      path: '/login',
      name: 'login',
      component: Login
    },
    {
      path: '/login/forgor',
      name: 'forgotPassword',
      component: ForgotPassword
    },
    {
      path: '/login/reset-password',
      name: 'resetPassword',
      component: ResetPassword
    },
    {
      path: '/account',
      name: 'account',
      component: Account,
      meta: {requiresAuth: true}
    },
    {
      path: '/account/create',
      name: 'accountCreation',
      component: CreateAccount,
    },
    {
      path: '/account/activate',
      name: 'activateAccount',
      component: ActivateAccount
    },
    {
      path: '/account/articleEditor',
      name: 'articleEditor',
      component: ArticleEditor,
      meta: {requiresAuth: true}
    },
    {
      path: '/events',
      name: 'events',
      component: Events
    },
    {
      path: '/events/calendar',
      redirect: '/events'
    },
    {
      path: '/events/create',
      name: 'createEvent',
      component: EditEvent,
      meta: {requiresAuth: true}
    },
    {
      path: '/events/edit/:id',
      name: 'editEvent',
      component: EditEvent,
      meta: {requiresAuth: true}
    },
    {
      path: '/events/manage',
      name: 'eventManager',
      component: EventManager,
      meta: {requiresAuth: true}
    },
    {
      path: '/events/signups/:id',
      name: 'eventSignUps',
      component: EventSignUps,
      meta: {requiresAuth: true}
    },
    {
      path: '/events/signups/edit/:accessToken',
      name: 'editSignUp',
      component: EditSignUp,
    },
    {
      path: '/events/circuitShowdown',
      name: 'circuitShowdown',
      component: CircuitShowdown,
    },
    {
      path: '/members/manage',
      name: 'memberManager',
      component: MemberManager,
      meta: {requiresAuth: true}
    },
    {
      path: '/blogs',
      name: 'BlogList',
      component: BlogsView
    },
    {
      path: '/blogs/:id',
      name: 'BlogView',
      component: BlogView
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'NotFound',
      component: NotFound
    }
  ]
});


router.beforeEach((to, from, next) => {
  const login = store.getters.getLogin
  if (to.meta.requiresAuth && (login == null || store.getters.tokenExpired)) {
    next({
      path: '/login',
      query: {redirect: to.fullPath},
    })
  } else if (login && to.fullPath === '/membership/signup' && login.roles.includes('MEMBER')) {
    store.commit('setStatusSnackbarMessage', "You are already a member!")
  } else {
    next()
  }
})


export default router
