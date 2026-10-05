<script lang="ts" setup generic="T extends {key: string | number; name: string; note?: string}">
/* Adding the same thing to many rows at once: first what will be added and to whom, then one more
   question, then the work, one row after another, and what could not be done at the end. Nothing is
   made before the second yes. */
import {computed, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"

const {open, title, each, noun, items, skipped = [], run, testid} = defineProps<{
  open: boolean
  /** What the task is called: "Add Discord roles". */
  title: string
  /** What each row gets, after "will get": "a Discord role and a private channel". */
  each: string
  /** What a row is, one and many: ["committee", "committees"]. */
  noun: [string, string]
  items: T[]
  /** The ticked rows left out, and why. */
  skipped?: {name: string; why: string}[]
  run: (item: T) => Promise<{ok: true} | {ok: false; reason: string}>
  testid: string
}>()

const emit = defineEmits<{"update:open": [open: boolean]; done: []}>()

type Step = "preview" | "confirm" | "working" | "done"
const step = ref<Step>("preview")
const finished = ref(0)
const failures = ref<{name: string; reason: string}[]>([])

watch(() => open, (now) => {
  if (!now) return
  step.value = "preview"
  finished.value = 0
  failures.value = []
})

const count = computed(() => `${items.length} ${items.length === 1 ? noun[0] : noun[1]}`)

const go = async () => {
  step.value = "working"
  for (const item of items) {
    const answered = await run(item)
    if (!answered.ok) failures.value = [...failures.value, {name: item.name, reason: answered.reason}]
    finished.value += 1
  }
  step.value = "done"
  emit("done")
}
</script>

<template>
  <modal-dialog
    :open="open"
    :testid="testid"
    :title="title"
    @update:open="emit('update:open', $event)"
  >
    <div
      class="bulk-add"
      :data-testid="`${testid}-${step}`"
    >
      <template v-if="step === 'preview'">
        <p v-if="items.length">
          {{ count }} will each get {{ each }}. Nothing is added yet.
        </p>
        <p v-else>
          None of the selected {{ noun[1] }} can get {{ each }}.
        </p>
        <ul
          v-if="items.length"
          class="bulk-add__list"
        >
          <li
            v-for="item in items"
            :key="item.key"
          >
            <span>{{ item.name }}</span>
            <span class="bulk-add__note">{{ item.note }}</span>
          </li>
        </ul>
        <template v-if="skipped.length">
          <p class="bulk-add__note">
            Left out:
          </p>
          <ul class="bulk-add__list">
            <li
              v-for="one in skipped"
              :key="one.name"
            >
              <span>{{ one.name }}</span>
              <span class="bulk-add__note">{{ one.why }}</span>
            </li>
          </ul>
        </template>
      </template>
      <p v-else-if="step === 'confirm'">
        Add {{ each }} to {{ count }} now? This is done right away and is not undone from here.
      </p>
      <p
        v-else-if="step === 'working'"
        role="status"
      >
        Adding, {{ finished }} of {{ items.length }} done.
      </p>
      <template v-else>
        <p role="status">
          {{ items.length - failures.length }} of {{ items.length }} added.
        </p>
        <ul
          v-if="failures.length"
          class="bulk-add__list"
          :data-testid="`${testid}-failures`"
        >
          <li
            v-for="one in failures"
            :key="one.name"
          >
            <span>{{ one.name }}</span>
            <span class="bulk-add__failure">{{ one.reason }}</span>
          </li>
        </ul>
      </template>
    </div>
    <template #footer>
      <div class="bulk-add__acts">
        <cut-button
          v-if="step === 'preview'"
          :disabled="items.length === 0"
          :testid="`${testid}-continue`"
          tone="solid"
          @click="step = 'confirm'"
        >
          Continue
        </cut-button>
        <template v-else-if="step === 'confirm'">
          <cut-button
            :testid="`${testid}-go`"
            tone="solid"
            @click="go"
          >
            Add to {{ count }}
          </cut-button>
          <cut-button
            :testid="`${testid}-back`"
            tone="quiet"
            @click="step = 'preview'"
          >
            Back
          </cut-button>
        </template>
        <cut-button
          v-if="step !== 'working'"
          :testid="`${testid}-close`"
          tone="quiet"
          @click="emit('update:open', false)"
        >
          {{ step === "done" ? "Close" : "Cancel" }}
        </cut-button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.bulk-add {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
}

.bulk-add__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bulk-add__list li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  padding: 0.4rem 0.7rem;
  font-size: 0.9rem;
  background-color: var(--color-pit);
}

.bulk-add__note {
  font-size: 0.86rem;
  color: var(--color-ash);
}

.bulk-add__failure {
  color: var(--color-danger);
}

.bulk-add__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  padding-top: 1rem;
}
</style>
