<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {
  approveDeclaration,
  BookingKind,
  day,
  type Declaration,
  DeclarationState,
  euros,
  readDeclaration,
  ReceiptStrip,
  rejectDeclaration,
  sendBackDeclaration,
  STATE_MARKS,
  stateOf,
  stepAt,
  stepLabel,
  titleOf,
  totalOf,
  withTheBoard,
} from "@/domains/declarations"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"

/** One declaration as the board checks it: the expenses, the receipts and what happened so far. */
defineOptions({name: "DeclarationReviewPage"})

const route = useRoute()
const declaration = ref<Declaration | null>(null)
const loaded = ref(false)

onMounted(async () => {
  declaration.value = await readDeclaration(Number(route.params.id))
  loaded.value = true
})

const total = computed(() => (declaration.value ? totalOf(declaration.value.expenses) : 0))
const submitted = computed(() => (declaration.value ? stepAt(declaration.value, DeclarationState.SUBMITTED) : null))
const actable = computed(() => !!declaration.value && withTheBoard(declaration.value))

const working = ref(false)
const failure = ref<string | null>(null)

const move = async (answer: () => Promise<Saved<Declaration> | Refused>) => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    const moved = await answer()
    if (moved.ok) declaration.value = moved.saved
    else failure.value = moved.reason
  } finally {
    working.value = false
    asking.value = null
  }
}

/* Sending back and rejecting both need a reason, which goes to the submitter by email. */
const asking = ref<"sendBack" | "reject" | null>(null)
const reason = ref("")
const ask = (kind: "sendBack" | "reject") => {
  reason.value = ""
  asking.value = kind
}
// The dialog closes itself; it opens only through ask.
const reasonOpen = computed({
  get: () => asking.value != null,
  set: (open: boolean) => {
    if (!open) asking.value = null
  },
})
const answerAsked = () => {
  const id = declaration.value!.id
  const said = reason.value
  return move(() => (asking.value === "reject" ? rejectDeclaration(id, said) : sendBackDeclaration(id, said)))
}

const lede = computed(() => (declaration.value
  ? [euros(total.value), titleOf(declaration.value), submitted.value ? `Submitted ${day(submitted.value.at)}` : null].filter(Boolean).join(" · ")
  : ""))
</script>

