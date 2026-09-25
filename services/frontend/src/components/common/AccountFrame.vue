<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import {accountFor, type NavReader} from "@/components/common/nav"

/**
 * The head every account page shares: its name, the account tabs and, on a page one step down, a
 * crumb back. The tabs are the account menu's own list, so the two cannot disagree.
 *
 * A page still on Vuetify is drawn below the island rather than in it, because the island's reset
 * strips the buttons and headings Vuetify draws.
 */
defineOptions({name: "AccountFrame"})

const {eyebrow = "Your account", body = "", crumb = undefined, tabs = true, islandContent = false} = defineProps<{
  heading: string
  eyebrow?: string
  body?: string
  crumb?: {label: string, to: string}
  tabs?: boolean
  islandContent?: boolean
}>()

const store = useStore()

const reader = computed<NavReader>(() => ({
  loggedIn: Boolean(store.getters.isLoggedIn),
  board: Boolean(store.getters.isBoard),
  admin: Boolean(store.getters.isAdmin),
  addressId: store.getters.getLogin?.addressId ?? null,
}))
const pages = computed(() => accountFor(reader.value))
</script>

<template>
  <v-main class="account-main">
    <island
      class="account"
      testid="account-island"
    >
      <header-band>
        <template #head>
          <router-link
            v-if="crumb"
            class="account__crumb"
            data-testid="account-crumb"
            :to="crumb.to"
          >
            <svg
              aria-hidden="true"
              fill="none"
              height="11"
              viewBox="0 0 20 12"
              width="18"
            ><path
              d="M20 6H3M7 1.5L1.5 6L7 10.5"
              stroke="currentColor"
              stroke-width="1.4"
            /></svg>
            {{ crumb.label }}
          </router-link>
          <div class="account__head">
            <div>
              <p class="account__eyebrow">
                {{ eyebrow }}
              </p>
              <h1 class="account__heading">
                {{ heading }}
              </h1>
              <p
                v-if="body"
                class="account__body"
              >
                {{ body }}
              </p>
            </div>
            <div
              v-if="$slots.actions"
              class="account__actions"
            >
              <slot name="actions" />
            </div>
          </div>
        </template>
        <div
          v-if="tabs"
          class="account__wrap"
        >
          <page-tabs
            :entries="pages"
            label="Your account"
            testid="account-tab"
          />
        </div>
      </header-band>

      <div
        v-if="islandContent"
        class="account__wrap account__content"
      >
        <slot />
      </div>
    </island>

    <slot v-if="!islandContent" />
  </v-main>
</template>

<style scoped>
/* The island runs down to the footer, so a short page does not stop on the Vuetify ground. */
.account-main {
  display: flex;
  flex-direction: column;
}

.account {
  flex: 1 0 auto;
  min-height: 0;
}

.account__wrap {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding-inline: 1.25rem;
}

.account__content {
  padding-bottom: 3.5rem;
}

.account__crumb {
  display: inline-flex;
  align-items: center;
  gap: 0.6rem;
  margin-bottom: 1.25rem;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
  color: var(--color-ash);
}

.account__crumb:hover,
.account__crumb:focus-visible {
  color: var(--color-chalk);
}

.account__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
  flex-wrap: wrap;
}

.account__eyebrow {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.account__heading {
  margin-top: 0.625rem;
  font-family: var(--font-display);
  font-size: 2.25rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.account__body {
  margin-top: 0.75rem;
  max-width: 36rem;
  font-size: 0.95rem;
  line-height: 1.6;
  color: var(--color-ash);
}

.account__actions {
  flex: none;
}

@media (min-width: 640px) {
  .account__wrap {
    padding-inline: 2rem;
  }
}

@media (max-width: 767px) {
  .account__heading {
    font-size: 1.6rem;
  }
}
</style>
