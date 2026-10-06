<script lang="ts" setup>
/* Adding Discord roles and channels to many committees or teams at once. The api first says, row by
   row, what is linked and what is created; each row's channel can be renamed, swapped for an existing
   one or left out. Nothing happens before the second yes, a bot that may not do the work stops it
   before the first row, and the end says per row what was linked, what was created and what failed. */
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import type {DiscordPlaceRequest} from "@/services/api"
import {readBotStanding} from "../adapters/bot"
import {type CataloguedChannel, listCatalogue} from "../adapters/catalogue"
import {type DiscordPlanRow, planBulkAdd} from "../adapters/plan"
import ChannelMark from "./ChannelMark.vue"

defineOptions({name: "DiscordBulkAdd"})

export type BulkRow = {key: string; name: string; channel: string | null}

const {open, title, noun, rows, skipped = [], save, testid} = defineProps<{
  open: boolean
  title: string
  /** What a row is, one and many: ["committee", "committees"]. */
  noun: [string, string]
  rows: BulkRow[]
  /** The ticked rows left out, and why. */
  skipped?: {name: string; why: string}[]
  save: (key: string, choice: DiscordPlaceRequest) => Promise<{ok: true} | {ok: false; reason: string}>
  testid: string
}>()

const emit = defineEmits<{"update:open": [open: boolean]; done: []}>()

type Step = "plan" | "confirm" | "working" | "done"
type ChannelChoice = {mode: "new" | "existing" | "none"; name: string; channelId: string | null}
type Outcome = {key: string; name: string; lines: string[]; failure: string | null}

const NONE = "__none__"
const NEW = "__new__"

const step = ref<Step>("plan")
const plan = ref<DiscordPlanRow[] | null>(null)
const choices = ref<Record<string, ChannelChoice>>({})
const catalogue = ref<CataloguedChannel[]>([])
const blocked = ref<string | null>(null)
const failure = ref<string | null>(null)
const outcomes = ref<Outcome[]>([])
const finished = ref(0)

const count = (n: number) => `${n} ${n === 1 ? noun[0] : noun[1]}`
const texts = computed(() => catalogue.value.filter((one) => one.kind === "TEXT"))
const channelOf = (id: string | null) => texts.value.find((one) => one.id === id)

watch(() => open, async (now) => {
  if (!now) return
  step.value = "plan"
  plan.value = null
  blocked.value = null
  failure.value = null
  outcomes.value = []
  finished.value = 0
  const standing = await readBotStanding()
  if (!standing?.connected) {
    blocked.value = "The bot is not in the server, so no role or channel can be added."
    return
  }
  const lacking = [standing.manageRoles ? null : "Manage Roles", standing.manageChannels ? null : "Manage Channels"].filter(Boolean)
  if (lacking.length > 0) {
    blocked.value = `The bot lacks ${lacking.join(" and ")} on Discord, so nothing is added. Turn it on in the server's settings, then try again.`
    return
  }
  const [answered, channels] = await Promise.all([planBulkAdd(rows.map(({key, channel}) => ({key, channel}))), listCatalogue()])
  catalogue.value = channels
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  plan.value = answered.saved
  choices.value = Object.fromEntries(answered.saved.map((row) => [row.key, {
    mode: row.channel == null ? "none" : row.channel.channelId ? "existing" : "new",
    name: row.channel?.name ?? "",
    channelId: row.channel?.channelId ?? null,
  }]))
}, {immediate: true})

const roleLine = (row: DiscordPlanRow) => (row.role.kept ? "Keeps" : row.role.roleId ? "Links" : "Creates")

