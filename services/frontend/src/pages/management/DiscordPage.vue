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
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {
  type ListedTarget,
  type MissingTarget,
  type TargetOverview,
  TargetSystem,
  createMissingLists,
  driftOf,
  followsOf,
  readTargetOverview,
} from "@/domains/cohorts"
import {
  ARCHIVE_CATEGORY,
  type AdoptionMatch,
  type CataloguedChannel,
  type NamedRole,
  accessOf,
  adoptMatches,
  belongsTo,
  catalogueFacts,
  channelGroups,
  differsOf,
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

const ROLE_COLUMNS: TableColumn[] = [
  {key: "role", label: "Role", wrap: true},
  {key: "follows", label: "Follows", wrap: true},
  {key: "state", label: "State"},
  {key: "access", label: "Has access to", wrap: true},
]
const CHANNEL_COLUMNS: TableColumn[] = [
  {key: "channel", label: "Channel", wrap: true},
  {key: "kind", label: "Kind"},
  {key: "belongs", label: "Belongs to", wrap: true},
  {key: "access", label: "Access", wrap: true},
  {key: "state", label: "Differs on Discord"},
]

interface RoleRow {
  key: string
  missing: MissingTarget | null
  role: ListedTarget | null
  managed: boolean
}

// The roles still to create first, then the ones the site manages, then the ones made by hand.
const roleRows = computed<RoleRow[]>(() => [
  ...missing.value.map((one) => ({key: `missing-${one.targetId}`, missing: one, role: null, managed: true})),
  ...kept.value.map((one) => ({key: one.externalId, missing: null, role: one, managed: true})),
  ...others.value.map((one) => ({key: one.externalId, missing: null, role: one, managed: false})),
])
const roleLink = (row: RoleRow) => (row.role ? `/management/platforms/discord/roles/${row.role.externalId}` : null)

type ChannelRow = CataloguedChannel & {category: string}
const channelRows = computed<ChannelRow[]>(() => groups.value.flatMap((group) => group.channels.map((one) => ({...one, category: group.name}))))

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
const followsRow = (list: ListedTarget) => followsOf({list})

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

const channelNames = (match: AdoptionMatch) =>
  match.channels.length === 0 ? "no channel" : match.channels.map((one) => `#${one.name}`).join(", ")

const adopt = async () => {
  if (picked.value.size === 0 || acting.value) return
  acting.value = true
  const answered = await adoptMatches([...picked.value])
  acting.value = false
  if (!answered.ok) return void (refusal.value = answered.reason)
  reviewing.value = false
  said(answered.saved === 1 ? "1 match is linked." : `${answered.saved} matches are linked.`)
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
        :rows="roleRows"
        testid="discord-roles"
        :to="roleLink"
      >
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
          <span class="mg-quiet">{{ row.missing ? followsOf({missing: row.missing}) : row.managed && row.role ? followsRow(row.role) : "Nothing" }}</span>
        </template>
        <template #state="{row}">
          <state-mark
            v-if="row.missing"
            kind="not-created"
          >
            {{ row.missing.creating ? "Being created" : "No role yet" }}
          </state-mark>
          <state-mark
            v-else-if="row.managed && row.role"
            :kind="driftOf(row.role).kind"
            :testid="`discord-role-state-${row.role.externalId}`"
          >
            {{ driftOf(row.role).word }}
          </state-mark>
          <state-mark
            v-else
            kind="not-compared"
          >
            Not compared
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

      <management-table
        v-else
        class="discord__table"
        :columns="CHANNEL_COLUMNS"
        :row-key="(channel) => channel.id"
        :row-testid="(channel) => `discord-channel-${channel.id}`"
        :rows="channelRows"
        testid="discord-channels"
      >
        <template #count>
          {{ channelRows.length }} {{ channelRows.length === 1 ? "channel" : "channels" }} in {{ groups.length }} {{ groups.length === 1 ? "category" : "categories" }}
        </template>
        <template #empty>
          No channels yet.
        </template>
        <template #channel="{row}">
          <span class="discord__channel">#{{ row.name }}</span>
          <span class="mg-sub">{{ row.category === ARCHIVE_CATEGORY ? `${row.category} · read only, kept for history` : row.category }}</span>
        </template>
        <template #kind="{row}">
          {{ kindOf(row) }}
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
          Each committee or team you tick is linked to the role and the channels named. Channels the role already has
          access to stay as they are.
        </p>
        <ul class="discord__moves">
          <li
            v-for="match in matches"
            :key="match.key"
          >
            <check-box
              :label="`${match.label}: @${match.roleName} and ${channelNames(match)}`"
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
