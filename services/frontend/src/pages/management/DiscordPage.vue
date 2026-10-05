<script lang="ts" setup>
/* The roles and channels the site keeps in the association's Discord server: what fills each role
   and where it drifts, what each channel belongs to and who gets in, and where Discord differs.
   Committees and teams with no role yet are offered the roles and channels named as they are. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CheckBox from "@/components/island/CheckBox.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import FilterBar from "@/components/island/FilterBar.vue"
import FilterPicker from "@/components/island/FilterPicker.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import SelectionBar from "@/components/island/SelectionBar.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import BulkAdd from "@/components/management/BulkAdd.vue"
import {useUserSelection} from "@/composables/useUserSelection"
import {
  type ListedTarget,
  type MissingTarget,
  type TargetOverview,
  TargetSystem,
  createMissingLists,
  driftOf,
  followsOf,
  readTargetOverview,
  useTargetSync,
} from "@/domains/cohorts"
import {
  ChannelGlyph,
  type AdoptionMatch,
  type CataloguedChannel,
  type NamedRole,
  accessOf,
  adoptMatches,
  belongsTo,
  catalogueFacts,
  channelGroups,
  differsOf,
  isArchive,
  listCatalogue,
  listMatches,
  opensOf,
} from "@/domains/discord"
import store from "@/plugins/store"

defineOptions({name: "DiscordPage"})

const SYSTEM = TargetSystem.DISCORD
const ROLES = "/management/platforms/discord"
const CHANNELS = "/management/platforms/discord/channels"
const TABS = [{label: "Roles", to: ROLES}, {label: "Channels", to: CHANNELS}]

const route = useRoute()
const onChannels = computed(() => route.path === CHANNELS)

const overview = ref<TargetOverview | null>(null)
const channels = ref<CataloguedChannel[]>([])
const matches = ref<AdoptionMatch[]>([])
const loaded = ref(false)
const acting = ref(false)

const kept = computed(() => overview.value?.lists.filter((one) => one.targetId != null) ?? [])
const others = computed(() => overview.value?.lists.filter((one) => one.targetId == null) ?? [])
const missing = computed(() => overview.value?.missing ?? [])
const roles = computed(() => new Map<string, NamedRole>((overview.value?.lists ?? []).map((one) => [
  one.externalId,
  {id: one.externalId, name: one.label, follows: one.cohortLabel ?? null},
])))
const groups = computed(() => channelGroups(channels.value))
const facts = computed(() => catalogueFacts(overview.value?.lists ?? [], missing.value.length, channels.value))

const load = async () => {
  const [read, listed, matched] = await Promise.all([readTargetOverview(SYSTEM), listCatalogue(), listMatches()])
  overview.value = read
  channels.value = listed
  matches.value = matched
  loaded.value = true
}

const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

interface RoleRow {
  key: string
  missing: MissingTarget | null
  role: ListedTarget | null
  managed: boolean
}
type ChannelRow = CataloguedChannel & {category: string}

const followsWord = (row: RoleRow) => {
  if (row.missing) return followsOf({missing: row.missing})
  return row.managed && row.role ? followsOf({list: row.role}) : "Nothing"
}
const stateWord = (row: RoleRow) => {
  if (row.missing) return row.missing.creating ? "Being created" : "No role yet"
  return row.managed && row.role ? driftOf(row.role).word : "Not compared"
}

const ROLE_COLUMNS: TableColumn<RoleRow>[] = [
  {key: "role", label: "Role", wrap: true, sortBy: (row) => row.missing?.cohortLabel ?? row.role?.label},
  {key: "follows", label: "Follows", wrap: true, sortBy: followsWord},
  {key: "state", label: "State", sortBy: stateWord},
  {key: "access", label: "Has access to", wrap: true, sortBy: (row) => (row.role ? opensOf(row.role.externalId, channels.value) : null)},
]
const CHANNEL_COLUMNS: TableColumn<ChannelRow>[] = [
  {key: "channel", label: "Channel", wrap: true, sortBy: (row) => row.name},
  {key: "belongs", label: "Belongs to", wrap: true, sortBy: (row) => belongsTo(row, roles.value)},
  {key: "access", label: "Access", wrap: true, sortBy: (row) => accessOf(row, roles.value)},
  {key: "state", label: "Differs on Discord", sortBy: differsOf},
]

// The roles still to create first, then the ones the site manages, then the ones made by hand.
const roleRows = computed<RoleRow[]>(() => [
  ...missing.value.map((one) => ({key: `missing-${one.targetId}`, missing: one, role: null, managed: true})),
  ...kept.value.map((one) => ({key: one.externalId, missing: null, role: one, managed: true})),
  ...others.value.map((one) => ({key: one.externalId, missing: null, role: one, managed: false})),
])
const roleLink = (row: RoleRow) => (row.role ? `/management/platforms/discord/roles/${row.role.externalId}` : null)

/* Ticked roles are compared with Discord or added to who is missing them together. A role still to
   be created has nothing to compare, so it carries no tick. */
