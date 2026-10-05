<script lang="ts" setup>
/* One Discord role: what fills it, its holders and drift, and what it opens. Each category or
   channel it opens has an access; a change is written to Discord, and where Discord differs the
   row says so and offers to set it, never doing so on its own. Channels are made for the role and
   archived from here. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {
  type Cohort,
  type ListedTarget,
  TargetDrift,
  TargetSystem,
  cohortTypeLabel,
  driftRowsOf,
  fetchCohort,
  inSyncOn,
  readListedTarget,
  setTargetEnforced,
  triggerReconcile,
} from "@/domains/cohorts"
import {ChannelMark, 
  type CataloguedChannel,
  RoleAccess,
  type RoleOpeningState,
  archiveChannelOf,
  closeTo,
  createChannelFor,
  listCatalogue,
  openTo,
  openingDiffers,
  openingKind,
  opensOf,
  readOpenings,
  roleAccessWord,
  unlinkRole,
} from "@/domains/discord"
import store from "@/plugins/store"

defineOptions({name: "DiscordRolePage"})

const route = useRoute()
const roleId = computed(() => String(route.params.roleId))

const role = ref<ListedTarget | null>(null)
const cohort = ref<Cohort | null>(null)
const isAdmin = computed(() => store.getters.isAdmin === true)
const openings = ref<RoleOpeningState[] | null>(null)
const channels = ref<CataloguedChannel[]>([])
const loaded = ref(false)
const acting = ref(false)

const ACCESSES = [RoleAccess.WRITE, RoleAccess.READ, RoleAccess.SPEAK]
const COLUMNS: TableColumn<RoleOpeningState>[] = [
  {key: "channel", label: "Channel", wrap: true, sortBy: (state) => state.channel.name},
  {key: "access", label: "Access", sortBy: (state) => state.kept ?? state.actual},
  {key: "differs", label: "On Discord", wrap: true, sortBy: openingDiffers},
]

const accessOptions = computed(() => ACCESSES.map((one) => ({key: one, label: roleAccessWord(one)})))
const opened = computed(() => new Set((openings.value ?? []).map((one) => one.channel.id)))
const openable = computed(() => channels.value
  .filter((one) => !opened.value.has(one.id))
  .map((one) => ({key: one.id, label: one.kind === "CATEGORY" ? one.name : `#${one.name}`, note: one.kind === "CATEGORY" ? "Category" : one.category ?? undefined})))
const categoryOptions = computed(() => channels.value.filter((one) => one.kind === "CATEGORY").map((one) => ({key: one.name, label: one.name})))
const asCatalogue = computed(() => (openings.value ?? []).filter((one) => one.actual != null).map((one) => ({...one.channel, private: true, roleIds: [roleId.value]})))

const mapping = computed(() => cohort.value?.mappings.find((one) => one.system === TargetSystem.DISCORD) ?? null)
const holders = computed(() => {
  const members = cohort.value?.members ?? []
  const drift = driftRowsOf(members, TargetSystem.DISCORD)
  const unlinked = drift.filter((one) => one.unreachable).length
  const missing = drift.filter((one) => one.sync === "ONLY_HERE").length - unlinked
  const extra = drift.filter((one) => one.sync === "ONLY_EXTERNAL").length
  return {
    label: "Holders",
    value: `${inSyncOn(members, TargetSystem.DISCORD)} in sync`,
    sub: `${missing} missing · ${extra} additional · ${unlinked} with no Discord linked`,
    testid: "discord-role-holders",
  }
})
const facts = computed(() => [
  {label: "Follows", value: role.value?.cohortLabel ?? "Nothing", sub: role.value?.cohortType ? cohortTypeLabel(role.value.cohortType) : "Made by hand on Discord"},
  ...(cohort.value ? [holders.value] : []),
  {label: "Opens", value: opensOf(roleId.value, asCatalogue.value), sub: "Categories and channels, each at an access", testid: "discord-role-opens"},
])

const load = async () => {
  const [read, open, listed] = await Promise.all([readListedTarget(TargetSystem.DISCORD, roleId.value), readOpenings(roleId.value), listCatalogue()])
  role.value = read
  cohort.value = read?.cohortId != null ? await fetchCohort(read.cohortId) : null
  openings.value = open
  channels.value = listed
  loaded.value = true
}

const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

const act = async (call: () => Promise<{ok: true; saved: RoleOpeningState[]} | {ok: false; reason: string}>, done: string) => {
  if (acting.value) return false
  acting.value = true
  const answered = await call()
  acting.value = false
  if (!answered.ok) {
    said(answered.reason)
    return false
  }
  openings.value = answered.saved
  said(done)
  return true
}

const reconcile = async () => {
  if (!cohort.value || !mapping.value || acting.value) return
  acting.value = true
  const answered = await triggerReconcile(cohort.value.id, mapping.value.targetId)
  acting.value = false
  said(answered.ok ? "A reconcile is queued." : answered.reason)
}

const enforce = async () => {
  if (!cohort.value || !mapping.value || acting.value) return
  acting.value = true
  const answered = await setTargetEnforced(cohort.value.id, mapping.value.targetId, !mapping.value.enforced)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  await load()
}

/* Unlinking lets the role go from what it follows, so another committee, board or team can be
   linked to it. Nothing changes on Discord. */
