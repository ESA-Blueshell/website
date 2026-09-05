<template>
  <v-app-bar
    class="island site-bar"
    flat
  >
    <v-btn
      v-if="display.mdAndDown.value"
      aria-label="Open the navigation menu"
      class="site-bar__icon ml-2"
      icon="mdi-menu"
      variant="text"
      @click="drawer = !drawer"
    />

    <router-link
      aria-label="Blueshell home"
      class="site-bar__logo"
      to="/"
    >
      <img
        alt="Blueshell logo"
        class="mr-2"
        :src="logo"
      >
    </router-link>

    <nav
      v-if="!display.mdAndDown.value"
      aria-label="Main"
      class="site-bar__nav"
    >
      <v-btn
        :class="{'bar-button--here': here('/')}"
        class="bar-button rounded-0"
        to="/"
        variant="text"
      >
        Home
      </v-btn>
      <v-btn
        :class="{'bar-button--here': here('/membership')}"
        class="bar-button rounded-0"
        to="/membership"
        variant="text"
      >
        Membership
      </v-btn>
      <v-menu
        :offset="3"
        :open-on-hover="true"
        content-class="island site-bar-menu"
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            :class="{'bar-button--here': here('/aboutus', '/board', '/committees', '/blogs', '/documents')}"
            class="bar-button rounded-0"
            to="/aboutus"
            v-bind="props"
            variant="text"
          >
            Association
            <v-icon class="site-bar__chevron">
              mdi-chevron-down
            </v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item to="/aboutus">
            About us
          </v-list-item>
          <v-list-item to="/board">
            Board
          </v-list-item>
          <v-list-item to="/committees">
            Committees
          </v-list-item>
          <v-list-item to="/blogs">
            Newsletters
          </v-list-item>
          <v-list-item to="/documents">
            Documents
          </v-list-item>
        </v-list>
      </v-menu>


      <v-btn
        :class="{'bar-button--here': here('/events')}"
        class="bar-button rounded-0"
        to="/events"
        variant="text"
      >
        Events
      </v-btn>
      <v-menu
        :offset="3"
        :open-on-hover="true"
        content-class="island site-bar-menu"
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            :class="{'bar-button--here': here('/esports')}"
            class="bar-button rounded-0"
            to="/esports/competitive-scene"
            v-bind="props"
            variant="text"
          >
            Esports
            <v-icon class="site-bar__chevron">
              mdi-chevron-down
            </v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item to="/esports/competitive-scene">
            Competitive scene
          </v-list-item>
          <v-list-item
            v-for="game in currentGames"
            :key="game.code"
            :to="`/esports/${game.slug}`"
          >
            {{ game.name }}
          </v-list-item>
        </v-list>
      </v-menu>
      <v-menu
        :offset="3"
        :open-on-hover="true"
        content-class="island site-bar-menu"
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            :class="{'bar-button--here': here('/partners')}"
            class="bar-button rounded-0"
            to="/partners/become-a-partner"
            v-bind="props"
            variant="text"
          >
            Partners
            <v-icon class="site-bar__chevron">
              mdi-chevron-down
            </v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item to="/partners/become-a-partner">
            Become a partner!
          </v-list-item>
          <v-list-item to="/partners/el-nino">
            El Niño – Digital Development
          </v-list-item>
          <v-list-item to="/partners/marketing-maatwerk">
            Marketing Maatwerk
          </v-list-item>
        </v-list>
      </v-menu>
      <v-btn
        :class="{'bar-button--here': here('/contact')}"
        class="bar-button rounded-0"
        to="/contact"
        variant="text"
      >
        Contact
      </v-btn>
    </nav>

    <v-spacer />

    <div class="site-bar__end">
      <!--  Dark mode toggle    -->
      <v-btn
        :aria-label="darkMode ? 'Switch to the light theme' : 'Switch to the dark theme'"
        :class="{'roll-on': darkMode,'roll-off': !darkMode }"
        :icon="darkMode ? 'mdi-moon-waxing-crescent' : 'mdi-white-balance-sunny'"
        class="site-bar__icon site-bar__icon--accent mr-2"
        variant="text"
        @click="emit('toggleDarkMode')"
      />

      <!-- LOGIN BUTTON/ACCOUNT DROPDOWN MENU -->
      <v-btn
        v-if="!isLoggedIn"
        :class="{'bar-button--here': here('/login')}"
        class="bar-button rounded-0 ma-0 mr-2"
        to="/login"
        variant="text"
      >
        Log In
      </v-btn>
      <v-menu
        v-if="isBoard || isAdmin"
        :offset="3"
        content-class="island site-bar-menu"
      >
        <template #activator="{ props }">
          <v-btn
            aria-label="Management"
            class="site-bar__icon ma-0 mr-2"
            data-testid="nav-management"
            v-bind="props"
            variant="text"
          >
            <v-icon size="x-large">
              custom:account-multiple-edit
            </v-icon>
          </v-btn>
        </template>

        <v-list>
          <v-list-item
            v-if="isBoard"
            to="/addresses/manage"
          >
            Manage addresses
          </v-list-item>
          <v-list-item
            v-if="isBoard"
            to="/recovery/manage"
          >
            Manage account recovery
          </v-list-item>
          <v-list-item
            v-if="isBoard"
            to="/committees/manage"
          >
            Manage committees
          </v-list-item>
          <v-list-item
            v-if="isBoard"
            to="/user-manager"
          >
            Manage users
          </v-list-item>
          <v-list-item
            v-if="isAdmin"
            to="/management/jobs"
          >
            Manage jobs
          </v-list-item>
          <v-list-item
            v-if="isBoard || isAdmin"
            to="/management/emails"
          >
            Manage emails
          </v-list-item>
          <v-list-item
            v-if="isAdmin"
            to="/management/cohorts"
          >
            Manage cohorts
          </v-list-item>
        </v-list>
      </v-menu>
      <v-menu
        v-if="isLoggedIn"
        :offset="3"
        content-class="island site-bar-menu"
      >
        <template #activator="{ props }">
          <v-btn
            aria-label="Your account"
            class="site-bar__icon ma-0 mr-2"
            v-bind="props"
            variant="text"
          >
            <v-icon
              size="x-large"
            >
              mdi-account
            </v-icon>
          </v-btn>
        </template>
        <v-list>
          <v-list-item to="/account">
            Account
          </v-list-item>
          <v-list-item :to="{ name: 'editAddress', params: { id: login.addressId } }">
            Address
          </v-list-item>
          <v-list-item @click="emit('logOut')">
            Log Out
          </v-list-item>
        </v-list>
      </v-menu>
    </div>
  </v-app-bar>

  <v-navigation-drawer
    v-model="drawer"
    class="island site-bar-drawer"
    temporary
  >
    <v-list
      class="pa-2"
      nav
    >
      <v-list-item to="/">
        Home
      </v-list-item>
      <v-list-item to="/membership">
        Membership
      </v-list-item>
      <v-list-group>
        <!-- why the fuck do list-groups not get a bottom margin but list items do what the fuck it's like they don't want us to use them in a navbar aaaaa -->
        <template #activator="{ props }">
          <v-list-item v-bind="props">
            Association
          </v-list-item>
        </template>

        <v-list-item to="/aboutus">
          About
        </v-list-item>
        <v-list-item to="/board">
          Board
        </v-list-item>
        <v-list-item to="/committees">
          Committees
        </v-list-item>
        <v-list-item to="/documents">
          Documents
        </v-list-item>
        <v-list-item to="/blogs">
          Newsletters
        </v-list-item>
        <v-divider class="mb-1" />
      </v-list-group>


      <v-list-group>
        <template #activator="{ props }">
          <v-list-item v-bind="props">
            Events
          </v-list-item>
        </template>
        <v-list-item to="/events">
          Events
        </v-list-item>
        <v-list-item
          to="/events/circuitShowdown"
        >
          Circuit Showdown
        </v-list-item>
        <v-divider class="mb-1" />
      </v-list-group>

      <v-list-group>
        <template #activator="{ props }">
          <v-list-item v-bind="props">
            Esports
          </v-list-item>
        </template>
        <v-list-item to="/esports/competitive-scene">
          Competitive scene
        </v-list-item>
        <v-list-item
          v-for="game in currentGames"
          :key="game.code"
          :to="`/esports/${game.slug}`"
        >
          {{ game.name }}
        </v-list-item>
        <v-divider class="mb-1" />
      </v-list-group>

      <v-list-group>
        <template #activator="{ props }">
          <v-list-item v-bind="props">
            Partners
          </v-list-item>
        </template>
        <v-list-item to="/partners/become-a-partner">
          Become a partner!
        </v-list-item>
        <v-list-item to="/partners/el-nino">
          El Niño – Digital Development
        </v-list-item>
        <v-list-item to="/partners/marketing-maatwerk">
          Marketing Maatwerk
        </v-list-item>
        <v-divider dark />
      </v-list-group>

      <v-list-item to="/contact">
        Contact
      </v-list-item>
    </v-list>

    <template #append>
      <div class="site-bar-drawer__social">
        <v-btn
          aria-label="Email the board"
          href="mailto:board@blueshell.utwente.nl"
          icon="mdi-email"
          variant="plain"
        />
        <v-btn
          aria-label="Instagram"
          href="https://www.instagram.com/esablueshell/"
          icon="mdi-instagram"
          target="_blank"
          variant="plain"
        />
        <v-btn
          aria-label="Facebook"
          href="https://www.facebook.com/BlueshellEsports/"
          icon="mdi-facebook"
          target="_blank"
          variant="plain"
        />
        <v-btn
          aria-label="Twitch"
          href="https://www.twitch.tv/blueshellesports"
          icon="mdi-twitch"
          target="_blank"
          variant="plain"
        />
        <v-btn
          aria-label="Twitter"
          href="https://twitter.com/BlueshellESA"
          icon="mdi-twitter"
          target="_blank"
          variant="plain"
        />
        <v-btn
          aria-label="LinkedIn"
          href="https://www.linkedin.com/company/blueshell-esports"
          icon="mdi-linkedin"
          target="_blank"
          variant="plain"
        />
      </div>
    </template>
  </v-navigation-drawer>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {useStore} from "vuex"
