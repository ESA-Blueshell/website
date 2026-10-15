<script lang="ts" setup>
/* The server's channels in one table, by the category they sit in: who can access each and what it
   belongs to, a game or the cohorts whose roles it opens to. */
import {computed, onMounted, ref} from "vue"
import PageTabs from "@/components/island/PageTabs.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {followsOf, readTargetOverview, TargetSystem} from "@/domains/cohorts"
import {
  accessOf,
  belongsTo,
  type CataloguedChannel,
  ChannelMark,
  channelGroups,
  DISCORD_TABS,
  listCatalogue,
  type NamedRole,
} from "@/domains/discord"

defineOptions({name: "DiscordChannelsPage"})

const channels = ref<CataloguedChannel[] | null>(null)
const roles = ref<Map<string, NamedRole> | null>(null)
const loaded = ref(false)

const categoryOf = (one: CataloguedChannel) => one.category ?? "No category"
const belongs = (one: CataloguedChannel) => (roles.value ? belongsTo(one, roles.value) : "Not read")
const COLUMNS: TableColumn<CataloguedChannel>[] = [
  {key: "channel", label: "Channel", sortBy: (one) => one.name},
  {key: "category", label: "Category", sortBy: categoryOf},
  {key: "access", label: "Who can access", wrap: true, sortBy: accessOf},
  {key: "belongs", label: "Belongs to", wrap: true, sortBy: belongs},
]

// The uncategorised first and the archive last, as Discord's own channel list reads.
const rows = computed(() => channelGroups(channels.value ?? []).flatMap((group) => group.channels))

onMounted(async () => {
  const [catalogue, overview] = await Promise.all([listCatalogue(), readTargetOverview(TargetSystem.DISCORD)])
  channels.value = catalogue
  roles.value = overview
    ? new Map(overview.lists.map((one) => {
      const said = followsOf({list: one})
      return [one.externalId, {id: one.externalId, name: one.label, follows: said === "Nothing" ? null : said}]
    }))
    : null
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="discord-channels-page"
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
      The server's channels, who can access each and what it belongs to.
    </template>

    <p
      v-if="loaded && channels == null"
      class="channels__note"
      data-testid="discord-channels-unread"
    >
      The channels could not be read. Reload the page to try again.
    </p>
    <p
      v-else-if="loaded && rows.length === 0"
      class="channels__note"
      data-testid="discord-channels-none"
    >
      The bot is not in the server now, so its channels cannot be read.
    </p>
    <p
      v-if="loaded && rows.length > 0 && roles == null"
      class="channels__note"
      data-testid="discord-channels-roles-unread"
    >
      The roles could not be read, so what each channel belongs to is not shown.
    </p>

    <management-table
      v-if="rows.length > 0"
      :columns="COLUMNS"
      :row-key="(one) => one.id"
      :row-testid="(one) => `discord-channel-${one.id}`"
      :rows="rows"
      search-label="Search channels"
      :search-text="(one) => `${one.name} ${categoryOf(one)} ${belongs(one)}`"
      testid="discord-channels"
    >
      <template #channel="{row}">
        <channel-mark
          :id="row.id"
          :locked="row.private"
          :name="row.name"
          :voice="row.kind === 'VOICE'"
        />
      </template>
      <template #category="{row}">
        <span class="mg-quiet">{{ categoryOf(row) }}</span>
      </template>
      <template #access="{row}">
        <span class="mg-quiet">{{ accessOf(row) }}</span>
      </template>
      <template #belongs="{row}">
        <span class="mg-quiet">{{ belongs(row) }}</span>
      </template>
    </management-table>
  </management-page>
</template>

<style scoped>
.channels__note {
  color: var(--color-ash);
}
</style>
