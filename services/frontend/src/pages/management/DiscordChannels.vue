<script lang="ts" setup>
/* The server's channels by the category they sit in: who gets into each and what it belongs to, a
   game or the cohorts whose roles it opens to. */
import {computed, onMounted, ref} from "vue"
import PageTabs from "@/components/island/PageTabs.vue"
import ListHead from "@/components/management/ListHead.vue"
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
const roles = ref(new Map<string, NamedRole>())
const loaded = ref(false)

const COLUMNS: TableColumn<CataloguedChannel>[] = [
  {key: "channel", label: "Channel", sortBy: (one) => one.name},
  {key: "access", label: "Who gets in", wrap: true, sortBy: (one) => accessOf(one, roles.value)},
  {key: "belongs", label: "Belongs to", wrap: true, sortBy: (one) => belongsTo(one, roles.value)},
]

const groups = computed(() => channelGroups(channels.value ?? []))

onMounted(async () => {
  const [catalogue, overview] = await Promise.all([listCatalogue(), readTargetOverview(TargetSystem.DISCORD)])
  channels.value = catalogue
  const follows = (overview?.lists ?? []).map((one) => {
    const said = followsOf({list: one})
    return [one.externalId, {id: one.externalId, name: one.label, follows: said === "Nothing" ? null : said}] as const
  })
  roles.value = new Map(follows)
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
      The server's channels by category, who gets into each and what it belongs to.
    </template>

    <p
      v-if="loaded && channels == null"
      class="channels__note"
      data-testid="discord-channels-unread"
    >
      The channels could not be read. Reload the page to try again.
    </p>
    <p
      v-else-if="loaded && groups.length === 0"
      class="channels__note"
      data-testid="discord-channels-none"
    >
      The bot is not in the server now, so its channels cannot be read.
    </p>

    <section
      v-for="group in groups"
      :key="group.name"
      class="channels__group"
      :data-testid="`discord-channel-group-${group.name}`"
    >
      <list-head :title="`${group.name} · ${group.channels.length}`" />
      <management-table
        :columns="COLUMNS"
        :row-key="(one) => one.id"
        :row-testid="(one) => `discord-channel-${one.id}`"
        :rows="group.channels"
        :search-label="`Search ${group.name}`"
        :search-text="(one) => `${one.name} ${belongsTo(one, roles)}`"
      >
        <template #channel="{row}">
          <channel-mark
            :id="row.id"
            :locked="row.private"
            :name="row.name"
            :voice="row.kind === 'VOICE'"
          />
        </template>
        <template #access="{row}">
          <span class="mg-quiet">{{ accessOf(row, roles) }}</span>
        </template>
        <template #belongs="{row}">
          <span class="mg-quiet">{{ belongsTo(row, roles) }}</span>
        </template>
      </management-table>
    </section>
  </management-page>
</template>

<style scoped>
.channels__note {
  color: var(--color-ash);
}

.channels__group {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 1.6rem;
}
</style>
