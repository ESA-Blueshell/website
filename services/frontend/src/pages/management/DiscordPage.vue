<script lang="ts" setup>
/* The roles and channels the site keeps in the association's Discord server: what fills each role
   and where it drifts, what each channel belongs to and who gets in, and where Discord differs.
   Committees and teams with no role yet are offered the roles and channels named as they are. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CheckBox from "@/components/island/CheckBox.vue"
import FactList from "@/components/island/FactList.vue"
import FoldOut from "@/components/island/FoldOut.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import StateMark from "@/components/island/StateMark.vue"
import {
  type ListedTarget,
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

const create = async (targetIds: number[]) => {
  if (acting.value) return
  acting.value = true
  const answered = await createMissingLists(SYSTEM, targetIds)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
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
  <div
    class="discord"
    data-testid="discord-page"
  >
    <header class="discord__head">
      <p class="discord__eyebrow">
        Platforms
      </p>
      <h1 class="discord__title">
        Discord
      </h1>
      <p class="discord__note">
        The roles and channels the site manages in the association's server, and where Discord differs.
      </p>
    </header>

    <p
      v-if="loaded && !overview"
      class="discord__note"
      data-testid="discord-unreadable"
    >
      Discord could not be read. Try again in a moment.
    </p>

    <notice-box
      v-if="matches.length"
      testid="discord-matches"
      :title="`${matches.length} existing ${matches.length === 1 ? 'role matches' : 'roles match'} by name`"
    >
      <p>
        Committees and teams on the site have a role or channel on Discord with the same name. Check the matches and
        link them in one go; history on Discord stays.
      </p>
      <button
        class="discord__action discord__action--on-notice"
        data-testid="discord-review-matches"
        type="button"
        @click="openReview"
      >
        Review matches
      </button>
    </notice-box>

    <template v-if="overview">
      <fact-list :facts="facts" />

      <page-tabs
        :entries="TABS"
        label="Discord"
        testid="discord-tabs"
      />

      <section
        v-if="!onChannels"
        class="discord__group"
        data-testid="discord-roles"
      >
        <p class="discord__folder">
          <span>Managed by the site</span>
          <span class="discord__sub">{{ kept.length + missing.length }} {{ kept.length + missing.length === 1 ? "role" : "roles" }}</span>
        </p>
        <ul class="discord__rows">
          <li
            v-for="role in missing"
            :key="`missing-${role.targetId}`"
            class="discord__row"
            :data-testid="`discord-role-missing-${role.targetId}`"
          >
            <span class="discord__name">{{ role.cohortLabel }}</span>
            <span class="discord__sub">{{ followsOf({missing: role}) }}</span>
            <state-mark kind="not-created">
              {{ role.creating ? "Being created" : "No role yet" }}
            </state-mark>
            <span class="discord__sub" />
            <span class="discord__row-acts">
              <button
                class="discord__mini"
                :data-testid="`discord-create-${role.targetId}`"
                :disabled="acting || role.creating"
                type="button"
                @click="create([role.targetId])"
              >
                Create
              </button>
            </span>
          </li>
          <li
            v-for="role in kept"
            :key="role.externalId"
            class="discord__row"
            :data-testid="`discord-role-${role.externalId}`"
          >
            <span class="discord__name"><router-link :to="`/management/platforms/discord/roles/${role.externalId}`">@{{ role.label }}</router-link></span>
            <span class="discord__sub">{{ followsRow(role) }}</span>
            <state-mark
              :kind="driftOf(role).kind"
              :testid="`discord-role-state-${role.externalId}`"
            >
              {{ driftOf(role).word }}
            </state-mark>
            <span class="discord__sub">{{ opensOf(role.externalId, channels) }}</span>
            <span class="discord__row-acts" />
          </li>
        </ul>
      </section>

      <fold-out
        v-if="!onChannels && others.length"
        :label="`Not managed by the site · ${others.length}`"
        testid="discord-other-roles"
      >
        <p class="discord__note discord__note--small">
          Roles made by hand on Discord. The site leaves their holders alone; the role bot's roles are never listed.
        </p>
        <ul class="discord__rows">
          <li
            v-for="role in others"
            :key="role.externalId"
            class="discord__row"
            :data-testid="`discord-role-${role.externalId}`"
          >
            <span class="discord__name"><router-link :to="`/management/platforms/discord/roles/${role.externalId}`">@{{ role.label }}</router-link></span>
            <span class="discord__sub">Nothing</span>
            <span />
            <span class="discord__sub">{{ opensOf(role.externalId, channels) }}</span>
            <span />
          </li>
        </ul>
      </fold-out>

      <component
        :is="group.name === ARCHIVE_CATEGORY ? FoldOut : 'section'"
        v-for="group in onChannels ? groups : []"
        :key="group.name"
        class="discord__group"
        :data-testid="`discord-channels-${group.name}`"
        v-bind="group.name === ARCHIVE_CATEGORY ? {label: `${group.name} · ${group.channels.length} channels · read only, kept for history`, testid: `discord-channels-${group.name}`} : {}"
      >
        <p
          v-if="group.name !== ARCHIVE_CATEGORY"
          class="discord__folder"
        >
          <span>{{ group.name }}</span>
          <span class="discord__sub">{{ group.channels.length }} {{ group.channels.length === 1 ? "channel" : "channels" }}</span>
        </p>
        <ul class="discord__rows">
          <li
            v-for="channel in group.channels"
            :key="channel.id"
            class="discord__row discord__row--channel"
            :data-testid="`discord-channel-${channel.id}`"
          >
            <span class="discord__name">#{{ channel.name }}</span>
            <span class="discord__sub">{{ kindOf(channel) }}</span>
            <span class="discord__sub">{{ belongsTo(channel, roles) }}</span>
            <span class="discord__sub">{{ accessOf(channel, roles) }}</span>
            <state-mark
              :kind="channel.access?.differs ? 'extra' : 'in-step'"
              :testid="`discord-channel-differs-${channel.id}`"
            >
              {{ differsOf(channel) }}
            </state-mark>
          </li>
        </ul>
      </component>
    </template>

    <modal-dialog
      :open="reviewing"
      testid="discord-matches-dialog"
      title="Matches by name"
      @update:open="reviewing = $event"
    >
      <div class="discord__form">
        <p class="discord__note">
          Each committee or team ticked takes the role and channels named as it is. Channels the role opens already stay
          open to it.
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
        <button
          class="discord__action discord__action--main"
          data-testid="discord-matches-link"
          :disabled="acting || picked.size === 0"
          type="button"
          @click="adopt"
        >
          Link {{ picked.size }} {{ picked.size === 1 ? "match" : "matches" }}
        </button>
      </template>
    </modal-dialog>
  </div>
</template>

<style scoped>
.discord {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.discord__head {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}

.discord__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.discord__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.discord__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.discord__note--small {
  font-size: 0.86rem;
}

.discord__failure {
  margin: 0;
  color: var(--color-error, #e5484d);
}

.discord__action,
.discord__mini {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.discord__mini {
  padding: 0.25rem 0.6rem;
  font-size: 0.8rem;
}

/* The notice's tint takes the brand text below contrast, so the button keeps the page's ink. */
.discord__action--on-notice {
  align-self: flex-start;
  border-color: var(--color-brand);
}

.discord__action--main {
  align-self: flex-start;
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.discord__action:disabled,
.discord__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

.discord__group {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.discord__folder {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
  margin: 0.8rem 0 0;
  font-weight: 600;
}

.discord__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.discord__row {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1.6fr) 10rem minmax(0, 1fr) 5rem;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.discord__row--channel {
  grid-template-columns: minmax(0, 1.2fr) 5rem minmax(0, 1.2fr) minmax(0, 1.4fr) 10rem;
}

.discord__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.discord__name a {
  color: var(--color-chalk);
}

.discord__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.discord__row-acts {
  display: flex;
  justify-content: flex-end;
}

.discord__moves {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.discord__form {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

@media (max-width: 839px) {
  .discord {
    padding: 1.2rem 1.1rem 2rem;
  }

  .discord__row,
  .discord__row--channel {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .discord__row > .discord__sub {
    display: none;
  }
}
</style>
