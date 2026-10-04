<template>
  <div
    class="island mg-shell"
    data-testid="management-shell"
  >
    <nav
      aria-label="Management"
      class="mg-side"
      data-testid="management-sidebar"
    >
      <div
        v-for="group in groups"
        :key="group.label ?? ''"
        class="mg-side__group"
      >
        <p
          v-if="group.label"
          class="mg-side__label"
        >
          {{ group.label }}
          <role-mark
            v-if="group.adminOnly"
            role="Admin"
          />
        </p>
        <router-link
          v-for="entry in group.entries"
          :key="entry.to"
          :aria-current="isOn(route.path, entry) ? 'page' : undefined"
          class="mg-side__item"
          :class="{'mg-side__item--on': isOn(route.path, entry)}"
          :data-testid="`management-nav-${slug(entry.label)}`"
          :to="entry.to"
        >
          {{ entry.label }}
          <role-mark
            v-if="entry.adminOnly && !group.adminOnly"
            role="Admin"
          />
          <span
            v-if="entry.counted && count > 0"
            class="mg-side__count"
            :data-testid="`management-nav-${slug(entry.label)}-count`"
          >{{ count }}<span class="mg-side__said"> alerts</span></span>
        </router-link>
      </div>
    </nav>

    <main class="mg-main">
      <router-view />
      <p class="mg-foot">
        ESA Blueshell · Management ·
        <router-link to="/">
          Back to the site
        </router-link>
      </p>
    </main>

    <nav
      aria-label="Management"
      class="mg-tabbar"
      data-testid="management-tabbar"
    >
      <router-link
        v-for="tab in tabs"
        :key="tab.to"
        class="mg-tab"
        :class="{'mg-tab--on': tab.on}"
        :data-testid="`management-tab-${slug(tab.label)}`"
        :to="tab.to"
      >
        <svg
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
          viewBox="0 0 24 24"
        >
          <path :d="tab.icon" />
        </svg>
        <span>{{ tab.label }}</span>
        <span
          v-if="tab.count > 0"
          class="mg-tab__count"
          :data-testid="`management-tab-${slug(tab.label)}-count`"
        >{{ tab.count }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, watch} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import RoleMark from "@/components/island/RoleMark.vue"
import {isOn, managementFor} from "@/components/management/managementNav"
import {useAlerts} from "@/domains/alerts"

defineOptions({name: "ManagementShell"})

const route = useRoute()
const store = useStore()

const groups = computed(() => managementFor({board: store.getters.isBoard === true, admin: store.getters.isAdmin === true}))

// Read again on every page, so an alert dealt with on one page leaves the count on the next.
const {count, refresh} = useAlerts()
onMounted(refresh)
watch(() => route.path, refresh)

const MEMBERS_ICON = "M9 4.6a3.4 3.4 0 1 1 0 6.8a3.4 3.4 0 1 1 0-6.8M2.6 19.4c0-3.4 2.9-5.6 6.4-5.6s6.4 2.2 6.4 5.6M16 5.2a3.2 3.2 0 0 1 0 6.1M18.4 14.2c1.8.8 3 2.6 3 5.2"
const ALERTS_ICON = "M12 3.5a5.5 5.5 0 0 0-5.5 5.5v3.6L4.6 16.4h14.8l-1.9-3.8V9A5.5 5.5 0 0 0 12 3.5M9.8 19.2a2.3 2.3 0 0 0 4.4 0"
const MORE_ICON = "M4 7h16M4 12h16M4 17h16"
const TAB_LOOK = {alerts: {label: "Alerts", icon: ALERTS_ICON}, members: {label: "Members", icon: MEMBERS_ICON}}

/** The phone's bottom bar: the pages that have a tab, then More for everything else. */
const tabs = computed(() => {
  const owned = groups.value.flatMap((group) => group.entries).flatMap((entry) => entry.tab
    ? [{...TAB_LOOK[entry.tab], to: entry.to, on: isOn(route.path, entry), count: entry.counted ? count.value : 0}]
    : [])
  const more = {label: "More", to: "/management/more", icon: MORE_ICON, on: route.path === "/management/more", count: 0}
  return [...owned, {...more, on: more.on || (!owned.some((tab) => tab.on) && route.path !== "/management")}]
})

