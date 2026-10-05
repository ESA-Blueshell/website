<script lang="ts" setup>
/* The Discord settings: which role each server-wide cohort follows, where the bots post and which
   roles the role-claim bot hands out. What was set in the configuration shows until the board
   changes it here. */
import {computed, onMounted, ref} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {
  ChannelMark,
  DISCORD_TABS,
  type DiscordBotSettings,
  type KeptChannel,
  type KeptRole,
  type ServerCohortRole,
  listKeepableChannels,
  listKeepableRoles,
  readBotSettings,
  readServerCohortRoles,
  saveBotSettings,
  setCohortRole,
} from "@/domains/discord"
import store from "@/plugins/store"

defineOptions({name: "DiscordSettingsPage"})

const cohorts = ref<ServerCohortRole[] | null>(null)
const bot = ref<DiscordBotSettings | null>(null)
const roles = ref<KeptRole[]>([])
const channels = ref<KeptChannel[]>([])
const loaded = ref(false)
const acting = ref(false)
const failure = ref<string | null>(null)

const COLUMNS: TableColumn<ServerCohortRole>[] = [
  {key: "cohort", label: "Cohort", sortBy: (one) => one.label},
  {key: "role", label: "Role"},
  {key: "defaults", label: "Channels it gets when set", wrap: true},
]

const roleOptions = computed(() => roles.value.map((one) => ({key: one.id, label: `@${one.name}`})))
const textChannels = computed(() => channels.value.filter((one) => one.kind === "TEXT"))
const channelOptions = computed(() => textChannels.value.map((one) => ({key: one.name, label: `#${one.name}`, note: one.category ?? undefined})))
const claimChosen = computed(() =>
  (bot.value?.claimRoleIds ?? []).map((id) => ({key: id, label: roles.value.find((one) => one.id === id)?.name ?? id})),
)
const claimOptions = computed(() => roles.value.map((one) => ({key: one.id, label: one.name})))
const channelOf = (name: string) => textChannels.value.find((one) => one.name.toLowerCase() === name.toLowerCase())

async function pickRole(row: ServerCohortRole, choice: {roleId: string} | {create: true}) {
  acting.value = true
  failure.value = null
  const answered = await setCohortRole(row.key, choice)
  acting.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  cohorts.value = answered.saved
  store.commit("setStatusSnackbarMessage", `${row.label} follows ${answered.saved.find((one: ServerCohortRole) => one.key === row.key)?.roleName ?? "the role"} now.`)
}

function setChannel(field: "infoChannel" | "calendarChannel" | "starboardChannel", name: string) {
  if (bot.value) bot.value = {...bot.value, [field]: name}
}

function setClaimRoles(ids: string[]) {
  if (bot.value) bot.value = {...bot.value, claimRoleIds: ids}
}

async function saveBot() {
  if (!bot.value) return
  acting.value = true
  failure.value = null
  const answered = await saveBotSettings(bot.value)
  acting.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  bot.value = answered.saved
  store.commit("setStatusSnackbarMessage", "The bot settings are saved.")
}