const channelOptions = (row: DiscordPlanRow) => [
  {key: NEW, label: "A new channel", note: `Under ${row.category}`},
  ...texts.value.map((one) => ({key: one.id, label: `#${one.name}`, note: one.category ?? undefined})),
  {key: NONE, label: "No channel"},
]
const channelKey = (key: string) => {
  const choice = choices.value[key]
  return choice?.mode === "none" ? NONE : choice?.mode === "new" ? NEW : choice?.channelId ?? NONE
}
const pickChannel = (row: DiscordPlanRow, picked: string) => {
  const was = choices.value[row.key]!
  choices.value[row.key] = picked === NONE ? {...was, mode: "none"} : picked === NEW ? {...was, mode: "new", name: was.name || slugOf(row.label)} : {...was, mode: "existing", channelId: picked}
}
const slugOf = (label: string) => label.toLowerCase().replace(/[^a-z0-9]+/g, "-").replace(/^-|-$/g, "")

const requestOf = (row: DiscordPlanRow): DiscordPlaceRequest => {
  const choice = choices.value[row.key]!
  const existing = choice.mode === "existing" && choice.channelId ? [choice.channelId] : []
  return {
    roleId: row.role.kept ? null : row.role.roleId ?? null,
    createRole: !row.role.kept && row.role.roleId == null,
    channelIds: [...new Set([...row.role.opens, ...existing])],
    createChannel: choice.mode === "new" ? choice.name.trim() || null : null,
  }
}

const doneLines = (row: DiscordPlanRow): string[] => {
  const choice = choices.value[row.key]!
  const role = row.role.kept ? `Kept @${row.role.name}` : row.role.roleId ? `Linked @${row.role.name}` : `Created @${row.role.name}`
  const channel = choice.mode === "none"
    ? "No channel"
    : choice.mode === "existing"
      ? `Linked #${channelOf(choice.channelId)?.name ?? choice.name}`
      : `#${choice.name.trim()} under ${row.category}, created unless it was there already`
  return [role, channel]
}

const go = async () => {
  step.value = "working"
  for (const row of plan.value ?? []) {
    const answered = await save(row.key, requestOf(row))
    outcomes.value = [...outcomes.value, {key: row.key, name: row.label, lines: answered.ok ? doneLines(row) : [], failure: answered.ok ? null : answered.reason}]
    finished.value += 1
  }
  step.value = "done"
  emit("done")
}

const failed = computed(() => outcomes.value.filter((one) => one.failure != null).length)
</script>

