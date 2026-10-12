<template>
  <ul
    class="pairs"
    :data-testid="testid"
  >
    <li
      v-for="pair in pairs"
      :key="pair.label"
      :data-testid="pair.testid"
    >
      <router-link
        v-if="pair.to"
        :to="pair.to"
      >
        {{ pair.label }}
      </router-link>
      <span v-else>{{ pair.label }}</span>
      <role-mark
        v-if="pair.adminOnly"
        role="Admin"
      />
      <span>{{ pair.value }}</span>
    </li>
  </ul>
</template>

<script lang="ts" setup>
/* Short facts read down a column: what each is on the left, how it stands on the right. */
import RoleMark from "@/components/island/RoleMark.vue"

export interface Pair {
  label: string
  value: string
  /** Where the label leads, when the fact has a page of its own. */
  to?: string
  /** Only an admin reads it, and the row says so. */
  adminOnly?: boolean
  testid?: string
}

const {pairs, testid = undefined} = defineProps<{
  pairs: Pair[]
  testid?: string
}>()
</script>

<style scoped>
.pairs {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pairs li {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  padding: 0.5rem 0.8rem;
  font-size: 0.88rem;
  background-color: var(--band-ground);
}

.pairs li > :last-child {
  flex: 1;
  text-align: right;
  color: var(--color-ash);
}

.pairs a:hover {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
