<template>
  <section
    class="mg-more"
    data-testid="management-more"
  >
    <p class="eyebrow">
      Management
    </p>
    <h1 class="h-page">
      More
    </h1>
    <div
      v-for="group in groups"
      :key="group.label ?? ''"
      class="mg-more__group"
    >
      <p class="mg-more__label">
        {{ group.label }}
      </p>
      <router-link
        v-for="entry in group.entries"
        :key="entry.to"
        class="mg-more__item"
        :data-testid="`management-more-${entry.label.toLowerCase().replace(/\s+/g, '-')}`"
        :to="entry.to"
      >
        {{ entry.label }}
      </router-link>
    </div>
  </section>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import {managementFor} from "@/components/management/managementNav"

defineOptions({name: "ManagementMore"})

const store = useStore()

/** Every page the reader may open, grouped as the sidebar groups them; a phone has no sidebar. */
const groups = computed(() => managementFor({board: store.getters.isBoard === true, admin: store.getters.isAdmin === true}))
</script>

<style scoped>
.mg-more {
  padding: 1.3rem 1.1rem 2rem;
}

.mg-more__group {
  display: flex;
  flex-direction: column;
  margin-top: 1.4rem;
}

.mg-more__label {
  margin: 0 0 0.35rem;
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.mg-more__item {
  padding: 0.75rem 0;
  border-bottom: 1px solid var(--color-hairline);
  font-size: 1rem;
  font-weight: 500;
  text-decoration: none;
  color: var(--color-chalk);
}
</style>