const slug = (label: string): string => label.toLowerCase().replace(/\s+/g, "-")
</script>

<style scoped>
.mg-shell {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  min-height: calc(100vh - 60px);
}

.mg-side {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
  padding: 1rem 0.8rem 2rem;
  background-color: var(--band-ground);
  border-right: 1px solid var(--color-hairline);

  /* The navigation stays where it is: the page beside it is what scrolls. */
  position: sticky;
  top: 60px;
  align-self: start;
  height: calc(100vh - 60px);
  overflow-y: auto;
}

.mg-side__group {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.mg-side__label {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin: 0;
  padding: 0 0.7rem 0.35rem;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.mg-side__item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 0.6rem;
  min-height: 38px;
  padding: 0 0.7rem 0 1rem;
  font-size: 0.93rem;
  font-weight: 500;
  text-decoration: none;
  color: color-mix(in oklab, var(--color-chalk) 78%, transparent);
}

.mg-side__item:hover {
  color: var(--color-chalk);
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.mg-side__item--on {
  color: var(--color-chalk);
  background: color-mix(in oklab, var(--color-brand) 14%, transparent);
}

.mg-side__item--on::before {
  content: "";
  position: absolute;
  left: 0.3rem;
  top: 0.55rem;
  bottom: 0.55rem;
  width: 3px;
  background: var(--color-brand);
  transform: skewX(-12deg);
}

.mg-side__count {
  min-width: 1.45rem;
  margin-left: auto;
  padding: 0.1rem 0.45rem;
  border-radius: 9999px;
  font-size: 0.72rem;
  font-weight: 700;
  text-align: center;
  color: var(--color-void);
  background: var(--color-warning);
}

/* The warning tone is dark on the light ground, so its count reads in white there. */
:global([data-theme="light"]) .mg-side__count,
:global([data-theme="light"]) .mg-tab__count {
  color: #fff;
}

.mg-side__said {
  position: absolute;
  left: -9999px;
}

.mg-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.mg-foot {
  margin: auto 0 0;
  padding: 1.2rem 2.4rem;
  font-size: 0.78rem;
  color: var(--color-ash);
  border-top: 1px solid var(--color-hairline);
}

.mg-foot a {
  color: var(--color-brand);
  /* A link inside a sentence is told apart by more than its colour. */
  text-decoration: underline;
}

.mg-tabbar {
  display: none;
}

@media (max-width: 839px) {
  .mg-shell {
    grid-template-columns: minmax(0, 1fr);
    padding-bottom: 64px;
  }

  .mg-side {
    display: none;
  }

  .mg-foot {
    padding: 1.2rem 1.1rem;
  }

  .mg-tabbar {
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    z-index: 1004;
    display: grid;
    grid-auto-columns: minmax(0, 1fr);
    grid-auto-flow: column;
    height: 64px;
    background: color-mix(in oklab, var(--color-pit) 96%, transparent);
    border-top: 1px solid var(--color-hairline);
  }
}

.mg-tab {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 0.2rem;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.04em;
  text-decoration: none;
  color: var(--color-ash);
}

.mg-tab svg {
  width: 22px;
  height: 22px;
}

.mg-tab__count {
  position: absolute;
  top: 0.5rem;
  left: calc(50% + 0.4rem);
  min-width: 1.1rem;
  padding: 0 0.3rem;
  font-size: 0.66rem;
  line-height: 1.1rem;
  text-align: center;
  border-radius: 9999px;
  font-weight: 700;
  color: var(--color-void);
  background: var(--color-warning);
}

.mg-tab--on {
  color: var(--color-chalk);
  box-shadow: inset 0 2px 0 var(--color-brand);
}
</style>
