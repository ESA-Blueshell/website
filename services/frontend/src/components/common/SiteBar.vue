<template>
  <v-app-bar theme="dark">
    <v-btn
      v-if="display.mdAndDown.value"
      class="ml-2"
      icon="mdi-menu"
      @click="drawer = !drawer"
    />

    <router-link to="/">
      <img
        alt="Blueshell logo"
        class="mr-2"
        src="@/assets/topbarlogo.png"
        style="max-height: 64px;width: 100%"
      >
    </router-link>

    <div
      v-if="!display.mdAndDown.value"
      style="height: 90%"
    >
      <v-btn
        class="bar-button"
        to="/"
      >
        Home
      </v-btn>
      <v-btn
        class="bar-button"
        to="/membership"
      >
        Membership
      </v-btn>
      <v-menu
        :offset="3"
        :open-on-hover="true"
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            class="bar-button"
            to="/aboutus"
            v-bind="props"
          >
            Association
            <v-icon>mdi-chevron-down</v-icon>
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
        class="bar-button"
        to="/events"
      >
        Events
      </v-btn>
      <v-menu
        :offset="3"
        :open-on-hover="true"
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            class="bar-button"
            to="/esports/competitive-scene"
            v-bind="props"
          >
            Esports
            <v-icon>mdi-chevron-down</v-icon>
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
        open-delay="0"
      >
        <template #activator="{ props }">
          <v-btn
            class="bar-button"
            to="/partners/become-a-partner"
            v-bind="props"
          >
            Partners
            <v-icon>mdi-chevron-down</v-icon>
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
        class="bar-button"

        to="/contact"
      >
        Contact
      </v-btn>
    </div>

    <v-spacer />

    <div style="height: 90%;display: flex;align-items: center;flex-wrap: nowrap;">
      <!--  Dark mode toggle    -->
      <v-btn
        :class="{'roll-on': darkMode,'roll-off': !darkMode }"
        :color="darkMode ? 'accent' : 'white'"
        :icon="darkMode ? 'mdi-moon-waxing-crescent' : 'mdi-white-balance-sunny'"
        class="mr-2"
        @click="emit('toggleDarkMode')"
      />

      <!-- LOGIN BUTTON/ACCOUNT DROPDOWN MENU -->
      <v-btn
        v-if="!isLoggedIn"
        class="bar-button ma-0 mr-2"
        to="/login"
      >
        Log In
      </v-btn>
      <v-menu
        v-if="isBoard || isAdmin"
        :offset="3"
      >
        <template #activator="{ props }">
          <v-btn
            class="bar-button ma-0 mr-2"
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
      >
        <template #activator="{ props }">
          <v-btn
            class="bar-button ma-0 mr-2"
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
      <v-btn
        href="mailto:board@blueshell.utwente.nl"
        icon="mdi-email"
        style="width: calc(100%/3)"
        variant="plain"
      />
      <v-btn
        href="https://www.instagram.com/esablueshell/"
        icon="mdi-instagram"
        style="width: calc(100%/3)"
        target="_blank"
        variant="plain"
      />
      <v-btn
        href="https://www.facebook.com/BlueshellEsports/"
        icon="mdi-facebook"
        style="width: calc(100%/3)"
        target="_blank"
        variant="plain"
      />
      <v-btn
        href="https://www.twitch.tv/blueshellesports"
        icon="mdi-twitch"
        style="width: calc(100%/3)"
        target="_blank"
        variant="plain"
      />
      <v-btn
        href="https://twitter.com/BlueshellESA"
        icon="mdi-twitter"
        style="width: calc(100%/3)"
        target="_blank"
        variant="plain"
      />
      <v-btn
        href="https://www.linkedin.com/company/blueshell-esports"
        icon="mdi-linkedin"
        style="width: calc(100%/3)"
        target="_blank"
        variant="plain"
      />
    </template>
  </v-navigation-drawer>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {useStore} from "vuex"
import {useDisplay} from "vuetify"
import {useGames} from "@/domains/esports/island/useGames"

// The theme is marked on the document and the session is ended app-wide, both of which outlive
// this bar, so the shell owns them and the bar only carries the buttons.
const {darkMode} = defineProps<{darkMode: boolean}>()
const emit = defineEmits<{
  toggleDarkMode: []
  logOut: []
}>()

const drawer = ref<boolean>(false)

/** The esports menu lists the games the association fields, as their records report them. */
const {current: currentGames} = useGames()

const store = useStore()
const display = useDisplay()

const isLoggedIn = computed((): boolean => store.getters.isLoggedIn)
const isBoard = computed((): boolean => store.getters.isBoard)
const isAdmin = computed((): boolean => store.getters.isAdmin)
const login = computed(() => store.getters.getLogin)
</script>

<style lang="scss" scoped>
.v-btn.bar-button {
  margin: 0 2px;
  height: 100% !important;
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
</style>
