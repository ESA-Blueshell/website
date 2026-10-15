<script lang="ts" setup>
/* The server's roles the site could keep: the cohort each one follows, the channels it can access
   and whether its holders match the cohort at the last comparison. A role is linked to a cohort on
   the committee's or the team's form. */
import {computed, onMounted, ref} from "vue"
import PageTabs from "@/components/island/PageTabs.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {driftMarksOf, followsOf, type ListedTarget, readTargetOverview, TargetSystem} from "@/domains/cohorts"
import {type CataloguedChannel, DISCORD_TABS, listCatalogue, opensOf} from "@/domains/discord"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "DiscordRolesPage"})

const roles = ref<ListedTarget[] | null>(null)
const channels = ref<CataloguedChannel[] | null>(null)
const loaded = ref(false)

const follows = (role: ListedTarget) => followsOf({list: role})
const COLUMNS: TableColumn<ListedTarget>[] = [
  {key: "role", label: "Role", sortBy: (one) => one.label},
  {key: "follows", label: "Follows", wrap: true, sortBy: follows},
  {key: "opens", label: "Channels it can access", wrap: true, sortBy: (one) => opensOf(one.externalId, channels.value ?? [])},
  {key: "drift", label: "State", sortBy: (one) => (one.missing ?? 0) + (one.extra ?? 0)},
  {key: "compared", label: "Compared", sortBy: (one) => one.lastReconciledAt ?? ""},
]

const sorted = computed(() => [...(roles.value ?? [])].sort((a, b) => a.label.localeCompare(b.label)))

onMounted(async () => {
  const [overview, catalogue] = await Promise.all([readTargetOverview(TargetSystem.DISCORD), listCatalogue()])
  roles.value = overview?.lists ?? null
  channels.value = catalogue
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="discord-roles-page"
    title="Discord"
  >
    <template #tabs>
      <page-tabs
        :entries="DISCORD_TABS"
        label="Discord"
        testid="discord-tabs"
      />
    </template>

    <template #lede>
      The roles the site could keep on the Discord server, the cohort each one follows and whether its holders match that cohort.
    </template>

    <p
      v-if="loaded && roles == null"
      class="roles__note"
      data-testid="discord-roles-unread"
    >
      The roles could not be read. Reload the page to try again.
    </p>
    <p
      v-else-if="loaded && roles?.length === 0"
      class="roles__note"
      data-testid="discord-roles-none"
    >
      The bot is not in the server now, or the server has no role the site could keep.
    </p>

    <p
      v-if="loaded && roles?.length && channels == null"
      class="roles__note"
      data-testid="discord-roles-channels-unread"
    >
      The channels could not be read, so the channels each role can access are not shown.
    </p>

    <management-table
      v-if="roles?.length"
      :columns="COLUMNS"
      :row-key="(one) => one.externalId"
      :row-testid="(one) => `discord-role-${one.externalId}`"
      :rows="sorted"
      search-label="Search roles"
      :search-text="(one) => `${one.label} ${follows(one)}`"
      testid="discord-roles"
    >
      <template #role="{row}">
        <router-link
          class="roles__link"
          :data-testid="`discord-role-open-${row.externalId}`"
          :to="`/management/platforms/discord/roles/${row.externalId}`"
        >
          <role-mark :role="row.label" />
        </router-link>
      </template>
      <template #follows="{row}">
        <span class="mg-quiet">{{ follows(row) }}</span>
      </template>
      <template #opens="{row}">
        <span class="mg-quiet">{{ channels ? opensOf(row.externalId, channels) : "Not read" }}</span>
      </template>
      <template #drift="{row}">
        <state-mark
          v-for="mark in driftMarksOf(row)"
          :key="mark.word"
          :kind="mark.kind"
        >
          {{ mark.word }}
        </state-mark>
      </template>
      <template #compared="{row}">
        <span class="mg-quiet">{{ row.lastReconciledAt ? formatMoment(row.lastReconciledAt) : "Never" }}</span>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.roles__note {
  color: var(--color-ash);
}

.roles__link {
  text-decoration: none;
}
</style>