import {useRoute} from "vue-router"
import {useDisplay} from "vuetify"
import {useGames} from "@/domains/esports/island/useGames"
import logoOnDark from "@/assets/topbarlogo.png"
import logoOnLight from "@/assets/topbarlogo-light.png"

// The theme is marked on the document and the session is ended app-wide, both of which outlive
// this bar, so the shell owns them and the bar only carries the buttons.
const {darkMode} = defineProps<{darkMode: boolean}>()

/**
 * The wordmark is drawn for the ground it sits on.
 *
 * One lockup in two inks. The letters sit on a black slab: on the dark bar that slab is the
 * bar, so it reads as letters, and the same file on a light bar leaves the slab behind as a
 * black box. The light copy has no slab and inks the letters instead. The shell is identical
 * in both.
 */
const logo = computed<string>(() => (darkMode ? logoOnDark : logoOnLight))
const emit = defineEmits<{
  toggleDarkMode: []
  logOut: []
}>()

const drawer = ref<boolean>(false)

/** The esports menu lists the games the association fields, as their records report them. */
const {current: currentGames} = useGames()

const store = useStore()
const display = useDisplay()
const route = useRoute()

/**
 * Whether the reader is under one of these sections, which is what the bar marks.
 *
 * Read off the path rather than off Vuetify's own active class: an entry that opens a menu
 * addresses one page of its section, so `/esports/valorant` would leave Esports unmarked.
 */