const unlinking = ref(false)
const unlink = async () => {
  if (acting.value) return
  acting.value = true
  const answered = await unlinkRole(roleId.value)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  unlinking.value = false
  said(`@${role.value?.label ?? roleId.value} is unlinked.`)
  await load()
}

const nameOf = (state: RoleOpeningState) => (state.channel.kind === "CATEGORY" ? state.channel.name : `#${state.channel.name}`)

const setAccess = (state: RoleOpeningState, access: string) =>
  act(() => openTo(roleId.value, state.channel.id, access as RoleAccess), `${nameOf(state)} is set to ${roleAccessWord(access as RoleAccess).toLowerCase()}.`)
const remove = (state: RoleOpeningState) => act(() => closeTo(roleId.value, state.channel.id), `${nameOf(state)} is taken off the role.`)
const archive = (state: RoleOpeningState) => act(() => archiveChannelOf(roleId.value, state.channel.id), `${nameOf(state)} is in the archive.`)

/** Opening another channel or category: picked, at an access. */
const adding = ref<string | null>(null)
const addingAccess = ref<string>(RoleAccess.WRITE)
const add = async () => {
  const channel = channels.value.find((one) => one.id === adding.value)
  if (!channel) return
  const label = channel.kind === "CATEGORY" ? channel.name : `#${channel.name}`
  if (await act(() => openTo(roleId.value, channel.id, addingAccess.value as RoleAccess), `${label} is open to the role.`)) adding.value = null
}

/** The new-channel dialog: a name and the category it goes under. */
const creating = ref(false)
const newName = ref("")
const newCategory = ref<string | null>(null)
const openCreate = () => {
  newName.value = ""
  newCategory.value = categoryOptions.value[0]?.key ?? null
  creating.value = true
}
const create = async () => {
  const name = newName.value.trim()
  if (name === "" || !newCategory.value) return
  const category = newCategory.value
  if (await act(() => createChannelFor(roleId.value, name, category, RoleAccess.WRITE), `#${name} is made under ${category}.`)) {
    creating.value = false
    channels.value = await listCatalogue()
  }
}

onMounted(load)
</script>

