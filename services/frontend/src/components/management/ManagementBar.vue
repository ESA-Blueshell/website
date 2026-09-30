<template>
  <header
    class="island mg-bar"
    data-testid="management-bar"
  >
    <router-link
      aria-label="Management"
      class="mg-bar__mark"
      to="/management"
    >
      <img
        alt="ESA Blueshell"
        :src="logo"
        :srcset="`${logo} 1x, ${logo2x} 2x, ${logo3x} 3x`"
      >
      <span class="mg-bar__word">Management</span>
    </router-link>
    <span class="mg-bar__gap" />
    <button
      :aria-label="darkMode ? 'Switch to the light theme' : 'Switch to the dark theme'"
      class="mg-bar__icon"
      data-testid="management-theme"
      type="button"
      @click="emit('toggleDarkMode')"
    >
      <nav-mark
        :mark="darkMode ? 'moon' : 'sun'"
        :size="22"
      />
    </button>
    <dropdown-menu-root :modal="false">
      <dropdown-menu-trigger
        aria-label="Your account"
        class="mg-bar__icon"
        data-testid="management-account"
      >
        <nav-mark
          mark="account"
          :size="24"
        />
      </dropdown-menu-trigger>
      <dropdown-menu-content
        align="end"
        class="mg-menu"
        :side-offset="0"
      >
        <dropdown-menu-item
          v-for="entry in account"
          :key="entry.to"
          as-child
        >
          <router-link
            class="mg-menu__entry"
            :to="entry.to"
          >
            {{ entry.label }}
          </router-link>
        </dropdown-menu-item>
        <dropdown-menu-item as-child>
          <router-link
            class="mg-menu__entry mg-menu__switch"
            data-testid="management-back-to-site"
            to="/"
          >
            Back to the site
          </router-link>
        </dropdown-menu-item>
        <dropdown-menu-item
          class="mg-menu__entry mg-menu__out"
          data-testid="management-log-out"
          @select="emit('logOut')"
        >
          Sign out
        </dropdown-menu-item>
      </dropdown-menu-content>
    </dropdown-menu-root>
  </header>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import {DropdownMenuContent, DropdownMenuItem, DropdownMenuRoot, DropdownMenuTrigger} from "reka-ui"
import NavMark from "@/components/common/NavMark.vue"
import {accountFor} from "@/components/common/nav"
import logo from "@/assets/topbarlogo-38.webp"
import logo2x from "@/assets/topbarlogo-76.webp"
import logo3x from "@/assets/topbarlogo-114.webp"

const {darkMode} = defineProps<{darkMode: boolean}>()
const emit = defineEmits<{toggleDarkMode: []; logOut: []}>()

const store = useStore()

const account = computed(() =>
  accountFor({
    loggedIn: true,
    board: store.getters.isBoard === true,
    admin: store.getters.isAdmin === true,
    addressId: store.getters.getLogin?.addressId ?? null,
  }),
)
</script>

<style scoped>
.mg-bar {
  position: sticky;
  top: 0;
  z-index: 1005;
  display: flex;
  align-items: center;
  gap: 1rem;
  height: 60px;
  /* The island class stretches to fill its parent; a bar stays its own height. */
  min-height: 0;
  padding: 0 20px 0 22px;
  background: color-mix(in oklab, var(--color-pit) 92%, transparent);
  border-bottom: 1px solid var(--color-hairline);
}

.mg-bar__mark {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  color: var(--color-chalk);
  text-decoration: none;
}

.mg-bar__mark img {
  height: 28px;
  width: auto;
  display: block;
}

.mg-bar__word {
  padding-left: 0.8rem;
  border-left: 1px solid var(--color-hairline);
  font-family: "Shellhouse One", sans-serif;
  font-size: 1.05rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.mg-bar__gap {
  flex-grow: 1;
}

.mg-bar__icon {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  padding: 0;
  border: 0;
  background: none;
  color: var(--color-chalk);
  cursor: pointer;
}

@media (max-width: 839px) {
  .mg-bar {
    height: 56px;
    padding: 0 8px 0 14px;
  }

  .mg-bar__word {
    font-size: 0.9rem;
  }
}

:deep(.mg-menu) {
  z-index: 1010;
  width: 18rem;
  padding: 0.4rem 0;
  background: var(--color-surface);
  border-top: 3px solid var(--color-brand);
  color: var(--color-chalk);
  box-shadow: 0 18px 40px rgb(0 0 0 / 45%);
}

:deep(.mg-menu__entry) {
  display: block;
  padding: 0.6rem 1.1rem;
  font-size: 0.92rem;
  color: var(--color-chalk);
  cursor: pointer;
  text-decoration: none;
}

:deep(.mg-menu__entry[data-highlighted]),
:deep(.mg-menu__entry:hover) {
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

:deep(.mg-menu__switch) {
  border-top: 1px solid var(--color-hairline);
  color: var(--color-brand);
}

:deep(.mg-menu__out) {
  border-top: 1px solid var(--color-hairline);
  color: var(--color-ash);
}
</style>