const here = (...sections: string[]): boolean => sections.some(section =>
  section === "/"
    ? route.path === "/"
    : route.path === section || route.path.startsWith(`${section}/`))

const isLoggedIn = computed((): boolean => store.getters.isLoggedIn)
const isBoard = computed((): boolean => store.getters.isBoard)
const isAdmin = computed((): boolean => store.getters.isAdmin)
const login = computed(() => store.getters.getLogin)
</script>

<style lang="scss" scoped>
/*
 * The bar is the island's top edge, so everything visible here is a token island.css already
 * sets and the light half already overrides: one theme change moves the bar and the page under
 * it together. The `island` class on the root is what puts those overrides in reach.
 */

.site-bar.v-app-bar {
  /* Glass over whatever is scrolling past, the same idiom as .island-plus. */
  background: color-mix(in oklab, var(--color-pit) 88%, transparent);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--color-hairline);
  color: var(--color-chalk);
  /* The island's root fills its container; a fixed bar's container is the window. */
  min-height: 0;
}

.site-bar__logo img {
  display: block;
  max-height: 44px;
  width: auto;
}

.site-bar__nav,
.site-bar__end {
  display: flex;
  align-items: stretch;
  flex-wrap: nowrap;
  height: 100%;
}

.site-bar__end {
  align-items: center;
}

/*
 * A label with a rule under it, and nothing else: the rule is transparent at rest, faint under
 * the pointer and the accent where the reader already is. One device, carrying one fact.
 */
.site-bar :deep(.v-btn.bar-button) {
  height: 100%;
  min-width: 0;
  padding: 0 0.7rem;
  color: var(--color-ash);
  font-size: 0.8125rem;
  letter-spacing: 0.06em;
  box-shadow: inset 0 -2px 0 transparent;
  transition:
    color 180ms var(--ease-out-quint),
    box-shadow 180ms var(--ease-out-quint);
}

/* housestyle.scss rings a hovered, open or current button in the accent, at a specificity
   nothing here can reach: up here the rule under the label says it instead. */
.site-bar :deep(.v-btn) {
  border-color: transparent !important;
}

/* Vuetify washes a button on hover; here the rule says it, so the wash would only muddy it. */
.site-bar :deep(.v-btn .v-btn__overlay) {
  display: none;
}

.site-bar :deep(.v-btn.bar-button:hover),
.site-bar :deep(.v-btn.bar-button:focus-visible) {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 color-mix(in oklab, var(--color-chalk) 32%, transparent);
}

.site-bar :deep(.v-btn.bar-button--here) {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-eyebrow);
}

