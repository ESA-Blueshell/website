<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import {
  approveDeclaration,
  BookingKind,
  day,
  type Declaration,
  DeclarationState,
  euros,
  markDeclarationPaid,
  type BankAccountToPay,
  readDeclaration,
  readBankAccountToPay,
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
import {revealMandateIban} from "@/domains/user"
import type {Refused} from "@/types/api"
import type {Saved} from "@/utils/refusals"

/** One declaration as the board checks it: what to transfer, the expenses, the receipts and what happened so far. */
defineOptions({name: "DeclarationReviewPage"})

const route = useRoute()
const declaration = ref<Declaration | null>(null)
const loaded = ref(false)

// The account on file now, read only once the declaration waits to be paid.
const account = ref<BankAccountToPay | null>(null)
const accountUnread = ref(false)
const loadAccount = async (id: number) => {
  const read = await readBankAccountToPay(id)
  account.value = read ?? null
  accountUnread.value = read === undefined
}

// Both come in before either shows, or "no bank account yet" flashes while the account loads.
onMounted(async () => {
  const found = await readDeclaration(Number(route.params.id))
  if (found?.state === DeclarationState.APPROVED) await loadAccount(found.id)
  declaration.value = found
  loaded.value = true
})

const total = computed(() => (declaration.value ? totalOf(declaration.value.expenses) : 0))
const submitted = computed(() => (declaration.value ? stepAt(declaration.value, DeclarationState.SUBMITTED) : null))
const actable = computed(() => !!declaration.value && withTheBoard(declaration.value))
// TWIN: `markPaid` in the api's `DeclarationReviews.kt`.
const payable = computed(() => declaration.value?.state === DeclarationState.APPROVED)

/* The full IBAN opens only through a reveal, which the api writes to the submitter's security log. */
const revealed = ref<string | null>(null)
const revealing = ref(false)
const revealFailure = ref<string | null>(null)
const reveal = async () => {
  revealing.value = true
  revealFailure.value = null
  const answered = await revealMandateIban(declaration.value!.submitterId)
  revealing.value = false
  if (answered.ok) revealed.value = answered.saved
  else revealFailure.value = answered.reason
}

// A SEPA transfer's description holds 140 characters.
const DESCRIPTION_MAX = 140
const reference = computed(() => (declaration.value ? `Declaration ${declaration.value.id} ${titleOf(declaration.value)}`.slice(0, DESCRIPTION_MAX) : ""))

/* What the board types into the bank's own transfer form, each copied as the bank wants it. */
const transfer = computed(() => {
  if (!account.value) return []
  return [
    {key: "holder", label: "Name", shown: account.value.holder ?? "Name unavailable", copied: account.value.holder},
    {key: "iban", label: "IBAN", shown: revealed.value?.replaceAll(/(.{4})(?=.)/g, "$1 ") ?? account.value.preview, copied: revealed.value},
    ...(account.value.bic ? [{key: "bic", label: "BIC", shown: account.value.bic, copied: account.value.bic}] : []),
    {key: "amount", label: "Amount", shown: euros(total.value), copied: total.value.toFixed(2)},
    {key: "reference", label: "Description", shown: reference.value, copied: reference.value},
  ]
})

const copiedKey = ref<string | null>(null)
const copy = async (key: string, text: string) => {
  await navigator.clipboard.writeText(text)
  copiedKey.value = key
  setTimeout(() => {
    if (copiedKey.value === key) copiedKey.value = null
  }, 1500)
}

const confirmingPaid = ref(false)

const working = ref(false)
const failure = ref<string | null>(null)

const move = async (answer: () => Promise<Saved<Declaration> | Refused>) => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    const moved = await answer()
    if (moved.ok) {
      if (moved.saved.state === DeclarationState.APPROVED) await loadAccount(moved.saved.id)
      declaration.value = moved.saved
    } else failure.value = moved.reason
  } finally {
    working.value = false
    asking.value = null
    confirmingPaid.value = false
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
        v-if="payable"
        :disabled="working || !account"
        testid="declaration-mark-paid"
        tone="solid"
        @click="confirmingPaid = true"
      >
        Mark as paid
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
        <section
          v-if="payable"
          data-testid="declaration-review-pay-to"
        >
          <p class="review__title">
            Pay to
          </p>
          <notice-box
            v-if="accountUnread"
            testid="declaration-review-account-unread"
            tone="danger"
          >
            We cannot load this bank account right now. Try again in a moment.
          </notice-box>
          <p
            v-else-if="!account"
            class="review__note"
            data-testid="declaration-review-no-bank"
          >
            {{ declaration.submitter }} has not added a bank account yet. Ask them to add one on their Payment details page.
          </p>
          <ul
            v-else
            class="review__rows"
          >
            <li
              v-for="line in transfer"
              :key="line.key"
              class="review__pair"
            >
              <span>{{ line.label }}</span>
              <span
                class="review__value"
                :data-testid="`declaration-review-${line.key}`"
              >{{ line.shown }}</span>
              <mini-button
                v-if="line.key === 'iban'"
                :disabled="revealing"
                testid="declaration-reveal"
                @click="revealed ? (revealed = null) : reveal()"
              >
                {{ revealed ? "Hide the IBAN" : "Reveal the IBAN" }}
              </mini-button>
              <mini-button
                v-if="line.copied"
                :testid="`declaration-copy-${line.key}`"
                @click="copy(line.key, line.copied)"
              >
                {{ copiedKey === line.key ? "Copied" : "Copy" }}
              </mini-button>
            </li>
          </ul>
          <p
            v-if="revealFailure"
            class="review__failure"
            data-testid="declaration-reveal-failure"
            role="alert"
          >
            {{ revealFailure }}
          </p>
        </section>

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
              <span>{{ stepLabel(step, index) }}<template v-if="step.by"> by {{ step.by }}</template><template v-if="step.paidTo">, to {{ step.paidTo }}</template></span>
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

    <confirm-dialog
      v-model:open="confirmingPaid"
      confirm-label="Mark as paid"
      :question="`You sent ${euros(total)} to ${account?.preview}. They get an email saying it is paid.`"
      testid="declaration-paid-dialog"
      title="Mark as paid?"
      :working="working"
      @confirm="move(() => markDeclarationPaid(declaration!.id))"
    />
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

/* The day moves under the step when the aside is too narrow for both. */
.review__steps li {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 0.1rem 0.8rem;
}

.review__when {
  white-space: nowrap;
}

.review__reason {
  flex-basis: 100%;
  font-style: italic;
}

.review__failure {
  margin: 0.5rem 0 0;
  color: var(--color-danger);
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