const madeRoles = computed(() => roleRows.value.filter((row) => row.role))
const {selectedIdsArray, isSelected, toggle, headerState, toggleHeader, clear: clearSelection} =
  useUserSelection(computed(() => madeRoles.value.map((row) => row.key)))
const ticked = computed(() => madeRoles.value.filter((row) => isSelected(row.key)).map((row) => row.role!))
const sync = useTargetSync(SYSTEM, ticked, ["role", "roles"])
const synced = async () => {
  clearSelection()
  await load()
}

const everyChannel = computed<ChannelRow[]>(() => groups.value.flatMap((group) => group.channels.map((one) => ({...one, category: group.name}))))
/* Archived channels are kept for history and nobody works in them, so the list leaves them out until asked. */
const ARCHIVED_SHOWN = "shown"
const archivedShown = ref(false)
const KINDS = [{key: "TEXT", label: "Text"}, {key: "VOICE", label: "Voice"}]
const VISIBILITIES = [{key: "private", label: "Private"}, {key: "public", label: "Public"}]
const kindShown = ref<string | null>(null)
const visibilityShown = ref<string | null>(null)
const channelRows = computed(() => everyChannel.value
  .filter((one) => archivedShown.value || !isArchive(one.category))
  .filter((one) => kindShown.value === null || one.kind === kindShown.value)
  .filter((one) => visibilityShown.value === null || one.private === (visibilityShown.value === "private")))
const clearChannelFilters = () => {
  archivedShown.value = false
  kindShown.value = null
  visibilityShown.value = null
}
const archivedCount = computed(() => everyChannel.value.filter((one) => isArchive(one.category)).length)

/* A role is created on Discord only after it is named once more and confirmed. */
const pendingRole = ref<MissingTarget | null>(null)
const createFailure = ref<string | null>(null)
const create = async () => {
  const role = pendingRole.value
  if (acting.value || !role) return
  acting.value = true
  const answered = await createMissingLists(SYSTEM, [role.targetId])
  acting.value = false
  if (!answered.ok) return void (createFailure.value = answered.reason)
  pendingRole.value = null
  said(answered.saved === 1 ? "The role is being created." : `${answered.saved} roles are being created.`)
  await load()
}

const kindOf = (channel: CataloguedChannel) => (channel.kind === "VOICE" ? "Voice" : channel.private ? "Private" : "Public")

/** The matches dialog: every match ticked, and linked only once confirmed. */
const reviewing = ref(false)
const picked = ref<Set<string>>(new Set())
const refusal = ref<string | null>(null)

const openReview = () => {
  picked.value = new Set(matches.value.map((one) => one.key))
  refusal.value = null
  reviewing.value = true
}

const pick = (key: string, on: boolean) => {
  const next = new Set(picked.value)
  if (on) next.add(key)
  else next.delete(key)
  picked.value = next
}


