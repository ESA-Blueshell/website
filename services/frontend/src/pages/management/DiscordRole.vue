<script lang="ts" setup>
/* One Discord role: what fills it, its holders and drift, and what it opens. Each category or
   channel it opens has an access; a change is written to Discord, and where Discord differs the
   row says so and offers to set it, never doing so on its own. Channels are made for the role and
   archived from here. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import FactList from "@/components/island/FactList.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  type Cohort,
  type ListedTarget,
  TargetDrift,
  TargetSystem,
  cohortTypeLabel,
  driftRowsOf,
  fetchCohort,
  inStepOn,
  readListedTarget,
  setTargetEnforced,
  triggerReconcile,
} from "@/domains/cohorts"
import {
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
    value: `${inStepOn(members, TargetSystem.DISCORD)} in step`,
    sub: `${missing} missing · ${extra} extra · ${unlinked} with no Discord linked`,
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
  <div
    class="role"
    data-testid="discord-role-page"
  >
    <router-link
      class="role__back"
      to="/management/platforms/discord"
    >
      Discord
    </router-link>
    <header class="role__head">
      <p class="role__eyebrow">
        Discord role{{ role?.cohortType ? ` · ${cohortTypeLabel(role.cohortType)}` : "" }}
      </p>
      <h1 class="role__title">
        @{{ role?.label ?? roleId }}
      </h1>
      <p class="role__note">
        {{ role?.cohortLabel ? `Everyone in ${role.cohortLabel} holds this role, and with it what it opens below.` : "Nothing on the site fills this role; what it opens is set below." }}
      </p>
      <button
        v-if="mapping"
        class="role__action role__reconcile"
        data-testid="discord-role-reconcile"
        :disabled="acting"
        type="button"
        @click="reconcile"
      >
        Reconcile now
      </button>
    </header>

    <p
      v-if="loaded && openings == null"
      class="role__note"
      data-testid="discord-role-unreadable"
    >
      Discord could not be read. Try again in a moment.
    </p>

    <template v-if="openings">
      <fact-list
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

      <section
        class="role__group"
        data-testid="discord-role-openings"
      >
        <p class="role__folder">
          What it opens
        </p>
        <p
          v-if="openings.length === 0"
          class="role__note"
          data-testid="discord-role-opens-nothing"
        >
          The role opens nothing yet.
        </p>
        <ul class="role__rows">
          <li
            v-for="state in openings"
            :key="state.channel.id"
            class="role__row"
            :data-testid="`discord-opening-${state.channel.id}`"
          >
            <span class="role__name">{{ nameOf(state) }}</span>
            <span class="role__sub">{{ openingKind(state, channels) }}</span>
            <search-picker
              :options="accessOptions"
              :selected-key="state.kept ?? state.actual ?? null"
              :testid-prefix="`discord-opening-access-${state.channel.id}`"
              @pick="(key: string) => setAccess(state, key)"
            />
            <span class="role__acts">
              <button
                class="role__mini"
                :data-testid="`discord-opening-remove-${state.channel.id}`"
                :disabled="acting"
                type="button"
                @click="remove(state)"
              >
                Remove
              </button>
              <button
                v-if="state.channel.kind !== 'CATEGORY'"
                class="role__mini"
                :data-testid="`discord-opening-archive-${state.channel.id}`"
                :disabled="acting"
                type="button"
                @click="archive(state)"
              >
                Archive
              </button>
            </span>
            <p
              v-if="openingDiffers(state)"
              class="role__differs"
              :data-testid="`discord-opening-differs-${state.channel.id}`"
            >
              {{ openingDiffers(state) }}.
              <button
                class="role__mini"
                :data-testid="`discord-opening-set-${state.channel.id}`"
                :disabled="acting"
                type="button"
                @click="setAccess(state, state.kept ?? state.actual!)"
              >
                {{ state.kept ? "Set it on Discord" : "Set it on the site" }}
              </button>
            </p>
          </li>
        </ul>

        <div class="role__add">
          <search-picker
            :options="openable"
            placeholder="Open another channel or category"
            :selected-key="adding"
            testid-prefix="discord-open-another"
            @pick="(key: string) => adding = key"
          />
          <search-picker
            :options="accessOptions"
            :selected-key="addingAccess"
            testid-prefix="discord-open-access"
            @pick="(key: string) => addingAccess = key"
          />
          <button
            class="role__action"
            data-testid="discord-open-add"
            :disabled="acting || adding == null"
            type="button"
            @click="add"
          >
            Open it
          </button>
          <button
            class="role__action"
            data-testid="discord-create-channel"
            type="button"
            @click="openCreate"
          >
            Create a channel
          </button>
        </div>
      </section>

      <button
        v-if="mapping && isAdmin"
        class="role__setting"
        data-testid="discord-role-enforce"
        :disabled="acting"
        type="button"
        @click="enforce"
      >
        <span>
          <span class="role__setting-title">Enforce</span>
          <span class="role__sub">Remove the role from extra holders at every reconcile. {{ mapping.enforced ? "On." : "Off." }}</span>
        </span>
        <span class="role__sub">{{ mapping.enforced ? "Turn off" : "Turn on" }}</span>
      </button>
    </template>

    <modal-dialog
      :open="creating"
      testid="discord-create-dialog"
      title="Create a channel"
      @update:open="creating = $event"
    >
      <form
        class="role__form"
        @submit.prevent="create"
      >
        <p class="role__note">
          A text channel only this role sees, under the category picked.
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
        <button
          class="role__action role__action--main"
          data-testid="discord-create-confirm"
          :disabled="acting || newName.trim() === '' || !newCategory"
          type="button"
          @click="create"
        >
          Create
        </button>
      </template>
    </modal-dialog>
  </div>
</template>

<style scoped>
.role {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.role__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.role__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.role__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.role__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.role__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.role__action,
.role__mini {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.role__mini {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

.role__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.role__action:disabled,
.role__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

.role__reconcile {
  align-self: flex-start;
  margin-top: 0.4rem;
}

.role__setting {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.9rem 1.1rem;
  border: 0;
  background-color: var(--band-ground);
  font: inherit;
  color: var(--color-chalk);
  text-align: left;
  cursor: pointer;
}

.role__setting:disabled {
  opacity: 0.45;
  cursor: default;
}

.role__setting-title {
  display: block;
  font-weight: 600;
}

.role__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.role__folder {
  margin: 0.8rem 0 0;
  font-weight: 600;
}

.role__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.role__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) 12rem auto;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.role__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.role__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.role__acts {
  display: flex;
  justify-content: flex-end;
  gap: 0.4rem;
}

.role__differs {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  margin: 0;
  padding-left: 0.6rem;
  border-left: 3px solid var(--color-brand);
  font-size: 0.84rem;
}

.role__add {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) 12rem auto auto;
  align-items: center;
  gap: 0.6rem;
  margin-top: 0.6rem;
}

.role__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

@media (max-width: 839px) {
  .role {
    padding: 1.2rem 1.1rem 2rem;
  }

  .role__row,
  .role__add {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
