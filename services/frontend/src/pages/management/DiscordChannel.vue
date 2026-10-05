<script lang="ts" setup>
/* One Discord channel: which roles have access to it, and at what. The same access a role's own
   page sets, set here from the channel's side: a role is given access, its access is changed, or it
   is removed, and each change is written to Discord. */
import RoleMark from "@/components/island/RoleMark.vue"
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FactList from "@/components/island/FactList.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {type ListedTarget, TargetSystem, readTargetOverview} from "@/domains/cohorts"
import {type CataloguedChannel, RoleAccess, closeTo, isArchive, listCatalogue, openTo, readOpenings, roleAccessWord} from "@/domains/discord"
import store from "@/plugins/store"

defineOptions({name: "DiscordChannelPage"})

type Holder = {roleId: string; name: string; follows: string | null; access: RoleAccess | null}

const route = useRoute()
const channelId = computed(() => String(route.params.channelId))

const channel = ref<CataloguedChannel | null>(null)
const roles = ref<ListedTarget[]>([])
const holders = ref<Holder[]>([])
const loaded = ref(false)
const acting = ref(false)

const COLUMNS: TableColumn<Holder>[] = [
  {key: "role", label: "Role", wrap: true, sortBy: (holder) => holder.name},
  {key: "access", label: "Access", sortBy: (holder) => holder.access},
]

const voice = computed(() => channel.value?.kind === "VOICE")
const accessOptions = computed(() =>
  (voice.value ? [RoleAccess.SPEAK] : [RoleAccess.WRITE, RoleAccess.READ]).map((one) => ({key: one, label: roleAccessWord(one)})))
const name = computed(() => (channel.value ? (channel.value.kind === "CATEGORY" ? channel.value.name : `#${channel.value.name}`) : ""))
const addable = computed(() => {
  const held = new Set(holders.value.map((one) => one.roleId))
  return roles.value.filter((one) => !held.has(one.externalId)).map((one) => ({key: one.externalId, label: `@${one.label}`, note: one.cohortLabel ?? undefined}))
})
const facts = computed(() => (channel.value ? [
  {label: "Kind", value: `${channel.value.private ? "Private" : "Public"} ${voice.value ? "voice" : "text"}`, sub: channel.value.category ?? "No category"},
  {label: "Roles with access", value: String(holders.value.length), sub: channel.value.private ? "Nobody else gets in" : "Everybody else gets in too"},
] : []))

const load = async () => {
  const [listed, overview] = await Promise.all([listCatalogue(), readTargetOverview(TargetSystem.DISCORD)])
  channel.value = listed.find((one) => one.id === channelId.value) ?? null
  roles.value = overview?.lists ?? []
  const named = new Map(roles.value.map((one) => [one.externalId, one]))
  // A role's access is the role's to say, so each role with access is asked for its own.
  holders.value = await Promise.all((channel.value?.roleIds ?? []).map(async (roleId) => {
    const opening = (await readOpenings(roleId))?.find((one) => one.channel.id === channelId.value)
    return {roleId, name: named.get(roleId)?.label ?? roleId, follows: named.get(roleId)?.cohortLabel ?? null, access: opening?.kept ?? opening?.actual ?? null}
  }))
  loaded.value = true
}

const said = (message: string) => store.commit("setStatusSnackbarMessage", message)

const act = async (call: () => Promise<{ok: true} | {ok: false; reason: string}>, done: string) => {
  if (acting.value) return false
  acting.value = true
  const answered = await call()
  acting.value = false
  if (!answered.ok) {
    said(answered.reason)
    return false
  }
  said(done)
  await load()
  return true
}

const setAccess = (holder: Holder, access: string) =>
  act(() => openTo(holder.roleId, channelId.value, access as RoleAccess), `@${holder.name} is set to ${roleAccessWord(access as RoleAccess).toLowerCase()}.`)
const remove = (holder: Holder) => act(() => closeTo(holder.roleId, channelId.value), `@${holder.name} no longer has access to ${name.value}.`)

/** Giving another role access: picked, at an access. */
const adding = ref<string | null>(null)
const addingAccess = ref<string | null>(null)
const add = async () => {
  const role = roles.value.find((one) => one.externalId === adding.value)
  const access = (addingAccess.value ?? accessOptions.value[0]?.key) as RoleAccess | undefined
  if (!role || !access) return
  if (await act(() => openTo(role.externalId, channelId.value, access), `@${role.label} has access to ${name.value}.`)) adding.value = null
}

onMounted(load)
</script>

<template>
  <management-page
    :back="{to: '/management/platforms/discord/channels', label: 'Channels'}"
    :eyebrow="`Discord channel${channel && isArchive(channel.category) ? ' · archived' : ''}`"
    testid="discord-channel-page"
    :title="name || channelId"
  >
    <template #lede>
      The roles that have access to this channel. Add a role, change its access or remove it.
    </template>

    <p
      v-if="loaded && !channel"
      class="channel__note"
      data-testid="discord-channel-missing"
    >
      Discord has no such channel, or could not be read. Go back to the list and try again.
    </p>

    <template v-if="channel">
      <fact-list
        class="channel__facts"
        :columns="2"
        :facts="facts"
      />

      <list-head title="Roles with access" />
      <management-table
        :columns="COLUMNS"
        :row-key="(holder) => holder.roleId"
        :row-testid="(holder) => `discord-channel-role-${holder.roleId}`"
        :rows="holders"
        search-label="Search roles"
        :search-text="(holder) => `${holder.name} ${holder.follows ?? ''}`"
        testid="discord-channel-roles"
      >
        <template #empty>
          <span data-testid="discord-channel-no-roles">No role has access to this channel yet.</span>
        </template>
        <template #role="{row}">
          <router-link
            class="mg-name"
            :to="`/management/platforms/discord/roles/${row.roleId}`"
          >
            <role-mark :role="row.name" />
          </router-link>
          <span class="mg-sub">{{ row.follows ? `Follows ${row.follows}` : "Follows nothing on the site" }}</span>
        </template>
        <template #access="{row}">
          <search-picker
            class="channel__access"
            compact
            :options="accessOptions"
            :selected-key="row.access"
            :testid-prefix="`discord-channel-access-${row.roleId}`"
            @pick="(key: string) => setAccess(row, key)"
          />
        </template>
        <template #acts="{row}">
          <mini-button
            :disabled="acting"
            :testid="`discord-channel-remove-${row.roleId}`"
            tone="danger"
            @click="remove(row)"
          >
            Remove access
          </mini-button>
        </template>
      </management-table>

      <div class="channel__add">
        <search-picker
          class="channel__pick"
          :options="addable"
          placeholder="Add another role"
          :selected-key="adding"
          testid-prefix="discord-channel-add"
          @pick="(key: string) => adding = key"
        />
        <search-picker
          class="channel__access"
          :options="accessOptions"
          :selected-key="addingAccess ?? accessOptions[0]?.key ?? null"
          testid-prefix="discord-channel-add-access"
          @pick="(key: string) => addingAccess = key"
        />
        <cut-button
          :disabled="acting || !adding"
          testid="discord-channel-add-go"
          tone="solid"
          @click="add"
        >
          Add access
        </cut-button>
      </div>
    </template>
  </management-page>
</template>

<style scoped>
.channel__facts {
  padding: 1.1rem 0 1.2rem;
}

.channel__note {
  padding-top: 1.2rem;
  color: var(--color-ash);
}

.channel__access {
  min-width: 11rem;
}

.channel__add {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  margin-top: 1rem;
}

.channel__pick {
  flex: 1 1 18rem;
  max-width: 28rem;
}
</style>
