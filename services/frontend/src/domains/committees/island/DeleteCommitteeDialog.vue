<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {type Committee, listCommittees, readEventsToHandOver, removeCommittee, setCommitteeArchived} from "../adapters/committees"

/**
 * Deleting a committee, with archiving offered in the same question since that is what is normally
 * meant. A committee with events names the one that takes them over; the api moves them and deletes
 * in one go, so no event is left without a committee.
 */
defineOptions({name: "DeleteCommitteeDialog"})

const props = defineProps<{open: boolean; committee: Pick<Committee, "id" | "name" | "archived">}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "archived", committee: Committee): void
  (event: "removed"): void
}>()

const events = ref<number | null>(null)
const others = ref<Committee[]>([])
const taker = ref<string | null>(null)
const working = ref(false)
const failure = ref<string | null>(null)

// The api refuses an archived committee or the one being deleted, so neither is offered.
const options = computed(() => others.value
  .filter((one) => one.id !== props.committee.id && !one.archived)
  .map((one) => ({key: String(one.id), label: one.name})))
const takerName = computed(() => options.value.find((one) => one.key === taker.value)?.label ?? null)
const ready = computed(() => events.value === 0 || (events.value != null && taker.value != null))

watch(() => props.open, async (open) => {
  if (!open) return
  failure.value = null
  taker.value = null
  events.value = null
  const [count, all] = await Promise.all([readEventsToHandOver(props.committee.id), listCommittees().catch(() => [])])
  events.value = count
  others.value = all
  if (count == null) failure.value = "How many events it organises could not be read, so it cannot be deleted now."
}, {immediate: true})

const run = async (act: () => Promise<boolean>) => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    if (await act()) emit("update:open", false)
  } finally {
    working.value = false
  }
}

// The same archiving the committee's own Archive button does.
const archive = () => run(async () => {
  const result = await setCommitteeArchived(props.committee.id, true)
  if (!result.ok) failure.value = result.reason
  else emit("archived", result.saved)
  return result.ok
})

const remove = () => run(async () => {
  const result = await removeCommittee(props.committee.id, taker.value == null ? undefined : Number(taker.value))
  if (!result.ok) failure.value = result.reason
  else emit("removed")
  return result.ok
})
</script>

<template>
  <modal-dialog
    cancel-testid="committee-remove-cancel"
    danger
    :open="open"
    testid="committee-remove-dialog"
    :title="`Delete ${committee.name}?`"
    @update:open="emit('update:open', $event)"
  >
    <div class="delete-committee">
      <p>
        A committee is normally archived: it leaves the reel and the pickers, and keeps its page and its events.
      </p>
      <p data-testid="committee-remove-what">
        Deleting removes {{ committee.name }} from every page, list and picker.
        <template v-if="events === 0">
          It organises no events.
        </template>
        <template v-else-if="events != null">
          Its {{ events }} {{ events === 1 ? "event moves" : "events move" }} to {{ takerName ?? "the committee you pick" }}, past ones included, and that committee's members can then edit {{ events === 1 ? "it" : "them" }}.
        </template>
        Its Discord role and Brevo list stay as they are, and the site stops keeping them in sync.
      </p>
      <form-field
        v-if="events != null && events > 0"
        label="Takes over its events"
        testid="committee-remove-taker"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            empty-note="No other committee runs."
            :labelled-by="labelId"
            :options="options"
            :selected-key="taker"
            testid-prefix="committee-remove-taker-picker"
            @pick="(key: string) => taker = key"
          />
        </template>
      </form-field>
      <p
        v-if="failure"
        class="delete-committee__failure"
        data-testid="committee-remove-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </div>
    <template #footer>
      <cut-button
        v-if="!committee.archived"
        :disabled="working"
        testid="committee-remove-archive"
        @click="archive"
      >
        Archive
      </cut-button>
      <cut-button
        :disabled="working || !ready"
        testid="committee-remove-confirm"
        tone="danger"
        @click="remove"
      >
        {{ working ? "Working" : "Delete" }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.delete-committee {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.delete-committee__failure {
  font-size: 0.88rem;
  color: var(--color-danger);
}
</style>