<template>
  <management-page
    :back="{to: '/management/declarations', label: 'Declarations'}"
    eyebrow="Money · Declaration"
    testid="declaration-review"
    :title="declaration ? declaration.submitter : 'Declaration'"
  >
    <template
      v-if="declaration"
      #lede
    >
      {{ lede }}
    </template>
    <template
      v-if="declaration && actable"
      #actions
    >
      <cut-button
        v-if="declaration.state === DeclarationState.SUBMITTED"
        :disabled="working"
        testid="declaration-approve"
        tone="solid"
        @click="move(() => approveDeclaration(declaration!.id))"
      >
        Approve
      </cut-button>
      <cut-button
        :disabled="working"
        testid="declaration-send-back"
        @click="ask('sendBack')"
      >
        Send back
      </cut-button>
      <cut-button
        :disabled="working"
        testid="declaration-reject"
        tone="danger"
        @click="ask('reject')"
      >
        Reject
      </cut-button>
    </template>

    <p
      v-if="loaded && !declaration"
      class="review__note"
      data-testid="declaration-review-missing"
    >
      This declaration does not exist, or it was never submitted.
    </p>

    <template v-else-if="declaration">
      <state-mark
        class="review__state"
        :kind="STATE_MARKS[declaration.state]"
        testid="declaration-review-state"
      >
        {{ stateOf(declaration).label }}
      </state-mark>

      <notice-box
        v-if="failure"
        testid="declaration-review-failure"
        tone="danger"
      >
        {{ failure }}
      </notice-box>

      <task-layout
        aside-title="What happened"
        wide
      >
        <section>
          <p class="review__title">
            Expenses
          </p>
          <ul class="review__rows">
            <li
              v-for="(expense, index) in declaration.expenses"
              :key="expense.id"
              class="review__expense"
              :data-testid="`declaration-review-expense-${index}`"
            >
              <receipt-strip
                :receipts="expense.receipts"
                :testid="`declaration-review-receipts-${index}`"
              />
              <span class="review__expense-words">
                <span class="review__expense-name">{{ expense.description }}</span>
                <span class="review__meta">
                  {{ [expense.paidOn ? `Paid ${day(expense.paidOn)}` : "No date given", expense.for ? `${expense.for.kind === BookingKind.EVENT ? "Event" : "Committee"}: ${expense.for.name}` : null].filter(Boolean).join(" · ") }}
                </span>
              </span>
              <span class="review__value">{{ euros(Number.parseFloat(expense.amount) || 0) }}</span>
            </li>
            <li class="review__pair review__total">
              <span>Total</span>
              <span class="review__value">{{ euros(total) }}</span>
            </li>
          </ul>
        </section>

        <section v-if="declaration.receipts.length > 0">
          <p class="review__title">
            Receipts for the whole declaration
          </p>
          <receipt-strip
            :receipts="declaration.receipts"
            testid="declaration-review-receipts"
          />
        </section>

        <template #aside>
          <ol
            class="review__steps"
            data-testid="declaration-review-history"
          >
            <li
              v-for="(step, index) in declaration.steps"
              :key="step.at + step.state"
            >
              <span>{{ stepLabel(step, index) }}<template v-if="step.by"> by {{ step.by }}</template></span>
              <span class="review__when">{{ day(step.at) }}</span>
              <span
                v-if="step.reason"
                class="review__reason"
              >"{{ step.reason }}"</span>
            </li>
          </ol>
        </template>
      </task-layout>
    </template>

    <modal-dialog
      v-model:open="reasonOpen"
      testid="declaration-reason-dialog"
      :title="asking === 'reject' ? 'Reject this declaration?' : 'Send this declaration back?'"
    >
      <p class="review__ask">
        {{ asking === "reject"
          ? "They get an email with your reason. A rejected declaration cannot be submitted again."
          : "They get an email with your reason. They can then change the declaration and submit it again." }}
      </p>
      <form-control
        v-model="reason"
        kind="textarea"
        label="Reason*"
        testid="declaration-reason"
      />
      <template #footer>
        <cut-button
          :disabled="!reason.trim() || working"
          testid="declaration-reason-confirm"
          :tone="asking === 'reject' ? 'danger' : 'solid'"
          @click="answerAsked"
        >
          {{ asking === "reject" ? "Reject" : "Send back" }}
        </cut-button>
      </template>
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.review__state {
  display: flex;
  width: fit-content;
}

.review__note {
  margin: 0;
  color: var(--color-ash);
}

/* The section names the Management pages use. */
.review__title {
  margin: 0 0 0.7rem;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

/* Rows as the Management pages list facts: the name at the left, its value at the right. */
.review__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.review__pair,
.review__expense {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.5rem 0.8rem;
  font-size: 0.88rem;
  background-color: var(--band-ground, var(--color-surface));
}

.review__pair {
  min-height: 2.6rem;
}

.review__pair > :first-child {
  flex: none;
  width: 7rem;
}

.review__value {
  flex: 1;
  text-align: right;
  font-variant-numeric: tabular-nums;
  color: var(--color-ash);
  overflow-wrap: anywhere;
}

.review__expense-words {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-width: 0;
}

.review__expense .review__value {
  flex: none;
}

.review__meta {
  font-size: 0.78rem;
  color: var(--color-ash);
}

.review__total {
  font-weight: 600;
}

.review__total .review__value {
  color: var(--color-chalk);
}

/* What happened, as the email page reads it: each step at the left, its day at the right. */
.review__steps {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.review__steps li {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 0.1rem 0.8rem;
}

.review__when {
  white-space: nowrap;
}

.review__reason {
  grid-column: 1 / -1;
  font-style: italic;
}

.review__ask {
  margin-bottom: 1rem;
}

@media (--phone) {
  .review__pair > :first-child {
    width: auto;
  }
}
</style>