<template>
  <modal-dialog
    :cancel="false"
    :open="open"
    :testid="testid"
    :title="title"
    wide
    @update:open="emit('update:open', $event)"
  >
    <div class="bulk-discord">
      <notice-box
        v-if="blocked"
        :testid="`${testid}-blocked`"
        tone="danger"
      >
        {{ blocked }}
      </notice-box>
      <notice-box
        v-else-if="failure"
        :testid="`${testid}-failure`"
        tone="danger"
      >
        {{ failure }}
      </notice-box>
      <p
        v-else-if="plan == null"
        class="bulk-discord__said"
        :data-testid="`${testid}-reading`"
      >
        Reading what the server has.
      </p>

      <template v-else-if="step === 'plan' || step === 'confirm'">
        <p class="bulk-discord__said">
          {{ count(plan.length) }}. What exists by the same name is linked, and only what is missing is created. Nothing is added yet.
        </p>
        <ul class="bulk-discord__rows">
          <li
            v-for="row in plan"
            :key="row.key"
            class="bulk-discord__row"
            :data-testid="`${testid}-row-${row.key}`"
          >
            <span class="bulk-discord__name">{{ row.label }}</span>
            <span class="bulk-discord__role">
              <span class="bulk-discord__verb">{{ roleLine(row) }}</span>
              <role-mark :role="row.role.name" />
            </span>
            <span class="bulk-discord__channel">
              <search-picker
                compact
                :disabled="step === 'confirm'"
                :options="channelOptions(row)"
                placeholder="Search channels"
                :selected-key="channelKey(row.key)"
                :testid-prefix="`${testid}-channel-${row.key}`"
                @pick="(key: string) => pickChannel(row, key)"
              >
                <template #chosen="{option}">
                  <channel-mark
                    v-if="choices[row.key]?.mode === 'existing' && channelOf(choices[row.key]!.channelId)"
                    :locked="channelOf(choices[row.key]!.channelId)?.private"
                    :name="channelOf(choices[row.key]!.channelId)!.name"
                  />
                  <template v-else>{{ option?.label ?? "No channel" }}</template>
                </template>
              </search-picker>
              <text-input
                v-if="choices[row.key]?.mode === 'new'"
                v-model="choices[row.key]!.name"
                class="bulk-discord__name-input"
                :disabled="step === 'confirm'"
                :testid="`${testid}-channel-name-${row.key}`"
              />
            </span>
          </li>
        </ul>
        <p
          v-if="skipped.length > 0"
          class="bulk-discord__said"
          :data-testid="`${testid}-skipped`"
        >
          Left out: {{ skipped.map((one) => `${one.name} (${one.why.toLowerCase()})`).join(", ") }}.
        </p>
        <p
          v-if="step === 'confirm'"
          class="bulk-discord__ask"
          :data-testid="`${testid}-ask`"
        >
          Add these to {{ count(plan.length) }} now? This is done on Discord right away.
        </p>
      </template>

      <p
        v-else-if="step === 'working'"
        class="bulk-discord__said"
        :data-testid="`${testid}-working`"
      >
        Adding: {{ finished }} of {{ plan.length }} done.
      </p>

      <template v-else>
        <p
          class="bulk-discord__said"
          :data-testid="`${testid}-summary`"
        >
          {{ count(outcomes.length - failed) }} done{{ failed > 0 ? `, ${failed} failed` : "" }}.
        </p>
        <ul class="bulk-discord__rows">
          <li
            v-for="one in outcomes"
            :key="one.key"
            class="bulk-discord__row bulk-discord__row--done"
            :data-testid="`${testid}-done-${one.key}`"
          >
            <span class="bulk-discord__name">{{ one.name }}</span>
            <span
              v-if="one.failure"
              class="bulk-discord__failed"
            >Failed: {{ one.failure }}</span>
            <span
              v-else
              class="bulk-discord__lines"
            >{{ one.lines.join(" · ") }}</span>
          </li>
        </ul>
      </template>
    </div>

    <template #footer>
      <cut-button
        v-if="step !== 'working'"
        :testid="`${testid}-close`"
        tone="quiet"
        @click="emit('update:open', false)"
      >
        {{ step === "done" ? "Close" : "Cancel" }}
      </cut-button>
      <cut-button
        v-if="step === 'confirm'"
        :testid="`${testid}-back`"
        tone="quiet"
        @click="step = 'plan'"
      >
        Back
      </cut-button>
      <cut-button
        v-if="step === 'plan' && plan && plan.length > 0 && !blocked"
        :testid="`${testid}-continue`"
        tone="solid"
        @click="step = 'confirm'"
      >
        Continue
      </cut-button>
      <cut-button
        v-else-if="step === 'confirm' && plan"
        :testid="`${testid}-go`"
        tone="solid"
        @click="go"
      >
        Add to {{ count(plan.length) }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style>
/* Unscoped, as the other dialogs: the dialog is portalled out of this component's subtree. */
.bulk-discord {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.bulk-discord__said,
.bulk-discord__ask {
  margin: 0;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.bulk-discord__ask {
  color: var(--color-chalk);
}

.bulk-discord__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.bulk-discord__row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr) minmax(0, 1.4fr);
  gap: 0.8rem;
  align-items: center;
  padding: 0.5rem 0.8rem;
  background-color: var(--color-pit);
}

.bulk-discord__row--done {
  grid-template-columns: minmax(0, 1fr) minmax(0, 2.4fr);
}

.bulk-discord__name {
  font-weight: 600;
}

.bulk-discord__role {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
}

.bulk-discord__verb {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.bulk-discord__channel {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem;
}

.bulk-discord__name-input {
  flex: 1 1 8rem;
}

.bulk-discord__failed {
  color: var(--color-danger);
}

@media (--phone) {
  .bulk-discord__row,
  .bulk-discord__row--done {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
