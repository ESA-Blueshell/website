<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {listOwnMemberships, MemberType, type MembershipResponse} from "@/domains/user"
import {formatDay} from "@/utils/timestamps"

/* What kind of member the reader is now, and since when: "Alumni since 1 Sep 2025". */
defineOptions({name: "CurrentMembershipType"})

const NAMES: Record<MemberType, string> = {
  [MemberType.REGULAR]: "Regular member",
  [MemberType.ALUMNI]: "Alumni",
  [MemberType.HONORARY]: "Honorary member",
}

const running = ref<MembershipResponse | null>(null)

const text = computed(() => {
  const current = running.value?.types.at(-1)
  return current ? `${NAMES[current.type]} since ${formatDay(current.since)}` : null
})

onMounted(async () => {
  running.value = (await listOwnMemberships()).find((m) => !m.endDate) ?? null
})
</script>

<template>
  <p
    v-if="text"
    class="membership-type"
    data-testid="current-membership-type"
  >
    {{ text }}
  </p>
</template>

<style scoped>
.membership-type {
  font-size: 0.95rem;
  color: var(--color-ash);
}
</style>
