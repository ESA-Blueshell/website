<script lang="ts" setup>
/* A role written as Discord writes a mention: what a mark needs, or what a Discord role is. */
import {computed} from "vue"

const {role, testid = undefined} = defineProps<{
  /** Without the at sign: "Admin", "Board", "Sitecie". */
  role: string
  testid?: string
}>()

const tone = computed(() => (["admin", "board", "treasurer"].includes(role.toLowerCase()) ? role.toLowerCase() : "plain"))
</script>

<template>
  <span
    class="role-mark"
    :class="`role-mark--${tone}`"
    :data-testid="testid"
  >@{{ role }}</span>
</template>

<style scoped>
.role-mark {
  display: inline-block;
  padding: 0.05rem 0.4rem;
  font-size: 0.72rem;
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
  color: var(--color-brand-lit, var(--color-brand));
  background: color-mix(in oklab, var(--color-brand) 16%, transparent);
}

.role-mark--admin {
  color: var(--color-warning);
  background: color-mix(in oklab, var(--color-warning) 14%, transparent);
}

.role-mark--board,
.role-mark--treasurer {
  color: var(--color-chalk);
  background: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}
</style>