onMounted(async () => {
  const [readCohorts, readBot, readRoles, readChannels] = await Promise.all([readServerCohortRoles(), readBotSettings(), listKeepableRoles(), listKeepableChannels()])
  cohorts.value = readCohorts
  bot.value = readBot
  roles.value = readRoles
  channels.value = readChannels
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="discord-settings-page"
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
      The roles the whole server follows, where the bots post and which roles the role-claim bot hands out.
    </template>

    <notice-box
      v-if="failure"
      class="settings__notice"
      testid="discord-settings-failure"
      tone="danger"
    >
      {{ failure }}
    </notice-box>

    <p
      v-if="loaded && roles.length === 0"
      class="settings__note"
      data-testid="discord-settings-away"
    >
      Discord cannot be read now, so no role or channel can be picked.
    </p>

    <form-section title="Roles">
      <management-table
        :columns="COLUMNS"
        :row-key="(row) => row.key"
        :row-testid="(row) => `discord-settings-cohort-${row.key}`"
        :rows="cohorts ?? []"
        testid="discord-settings-cohorts"
        :to="(row) => (row.roleId ? `/management/platforms/discord/roles/${row.roleId}` : null)"
      >
        <template #empty>
          <span data-testid="discord-settings-cohorts-unread">The cohorts could not be read.</span>
        </template>
        <template #cohort="{row}">
          <span class="mg-name">{{ row.label }}</span>
        </template>
        <template #role="{row}">
          <search-picker
            class="settings__role"
            compact
            :disabled="acting || roles.length === 0"
            :options="roleOptions"
            placeholder="Search roles"
            :selected-key="row.roleId ?? null"
            :testid-prefix="`discord-settings-role-${row.key}`"
            @click.stop
            @pick="(key: string) => pickRole(row, {roleId: key})"
          >
            <template #chosen="{option}">
              {{ option?.label ?? "No role" }}
            </template>
          </search-picker>
        </template>
        <template #defaults="{row}">
          <span
            v-if="row.defaultChannels.length > 0"
            class="mg-marks"
          >
            <channel-mark
              v-for="name in row.defaultChannels"
              :id="channelOf(name)?.id"
              :key="name"
              :name="channelOf(name)?.name ?? name"
              :testid="`discord-settings-default-${row.key}-${name}`"
            />
          </span>
          <span
            v-else
            class="mg-quiet"
          >None</span>
        </template>
        <template #acts="{row}">
          <cut-button
            v-if="!row.roleId"
            :disabled="acting || roles.length === 0"
            small
            :testid="`discord-settings-create-${row.key}`"
            @click="pickRole(row, {create: true})"
          >
            Create role
          </cut-button>
        </template>
      </management-table>
    </form-section>

    <form-section title="Bot channels">
      <p
        v-if="loaded && !bot"
        class="settings__note"
        data-testid="discord-settings-bot-unread"
      >
        The bot settings could not be read.
      </p>
      <form
        v-else-if="bot"
        class="island-form"
        data-testid="discord-settings-bot-form"
        @submit.prevent="saveBot"
      >
        <form-fields>
          <form-field
            label="Event announcements"
            testid="discord-settings-info"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                :labelled-by="labelId"
                :options="channelOptions"
                :selected-key="bot.infoChannel"
                testid-prefix="discord-settings-info-picker"
                @pick="(name: string) => setChannel('infoChannel', name)"
              />
            </template>
          </form-field>
          <form-field
            label="Event day posts"
            testid="discord-settings-calendar"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                :labelled-by="labelId"
                :options="channelOptions"
                :selected-key="bot.calendarChannel"
                testid-prefix="discord-settings-calendar-picker"
                @pick="(name: string) => setChannel('calendarChannel', name)"
              />
            </template>
          </form-field>
          <form-field
            label="Starboard"
            testid="discord-settings-starboard"
          >
            <template #default="{controlId, labelId}">
              <search-picker
                :control-id="controlId"
                :labelled-by="labelId"
                :options="channelOptions"
                :selected-key="bot.starboardChannel"
                testid-prefix="discord-settings-starboard-picker"
                @pick="(name: string) => setChannel('starboardChannel', name)"
              />
            </template>
          </form-field>
          <form-field
            class="form-span"
            hint="The site leaves these roles alone, so it never adds or removes them."
            label="Roles the role-claim bot hands out"
            testid="discord-settings-claim"
          >
            <template #default="{controlId, labelId}">
              <chip-picker
                :chosen="claimChosen"
                :control-id="controlId"
                :labelled-by="labelId"
                :options="claimOptions"
                sigil="@"
                testid-prefix="discord-settings-claim-picker"
                @add="(keys: string[]) => setClaimRoles([...bot!.claimRoleIds, ...keys])"
                @remove="(key: string) => setClaimRoles(bot!.claimRoleIds.filter((one) => one !== key))"
              />
            </template>
          </form-field>
        </form-fields>
        <div class="form-save">
          <cut-button
            :disabled="acting"
            submit
            testid="discord-settings-bot-save"
            tone="solid"
          >
            {{ acting ? "Saving" : "Save" }}
          </cut-button>
        </div>
      </form>
    </form-section>
  </management-page>
</template>

<style scoped>
.settings__notice {
  margin-bottom: 1rem;
}

.settings__note {
  margin: 0.6rem 0 1rem;
  color: var(--color-ash);
}

.settings__role {
  min-width: 14rem;
}
</style>
