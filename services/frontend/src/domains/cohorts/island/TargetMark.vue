<script lang="ts" setup>
/* What a committee, board or team has on Discord or Brevo, in a list: its role or list by name,
   leading to that role's or list's own page, or a mark saying it has none yet. */
import {computed} from "vue"
import StateMark from "@/components/island/StateMark.vue"
import {type SummaryTarget, TargetSystem} from "../adapters/cohorts"

defineOptions({name: "TargetMark"})

const {targets, system, quiet = false, testid = undefined} = defineProps<{
  /** Everything the cohort has, on every system; the one on this system is drawn. */
  targets: SummaryTarget[]
  system: TargetSystem
  /** Nothing is expected here, so its absence is said quietly: an archived committee, a past board. */
  quiet?: boolean
  testid?: string
}>()

const PAGES: Partial<Record<TargetSystem, string>> = {
  [TargetSystem.DISCORD]: "/management/platforms/discord/roles",
  [TargetSystem.BREVO]: "/management/platforms/brevo/lists",
}

const made = computed(() => targets.find((one) => one.system === system && one.made) ?? null)
const word = computed(() => (system === TargetSystem.DISCORD ? `@${made.value?.label}` : made.value?.label))
const page = computed(() => (made.value?.externalId && PAGES[system] ? `${PAGES[system]}/${made.value.externalId}` : null))
</script>

<template>
  <router-link
    v-if="made && page"
    class="target-mark"
    :data-testid="testid"
    :to="page"
  >
    {{ word }}
  </router-link>
  <span
    v-else-if="made"
    :data-testid="testid"
  >{{ word }}</span>
  <state-mark
    v-else
    :kind="quiet ? 'not-compared' : 'not-created'"
    :testid="testid"
  >
    {{ system === TargetSystem.DISCORD ? "No role" : "No list" }}
  </state-mark>
</template>

<style scoped>
.target-mark:hover,
.target-mark:focus-visible {
  text-decoration: underline;
  text-underline-offset: 3px;
}
</style>