.site-bar__chevron {
  margin-left: 0.1rem;
  font-size: 1rem;
  opacity: 0.7;
}

.site-bar :deep(.v-btn.site-bar__icon) {
  color: var(--color-ash);
  transition: color 180ms var(--ease-out-quint);
}

.site-bar :deep(.v-btn.site-bar__icon:hover),
.site-bar :deep(.v-btn.site-bar__icon:focus-visible),
.site-bar :deep(.v-btn.site-bar__icon[aria-expanded="true"]) {
  color: var(--color-chalk);
}

/* The one place the accent is spent up here, on the control that moves the whole theme. */
.site-bar :deep(.v-btn.site-bar__icon--accent) {
  color: var(--color-eyebrow);
}

.roll-off {
  animation: rotate-out 0.5s both;
}

.roll-on {
  animation: rotate-in 0.5s both;
}

@keyframes rotate-in {
  0% {
    transform: rotate(-45deg);
  }
  50% {
    transform: rotate(22.5deg);
  }
  100% {
    transform: rotate(0);
  }
}


@keyframes rotate-out {
  0% {
    transform: rotate(-45deg);
  }
  50% {
    transform: rotate(22.5deg);
  }
  100% {
    transform: rotate(0);
  }
}

/* The toggle rolls to show which way it went; somebody who asked for stillness is told by the
   icon itself. */
@media (prefers-reduced-motion: reduce) {
  .roll-off,
  .roll-on {
    animation: none;
  }

  .site-bar :deep(.v-btn) {
    transition: none;
  }
}

/* The drawer is a slab of the island rather than glass: at phone width it is the navigation,
   not a shelf over the page. */
.site-bar-drawer.v-navigation-drawer {
  background: var(--color-pit);
  border-color: var(--color-hairline);
  color: var(--color-chalk);
  font-family: var(--font-body);
}

.site-bar-drawer :deep(.v-list) {
  background: transparent;
}

.site-bar-drawer :deep(.v-list-item) {
  border: 1px solid transparent;
  border-radius: 0;
  color: var(--color-ash);
  letter-spacing: 0.02em;
}

.site-bar-drawer :deep(.v-list-item:hover),
.site-bar-drawer :deep(.v-list-item:focus-visible) {
  color: var(--color-chalk);
  background: color-mix(in oklab, var(--color-chalk) 7%, transparent);
}

.site-bar-drawer :deep(.v-list-item--active) {
  color: var(--color-chalk);
  box-shadow: inset 2px 0 0 var(--color-eyebrow);
}

.site-bar-drawer :deep(.v-list-item__overlay) {
  display: none;
}

/* Compounded to outrank housestyle.scss, which paints every divider in the dark half acid. */
.site-bar-drawer.v-navigation-drawer :deep(.v-divider) {
  border-color: var(--color-hairline);
}

.site-bar-drawer__social {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  border-top: 1px solid var(--color-hairline);
}

.site-bar-drawer__social :deep(.v-btn) {
  width: 100%;
  color: var(--color-ash);
}

.site-bar-drawer__social :deep(.v-btn:hover),
.site-bar-drawer__social :deep(.v-btn:focus-visible) {
  color: var(--color-chalk);
  opacity: 1;
}
</style>

<style lang="scss">
/*
 * Unscoped on purpose: a menu's list is teleported to the end of the body, so a scoped rule
 * would not reach it. `island` on the same element is what carries the light half's tokens
 * out there with it, and the two rules below undo what else it brings: the island's root
 * fills its container and paints the page's tile, and out here the container is the window.
 */
.site-bar-menu {
  min-height: 0;
  background: none;
}

/* Compounded on the overlay's own class: the panel is square, like every other island
   surface, and Vuetify rounds a menu's list at the same specificity. */
.site-bar-menu.v-overlay__content .v-list {
  padding: 0.25rem;
  background: var(--color-surface);
  border: 1px solid var(--color-hairline);
  border-radius: 0;
  box-shadow: 0 18px 40px rgb(0 0 0 / 35%);
  color: var(--color-chalk);
  font-family: var(--font-body);
}

.site-bar-menu .v-list-item {
  min-height: 0;
  padding: 0.45rem 0.85rem;
  border: 1px solid transparent;
  border-radius: 0;
  color: var(--color-ash);
  font-size: 0.875rem;
  letter-spacing: 0.02em;
}

.site-bar-menu .v-list-item:hover,
.site-bar-menu .v-list-item:focus-visible {
  color: var(--color-chalk);
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.site-bar-menu .v-list-item--active {
  color: var(--color-chalk);
  box-shadow: inset 2px 0 0 var(--color-eyebrow);
}

.site-bar-menu .v-list-item__overlay {
  display: none;
}
</style>
