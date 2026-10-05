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
  display: inline-flex;
  align-items: center;
  padding: 0.05em 0.35em;
  border-radius: 3px;
  font-family: var(--font-body);
  font-size: 0.82rem;
  font-weight: 600;
  letter-spacing: 0;
  text-transform: none;
  white-space: nowrap;
  color: color-mix(in oklab, var(--mention-lit) 70%, var(--color-chalk));
  background: color-mix(in oklab, var(--mention) 22%, transparent);

  --mention: var(--color-brand);
  --mention-lit: var(--color-brand-lit);
}

.role-mark--admin {
  --mention: #e8594f;
  --mention-lit: #e8594f;
}

.role-mark--board {
  --mention: #3387fa;
  --mention-lit: #3387fa;
}

.role-mark--treasurer {
  --mention: #e0a100;
  --mention-lit: #e0a100;
}
</style>
