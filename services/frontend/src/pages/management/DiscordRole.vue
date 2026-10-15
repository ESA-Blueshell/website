<script lang="ts" setup>
/* One Discord role: the cohort that fills it, its holders and their drift, each drifting person
   with why and what can be done, and the channels the role can access. A role is linked to a
   cohort on the committee's or the team's form, and people link their own Discord account. */
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {
  type Cohort,
  type ListedTarget,
  ROLE_DRIFT_WORDS,
  TargetDrift,
  TargetSystem,
  cohortTypeLabel,
  driftRowsOf,
  fetchCohort,
  inSyncOn,
  readListedTarget,
  runBars,
  triggerReconcile,
} from "@/domains/cohorts"
import {type CataloguedChannel, ChannelMark, listCatalogue} from "@/domains/discord"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "DiscordRolePage"})

const SYSTEM = TargetSystem.DISCORD
const BACK = {to: "/management/platforms/discord/roles", label: "Roles"}

const route = useRoute()
const externalId = computed(() => String(route.params.externalId))

const role = ref<ListedTarget | null>(null)
const cohort = ref<Cohort | null>(null)
const channels = ref<CataloguedChannel[] | null>(null)
const loaded = ref(false)
// Discord answered it has no such role, apart from a read that failed.
const gone = ref(false)
const cohortUnread = ref(false)
const acting = ref(false)

const mapping = computed(() => cohort.value?.mappings.find((one) => one.system === SYSTEM) ?? null)
const members = computed(() => cohort.value?.members ?? [])
const drift = computed(() => driftRowsOf(members.value, SYSTEM))
const opened = computed(() => (channels.value ?? []).filter((one) => one.kind !== "CATEGORY" && one.roleIds.includes(externalId.value)))

const load = async () => {
  const read = await readListedTarget(SYSTEM, externalId.value)
  gone.value = read === "gone"
  role.value = read === "gone" ? null : read
  cohort.value = role.value?.cohortId != null ? await fetchCohort(role.value.cohortId) : null
  cohortUnread.value = role.value?.cohortId != null && cohort.value == null
  channels.value = await listCatalogue()
  loaded.value = true
}

const facts = computed(() => {
  const latest = mapping.value?.runs.at(0)
  const missing = drift.value.filter((one) => one.sync === "ONLY_HERE")
  return [
    {label: "Follows", value: cohort.value?.label ?? "", sub: cohort.value ? cohortTypeLabel(cohort.value.type) : ""},
    {
      label: "People",
      value: `${inSyncOn(members.value, SYSTEM)} in sync`,
      sub: `${missing.length} missing · ${drift.value.filter((one) => one.sync === "ONLY_EXTERNAL").length} additional`,
      testid: "discord-role-people",
    },
    {
      label: "No Discord linked",
      value: String(missing.filter((one) => one.unreachable).length),
      sub: "They link their own account on their account page",
      testid: "discord-role-unreachable",
    },
    {label: "Compared", value: latest ? formatDateNoSeconds(latest.startedAt) : "Never", sub: "Nightly, and after every change"},
  ]
})
const bars = computed(() => runBars(mapping.value?.runs ?? []))

const reconcile = async () => {
  if (!cohort.value || !mapping.value || acting.value) return
  acting.value = true
  const answered = await triggerReconcile(cohort.value.id, mapping.value.targetId)
  acting.value = false
  store.commit("setStatusSnackbarMessage", answered.ok ? "A comparison is queued." : answered.reason)
}

onMounted(load)
watch(externalId, load)
</script>

<template>
  <management-page
    v-if="loaded && !role"
    :back="BACK"
    eyebrow="Platforms · Discord"
    testid="discord-role-page"
    :title="gone ? 'No such role' : 'Discord role'"
  >
    <p
      class="role__note"
      data-testid="discord-role-unread"
    >
      {{ gone ? "Discord has no role with this id. It may have been deleted there." : "The role could not be read from Discord. Try again later." }}
    </p>
  </management-page>

  <management-page
    v-else-if="role"
    :back="BACK"
    eyebrow="Platforms · Discord role"
    testid="discord-role-page"
    :title="`@${role.label}`"
  >
    <template #lede>
      <template v-if="cohort">
        Everyone in {{ cohort.label }}, {{ cohortTypeLabel(cohort.type).toLowerCase() }}, holds this role.
      </template>
      <template v-else-if="cohortUnread">
        The cohort this role follows could not be read. Try again later.
      </template>
      <template v-else>
        This role is not linked to a cohort, so the site leaves its holders alone. Link it on a committee's or a team's form.
      </template>
    </template>
    <template
      v-if="mapping"
      #actions
    >
      <cut-button
        :disabled="acting"
        testid="discord-role-reconcile"
        @click="reconcile"
      >
        Compare with Discord now
      </cut-button>
    </template>

    <template v-if="cohort && mapping">
      <fact-list
        class="role__facts"
        :facts="facts"
      />
      <p
        v-if="bars.length"
        :aria-label="`Drift over the last ${bars.length} runs`"
        class="role__runs"
        data-testid="discord-role-runs"
        role="img"
      >
        <span
          v-for="(bar, index) in bars"
          :key="index"
          class="role__bar"
          :class="{'role__bar--drift': bar.drift}"
          :style="{height: `${bar.height}px`}"
        />
      </p>
      <target-drift
        :cohort="cohort"
        :mapping="mapping"
        own-accounts
        :reload="load"
        testid="discord-role"
        :words="ROLE_DRIFT_WORDS"
      />
    </template>

    <list-head title="Channels it can access" />
    <p
      v-if="channels == null"
      class="role__note"
      data-testid="discord-role-channels-unread"
    >
      The channels could not be read. Try again later.
    </p>
    <p
      v-else-if="opened.length === 0"
      class="role__note"
      data-testid="discord-role-channels-none"
    >
      This role has no channels of its own.
    </p>
    <ul
      v-else
      class="role__channels"
      data-testid="discord-role-channels"
    >
      <li
        v-for="channel in opened"
        :key="channel.id"
      >
        <channel-mark
          :id="channel.id"
          :locked="channel.private"
          :name="channel.name"
          :voice="channel.kind === 'VOICE'"
        />
        <span class="role__category">{{ channel.category ?? "No category" }}</span>
      </li>
    </ul>
  </management-page>
</template>

<style scoped>
.role__note {
  color: var(--color-ash);
}

.role__facts {
  padding: 1.1rem 0 0.6rem;
}

.role__runs {
  display: flex;
  align-items: flex-end;
  gap: 4px;
  height: 26px;
  margin-bottom: 0.4rem;
}

.role__bar {
  width: 9px;
  min-height: 2px;
  background: color-mix(in oklab, var(--color-chalk) 18%, transparent);
  transform: skewX(-12deg);
}

.role__bar--drift {
  background: var(--color-warning);
}

.role__channels {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  padding: 0;
  list-style: none;
}

.role__category {
  margin-left: 0.6rem;
  font-size: 0.86rem;
  color: var(--color-ash);
}
</style>
