<script lang="ts" setup>
/* The Discord settings: the role each server-wide cohort follows and the channels that role can
   access, set through the same form a committee's editor uses; where the bots post; and which roles
   the role-claim bot hands out. What was set in the configuration shows until the board changes it. */
import {computed, onMounted, ref} from "vue"
import ChipPicker from "@/components/island/ChipPicker.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {
  ChannelMark,
  DISCORD_TABS,
  type DiscordBotSettings,
  DiscordPlaceFields,
  type DiscordPlaceRequest,
  type KeptChannel,
  type KeptRole,
  type ServerCohortRole,
  listKeepableChannels,
  listKeepableRoles,
  readBotSettings,
  readCohortDiscord,
  readServerCohortRoles,
  saveBotSettings,
  saveCohortDiscord,
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

/* A role can reach most of the server; the row opens its page, where every channel is listed. */
const SHOWN_CHANNELS = 12

const COLUMNS: TableColumn<ServerCohortRole>[] = [
  {key: "cohort", label: "Cohort", sortBy: (one) => one.label},
  {key: "role", label: "Role", sortBy: (one) => one.roleName ?? ""},
  {key: "access", label: "Channels they have access to", wrap: true},
]

const textChannels = computed(() => channels.value.filter((one) => one.kind === "TEXT"))
const channelOptions = computed(() => textChannels.value.map((one) => ({key: one.name, label: `#${one.name}`, note: one.category ?? undefined})))
const claimChosen = computed(() =>
  (bot.value?.claimRoleIds ?? []).map((id) => ({key: id, label: roles.value.find((one) => one.id === id)?.name ?? id})),
)
const claimOptions = computed(() => roles.value.map((one) => ({key: one.id, label: one.name})))
const channelOf = (name: string) => textChannels.value.find((one) => one.name.toLowerCase() === name.toLowerCase())

/* The cohort whose role and channels the dialog sets, and what its form will ask of Discord. */
const linking = ref<ServerCohortRole | null>(null)
const choice = ref<DiscordPlaceRequest | null>(null)
const slugOf = (label: string) => label.toLowerCase().replace(/[^a-z0-9]+/g, "-")

/* Why the dialog's save was refused, and the cohort that follows the chosen role where that is why. */
const linkFailure = ref<string | null>(null)
const linkedTo = ref<string | null>(null)

function closeLink() {
  linking.value = null
  linkFailure.value = null
  linkedTo.value = null
}

async function saveLink(move = false) {
  const row = linking.value
  if (!row || !choice.value) return
  acting.value = true
  linkFailure.value = null
  linkedTo.value = null
  const answered = await saveCohortDiscord(row.key, {...choice.value, move})
  acting.value = false
  if (!answered.ok) {
    linkFailure.value = answered.linkedTo ? `That role belongs to ${answered.linkedTo} now.` : answered.reason
    linkedTo.value = answered.linkedTo
    return
  }
  closeLink()
  cohorts.value = (await readServerCohortRoles()) ?? cohorts.value
  store.commit("setStatusSnackbarMessage", `${row.label} follows @${answered.saved.roleName ?? "its role"} now.`)
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
          <role-mark
            v-if="row.roleName"
            :role="row.roleName"
            :testid="`discord-settings-role-${row.key}`"
          />
          <span
            v-else
            class="mg-quiet"
            :data-testid="`discord-settings-role-${row.key}`"
          >No role</span>
        </template>
        <template #access="{row}">
          <span
            v-if="row.channels.length > 0"
            class="settings__channels"
          >
            <channel-mark
              v-for="one in row.channels.slice(0, SHOWN_CHANNELS)"
              :id="one.id"
              :key="one.id"
              :locked="one.private"
              :name="one.name"
              :testid="`discord-settings-channel-${row.key}-${one.id}`"
              :voice="one.voice"
            />
            <span
              v-if="row.channels.length > SHOWN_CHANNELS"
              class="mg-quiet"
              :data-testid="`discord-settings-channels-more-${row.key}`"
            >and {{ row.channels.length - SHOWN_CHANNELS }} more</span>
          </span>
          <span
            v-else
            class="mg-quiet"
          >None</span>
        </template>
        <template #acts="{row}">
          <cut-button
            :disabled="acting || roles.length === 0"
            small
            :testid="`discord-settings-link-${row.key}`"
            @click="linking = row"
          >
            {{ row.roleId ? "Edit" : "Link" }}
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
    <modal-dialog
      cancel-testid="discord-settings-link-cancel"
      :open="linking != null"
      testid="discord-settings-link-dialog"
      :title="linking?.label ?? ''"
      wide
      @update:open="(open: boolean) => { if (!open) closeLink() }"
    >
      <notice-box
        v-if="linkFailure"
        class="settings__notice"
        testid="discord-settings-link-failure"
        tone="danger"
      >
        {{ linkFailure }}
        <template v-if="linkedTo">
          Moving it here takes it from {{ linkedTo }}. Nothing changes on Discord.
        </template>
      </notice-box>
      <p
        v-if="linking && !linking.roleId && linking.defaultChannels.length > 0"
        class="settings__defaults"
        data-testid="discord-settings-link-defaults"
      >
        Linking a role also gives it access to
        <channel-mark
          v-for="name in linking.defaultChannels"
          :id="channelOf(name)?.id"
          :key="name"
          :name="channelOf(name)?.name ?? name"
          :testid="`discord-settings-default-${linking.key}-${name}`"
        />.
      </p>
      <discord-place-fields
        v-if="linking"
        :key="linking.key"
        v-model="choice"
        :category="linking.key === 'CURRENT_TEAM_PLAYERS' ? 'Esports' : linking.key === 'BOARD' || linking.key === 'KANDI' ? 'Board' : 'Committees'"
        holders="Everyone in the cohort holds it."
        :name="linking.label"
        :read="linking.roleId ? () => readCohortDiscord(linking!.key) : null"
        :slug="slugOf(linking.label)"
        testid="discord-settings-place"
      />
      <template #footer>
        <cut-button
          v-if="linkedTo"
          :disabled="acting"
          testid="discord-settings-link-move"
          tone="solid"
          @click="saveLink(true)"
        >
          Move it here
        </cut-button>
        <cut-button
          v-else
          :disabled="acting || !choice"
          testid="discord-settings-link-save"
          tone="solid"
          @click="saveLink()"
        >
          {{ acting ? "Saving" : "Save" }}
        </cut-button>
      </template>
    </modal-dialog>
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

.settings__channels {
  display: flex;
  flex-wrap: wrap;
  gap: 0.3rem 0.4rem;
}

.settings__defaults {
  margin: 0 0 0.8rem;
  font-size: 0.86rem;
  color: var(--color-ash);
}
</style>