/* One line a match: the channels the role has access to once linked, which are the ones it has now
   and the ones named as the committee or team is. */
const matchAccess = (match: AdoptionMatch) => {
  const held = channels.value.filter((one) => one.kind !== "CATEGORY" && one.roleIds.includes(match.roleId)).map((one) => one.name)
  const names = [...new Set([...held, ...match.channels.map((one) => one.name)])]
  return names.length === 0 ? "Has access to no channels yet." : `Has access to ${names.map((name) => `#${name}`).join(", ")}.`
}

const adopt = async () => {
  if (picked.value.size === 0 || acting.value) return
  acting.value = true
  const answered = await adoptMatches([...picked.value])
  acting.value = false
  if (!answered.ok) return void (refusal.value = answered.reason)
  const {linked, refused} = answered.saved
  said(linked === 1 ? "1 match is linked." : `${linked} matches are linked.`)
  // A match Discord refused stays on the dialog with why; the rest are linked and gone from it.
  refusal.value = refused.length ? refused.map((one) => `${one.label}: ${one.reason}`).join(" ") : null
  reviewing.value = refused.length > 0
  await load()
}

onMounted(load)
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="discord-page"
    title="Discord"
  >
    <template #lede>
      The roles and channels the site manages in the association's server, and where Discord differs.
    </template>

    <p
      v-if="loaded && !overview"
      class="discord__note"
      data-testid="discord-unreadable"
    >
      Discord could not be read. Try again in a moment.
    </p>

    <template v-if="overview">
      <fact-list
        class="discord__facts"
        :facts="facts"
      />

      <notice-box
        v-if="matches.length"
        class="discord__notice"
        testid="discord-matches"
        :title="`${matches.length} existing ${matches.length === 1 ? 'role matches' : 'roles match'} by name`"
      >
        <p>
          Committees and teams on the site have a role or channel on Discord with the same name. Check the matches and
          link them in one go. Nothing on Discord is changed or lost.
        </p>
        <div class="discord__acts">
          <cut-button
            small
            testid="discord-review-matches"
            @click="openReview"
          >
            Review matches
          </cut-button>
        </div>
      </notice-box>

      <page-tabs
        :entries="TABS"
        label="Discord"
        testid="discord-tabs"
      />

      <management-table
        v-if="!onChannels"
        class="discord__table"
        :columns="ROLE_COLUMNS"
        :row-key="(row) => row.key"
        :row-testid="(row) => (row.missing ? `discord-role-missing-${row.missing.targetId}` : `discord-role-${row.key}`)"
        search-label="Search roles"
        :search-text="(row) => row.role?.label ?? row.missing?.cohortLabel ?? ''"
        :header-state="headerState"
        :rows="roleRows"
        :selected-count="selectedIdsArray.length"
        testid="discord-roles"
        :to="roleLink"
        :total="madeRoles.length"
        @clear-selection="clearSelection"
        @toggle-shown="toggleHeader"
      >
        <template #check="{row}">
          <row-check
            v-if="row.role"
            :checked="isSelected(row.key)"
            :label="`Select @${row.role.label}`"
            :testid="`discord-role-check-${row.key}`"
            @toggle="toggle(row.key)"
          />
        </template>
        <template #count>
          {{ kept.length + missing.length }} managed by the site, {{ others.length }} not
        </template>
        <template #empty>
          No roles yet.
        </template>
        <template #role="{row}">
          <span
            v-if="row.missing"
            class="mg-name"
          >{{ row.missing.cohortLabel }}</span>
          <router-link
            v-else-if="row.role"
            class="mg-name discord__mention"
            :to="`/management/platforms/discord/roles/${row.role.externalId}`"
          >
            @{{ row.role.label }}
          </router-link>
          <span class="mg-sub">{{ row.managed ? "Managed by the site" : "Made by hand on Discord, not managed by the site" }}</span>
        </template>
        <template #follows="{row}">
          <span class="mg-quiet">{{ followsWord(row) }}</span>
        </template>
        <template #state="{row}">
          <state-mark
            v-if="row.missing"
            kind="not-created"
          >
            {{ stateWord(row) }}
          </state-mark>
          <state-mark
            v-else-if="row.managed && row.role"
            :kind="driftOf(row.role).kind"
            :testid="`discord-role-state-${row.role.externalId}`"
          >
            {{ stateWord(row) }}
          </state-mark>
          <state-mark
            v-else
            kind="not-compared"
          >
            {{ stateWord(row) }}
          </state-mark>
        </template>
        <template #access="{row}">
          <span class="mg-quiet">{{ row.role ? opensOf(row.role.externalId, channels) : "" }}</span>
        </template>
        <template #acts="{row}">
          <mini-button
            v-if="row.missing"
            :disabled="acting || row.missing.creating"
            :testid="`discord-create-${row.missing.targetId}`"
            @click="pendingRole = row.missing; createFailure = null"
          >
            Create
          </mini-button>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="row.managed ? 'Managed by the site' : 'Not managed by the site'"
            :name="row.missing ? row.missing.cohortLabel : `@${row.role?.label}`"
            :testid="row.missing ? `discord-role-missing-${row.missing.targetId}` : `discord-role-${row.key}`"
            :to="roleLink(row) ?? ''"
          >
            <state-mark :kind="row.missing ? 'not-created' : row.managed && row.role ? driftOf(row.role).kind : 'not-compared'">
              {{ row.missing ? "No role yet" : row.managed && row.role ? driftOf(row.role).word : "Not compared" }}
            </state-mark>
          </management-row>
        </template>
      </management-table>

      <template v-if="!onChannels">
        <selection-bar
          always
          :count="selectedIdsArray.length"
          testid="discord-role-selection"
          @clear="clearSelection"
        >
          <cut-button
            small
            testid="discord-bulk-compare"
            tone="solid"
            @click="sync.task.value = 'compare'"
          >
            Compare with Discord
          </cut-button>
          <cut-button
            small
            testid="discord-bulk-push"
            @click="sync.task.value = 'push'"
          >
            Add missing people
          </cut-button>
        </selection-bar>

        <bulk-add
          :items="sync.items.value"
          :noun="['role', 'roles']"
          :open="sync.task.value !== null"
          :run="sync.run"
          :skipped="sync.skipped.value"
          testid="discord-bulk-sync"
          :title="sync.title.value"
          :words="sync.words.value"
          @done="synced"
          @update:open="sync.task.value = null"
        />
      </template>

      <management-table
        v-else
        class="discord__table"
        :columns="CHANNEL_COLUMNS"
        :row-key="(channel) => channel.id"
        :row-testid="(channel) => `discord-channel-${channel.id}`"
        search-label="Search channels"
        :search-text="(channel) => `${channel.name} ${channel.category}`"
        :rows="channelRows"
        testid="discord-channels"
        :to="(channel) => `/management/platforms/discord/channels/${channel.id}`"
      >
        <template #count>
          {{ channelRows.length }} {{ channelRows.length === 1 ? "channel" : "channels" }}{{ archivedShown || archivedCount === 0 ? "" : `, ${archivedCount} archived hidden` }}
        </template>
        <template #filters>
          <filter-bar
            :active="archivedShown || kindShown !== null || visibilityShown !== null"
            testid="discord-channel-filters"
            @clear="clearChannelFilters"
          >
            <filter-picker
              v-model="kindShown"
              label="Kind"
              :options="KINDS"
              testid="discord-channel-kind"
            />
            <filter-picker
              v-model="visibilityShown"
              label="Visibility"
              :options="VISIBILITIES"
              testid="discord-channel-visibility"
            />
            <filter-picker
              any-label="Hidden"
              label="Archived channels"
              :model-value="archivedShown ? ARCHIVED_SHOWN : null"
              :options="[{key: ARCHIVED_SHOWN, label: 'Shown'}]"
              testid="discord-channel-archived"
              @update:model-value="archivedShown = $event === ARCHIVED_SHOWN"
            />
          </filter-bar>
        </template>
        <template #empty>
          No channels yet.
        </template>
        <template #channel="{row}">
          <span class="discord__channel">
            <channel-glyph
              :locked="row.private"
              :voice="row.kind === 'VOICE'"
            />
            {{ row.name }}
          </span>
          <span class="mg-sub">{{ isArchive(row.category) ? `${row.category} · read only, kept for history` : row.category }}</span>
        </template>
        <template #belongs="{row}">
          <span class="mg-quiet">{{ belongsTo(row, roles) }}</span>
        </template>
        <template #access="{row}">
          <span class="mg-quiet">{{ accessOf(row, roles) }}</span>
        </template>
        <template #state="{row}">
          <state-mark
            :kind="row.access?.differs ? 'extra' : 'in-step'"
            :testid="`discord-channel-differs-${row.id}`"
          >
            {{ differsOf(row) }}
          </state-mark>
        </template>
        <template #phone="{row}">
          <management-row
            :meta="`${row.category} · ${kindOf(row)} · ${accessOf(row, roles)}`"
            :name="`#${row.name}`"
            :testid="`discord-channel-${row.id}`"
          >
            <state-mark :kind="row.access?.differs ? 'extra' : 'in-step'">
              {{ differsOf(row) }}
            </state-mark>
          </management-row>
        </template>
      </management-table>
    </template>

    <confirm-dialog
      confirm-label="Create the role"
      :failure="createFailure"
      :open="pendingRole !== null"
      :question="pendingRole ? `The role @${pendingRole.cohortLabel} is created on Discord and given to the ${pendingRole.memberCount} ${pendingRole.memberCount === 1 ? 'person' : 'people'} it is for. A role is deleted on Discord itself, not from here.` : ''"
      testid="discord-create-dialog"
      :title="pendingRole ? `Create @${pendingRole.cohortLabel} on Discord?` : ''"
      :working="acting"
      working-label="Creating"
      @confirm="create"
      @update:open="pendingRole = $event ? pendingRole : null"
    />

    <modal-dialog
      :open="reviewing"
      testid="discord-matches-dialog"
      title="Matches by name"
      @update:open="reviewing = $event"
    >
      <div class="discord__form">
        <p class="discord__note">
          Each one you tick is linked to the role named. The role keeps the channels it has access to, and they can be
          changed afterwards on the committee, team or role.
        </p>
        <ul class="discord__moves">
          <li
            v-for="match in matches"
            :key="match.key"
          >
            <check-box
              :hint="matchAccess(match)"
              :label="`${match.label}: @${match.roleName}`"
              :model-value="picked.has(match.key)"
              :testid="`discord-match-${match.key}`"
              @update:model-value="pick(match.key, $event)"
            />
          </li>
        </ul>
        <p
          v-if="refusal"
          class="discord__failure"
          data-testid="discord-matches-refusal"
          role="alert"
        >
          {{ refusal }}
        </p>
      </div>
      <template #footer>
        <cut-button
          :disabled="acting || picked.size === 0"
          testid="discord-matches-link"
          tone="solid"
          @click="adopt"
        >
          Link {{ picked.size }} {{ picked.size === 1 ? "match" : "matches" }}
        </cut-button>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.discord__facts {
  padding: 1.1rem 0 1.2rem;
}

.discord__notice {
  margin-bottom: 0.4rem;
}

.discord__table {
  margin-top: 1.2rem;
}

.discord__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.discord__mention {
  text-transform: none;
}

.discord__channel {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-weight: 600;
}

.discord__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.discord__moves {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.discord__note {
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.discord__failure {
  font-size: 0.88rem;
  color: var(--color-danger);
}
</style>
