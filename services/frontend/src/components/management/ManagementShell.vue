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
        :key="group.label"
        class="mg-side__group"
      >
        <p class="mg-side__label">
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
        </router-link>
      </div>
    </nav>

    <div class="mg-main">
      <main>
        <router-view />
      </main>
      <footer-banner class="mg-footer" />
    </div>

    <nav
      aria-label="Management"
      class="mg-tabbar"
      data-testid="management-tabbar"
    >
      <router-link
        v-for="tab in tabs"
        :key="tab.to"
        :aria-current="tab.on ? 'page' : undefined"
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
      </router-link>
    </nav>
  </div>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useRoute} from "vue-router"
import FooterBanner from "@/components/common/banners/FooterBanner.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import {isOn, slug} from "@/components/management/managementNav"
import {useManagementGroups} from "@/composables/useManagementGroups"

defineOptions({name: "ManagementShell"})

const route = useRoute()
const groups = useManagementGroups()

const MEMBERS_ICON = "M9 4.6a3.4 3.4 0 1 1 0 6.8a3.4 3.4 0 1 1 0-6.8M2.6 19.4c0-3.4 2.9-5.6 6.4-5.6s6.4 2.2 6.4 5.6M16 5.2a3.2 3.2 0 0 1 0 6.1M18.4 14.2c1.8.8 3 2.6 3 5.2"
const MORE_ICON = "M4 7h16M4 12h16M4 17h16"
const TAB_LOOK = {members: {label: "Members", icon: MEMBERS_ICON}}

/** The phone's bottom bar: the pages that have a tab, then More for everything else. */
const tabs = computed(() => {
  const owned = groups.value.flatMap((group) => group.entries).flatMap((entry) => entry.tab
    ? [{...TAB_LOOK[entry.tab], to: entry.to, on: isOn(route.path, entry)}]
    : [])
  return [...owned, {label: "More", to: "/management/more", icon: MORE_ICON, on: !owned.some((tab) => tab.on)}]
})
</script>

<style scoped>
.mg-shell {
  display: grid;
  grid-template-columns: 250px minmax(0, 1fr);
  min-height: calc(100vh - var(--management-bar));
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
  top: var(--management-bar);
  align-self: start;
  height: calc(100dvh - var(--management-bar));
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
  font-size: 11px;
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

.mg-main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* The site's own footer, at the end of whatever page is open. */
.mg-footer {
  margin-top: auto;
}

.mg-tabbar {
  display: none;
}

@media (max-width: 839px) {
  .mg-shell {
    grid-template-columns: minmax(0, 1fr);
    padding-bottom: var(--management-tabs);
  }

  .mg-side {
    display: none;
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
    height: var(--management-tabs);
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

.mg-tab--on {
  color: var(--color-chalk);
  box-shadow: inset 0 2px 0 var(--color-brand);
}
</style>