<template>
  <management-page
    :back="{to: '/management/platforms/discord', label: 'Discord'}"
    :eyebrow="`Discord role${role?.cohortType ? ` · ${cohortTypeLabel(role.cohortType)}` : ''}`"
    testid="discord-role-page"
    :title="`@${role?.label ?? roleId}`"
  >
    <template #lede>
      {{ role?.cohortLabel
        ? `Everyone in ${role.cohortLabel} holds this role, and with it access to the channels below.`
        : "Nothing on the site decides who holds this role. The channels it has access to are set below." }}
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

    <p
      v-if="loaded && openings == null"
      class="role__note"
      data-testid="discord-role-unreadable"
    >
      Discord could not be read. Try again in a moment.
    </p>

    <template v-if="openings">
      <fact-list
        class="role__facts"
        :columns="cohort ? 3 : 2"
        :facts="facts"
      />

      <target-drift
        v-if="cohort && mapping"
        :cohort="cohort"
        :mapping="mapping"
        :reload="load"
        testid="discord-role-drift"
      />

      <list-head title="Channels the role has access to" />
      <management-table
        :columns="COLUMNS"
        :row-key="(state) => state.channel.id"
        :row-testid="(state) => `discord-opening-${state.channel.id}`"
        search-label="Search channels"
        :search-text="(state) => nameOf(state)"
        :rows="openings"
        testid="discord-role-openings"
      >
        <template #empty>
          <span data-testid="discord-role-opens-nothing">The role has access to no channel yet.</span>
        </template>
        <template #channel="{row}">
          <channel-mark
            :id="row.channel.id"
            :category="row.channel.kind === 'CATEGORY'"
            :name="row.channel.name"
            :voice="row.channel.kind === 'VOICE'"
          />
          <span class="mg-sub">{{ openingKind(row, channels) }}</span>
        </template>
        <template #access="{row}">
          <search-picker
            class="role__access"
            compact
            :options="accessOptions"
            :selected-key="row.kept ?? row.actual ?? null"
            :testid-prefix="`discord-opening-access-${row.channel.id}`"
            @pick="(key: string) => setAccess(row, key)"
          />
        </template>
        <template #differs="{row}">
          <span
            v-if="openingDiffers(row)"
            class="role__differs"
            :data-testid="`discord-opening-differs-${row.channel.id}`"
          >
            {{ openingDiffers(row) }}.
            <mini-button
              :disabled="acting"
              :testid="`discord-opening-set-${row.channel.id}`"
              @click="setAccess(row, row.kept ?? row.actual!)"
            >
              {{ row.kept ? "Set it on Discord" : "Add it to the site" }}
            </mini-button>
          </span>
          <span
            v-else
            class="mg-quiet"
          >The same</span>
        </template>
        <template #acts="{row}">
          <mini-button
            :disabled="acting"
            :testid="`discord-opening-remove-${row.channel.id}`"
            tone="danger"
            @click="remove(row)"
          >
            Remove access
          </mini-button>
          <mini-button
            v-if="row.channel.kind !== 'CATEGORY'"
            :disabled="acting"
            :testid="`discord-opening-archive-${row.channel.id}`"
            @click="archive(row)"
          >
            Archive
          </mini-button>
        </template>
      </management-table>

      <div class="role__add">
        <search-picker
          class="role__pick"
          :options="openable"
          placeholder="Link an existing channel or category"
          :selected-key="adding"
          testid-prefix="discord-open-another"
          @pick="(key: string) => adding = key"
        />
        <search-picker
          class="role__access"
          :options="accessOptions"
          :selected-key="addingAccess"
          testid-prefix="discord-open-access"
          @pick="(key: string) => addingAccess = key"
        />
        <cut-button
          :disabled="acting || adding == null"
          testid="discord-open-add"
          tone="solid"
          @click="add"
        >
          Add access
        </cut-button>
        <cut-button
          testid="discord-create-channel"
          @click="openCreate"
        >
          Create a new channel
        </cut-button>
      </div>

      <template v-if="mapping">
        <list-head title="Settings" />
        <cut-row
          :meta="`The site decides who holds this role from ${role?.cohortLabel ?? 'what it follows'}. Unlink it to link the role to something else.`"
          title="Linked on the site"
        >
          <template #end>
            <cut-button
              :disabled="acting"
              small
              testid="discord-role-unlink"
              @click="unlinking = true"
            >
              Unlink
            </cut-button>
          </template>
        </cut-row>
        <cut-row
          v-if="isAdmin"
          :meta="`Remove the role from additional holders every time it is compared. ${mapping.enforced ? 'On.' : 'Off.'}`"
          title="Enforce"
        >
          <template #end>
            <cut-button
              :disabled="acting"
              small
              testid="discord-role-enforce"
              @click="enforce"
            >
              {{ mapping.enforced ? "Turn off" : "Turn on" }}
            </cut-button>
          </template>
        </cut-row>
      </template>
    </template>

    <modal-dialog
      danger
      :open="unlinking"
      testid="discord-unlink-dialog"
      :title="`Unlink @${role?.label ?? roleId}`"
      @update:open="unlinking = $event"
    >
      <p class="role__note">
        The site will stop deciding who holds this role from {{ role?.cohortLabel ?? "what it follows" }}.
        The role stays on Discord with the people who hold it and the channels it has access to.
      </p>
      <template #footer>
        <cut-button
          :disabled="acting"
          testid="discord-unlink-confirm"
          tone="danger"
          @click="unlink"
        >
          Unlink the role
        </cut-button>
      </template>
    </modal-dialog>

    <modal-dialog
      :open="creating"
      testid="discord-create-dialog"
      title="Create a new channel"
      @update:open="creating = $event"
    >
      <form
        class="role__form"
        @submit.prevent="create"
      >
        <p class="role__note">
          A new text channel that only this role can access, under the category you pick.
        </p>
        <form-field
          v-slot="field"
          label="Name"
          testid="discord-create-name"
        >
          <text-input
            v-model="newName"
            :control-id="field.controlId"
          />
        </form-field>
        <form-field
          label="Category"
          testid="discord-create-category"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              :labelled-by="labelId"
              :options="categoryOptions"
              :selected-key="newCategory"
              testid-prefix="discord-create-category-picker"
              @pick="(key: string) => newCategory = key"
            />
          </template>
        </form-field>
      </form>
      <template #footer>
        <cut-button
          :disabled="acting || newName.trim() === '' || !newCategory"
          testid="discord-create-confirm"
          tone="solid"
          @click="create"
        >
          Create the channel
        </cut-button>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.role__facts {
  padding: 1.1rem 0 0.6rem;
}

.role__channel {
  font-weight: 600;
}

.role__access {
  min-width: 11rem;
}

.role__pick {
  flex: 1 1 18rem;
  max-width: 28rem;
}

.role__differs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.3rem 0.6rem;
  font-size: 0.84rem;
  color: var(--color-warning);
}

.role__add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.5rem;
  padding-top: 0.8rem;
}

.role__form {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.role__note {
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--color-ash);
}
</style>
