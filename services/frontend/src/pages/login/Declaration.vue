<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import StateTag from "@/components/island/StateTag.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {type BankAccountPreview, readOwnBankAccount} from "@/domains/banking"
import {
  day,
  type Declaration,
  type DeclarationDraft,
  DeclarationGlyph,
  DeclarationState,
  DeclarationSheet,
  deleteDraft,
  euros,
  type Expense,
  ExpenseEditor,
  readOwnDeclaration,
  receiptsOf,
  ReceiptPicker,
  ReceiptStrip,
  saveDraft,
  sentBack,
  stateOf,
  stepAt,
  submitDeclaration,
  titleOf,
  totalOf,
  useBookingChoices,
  withdrawDeclaration,
} from "@/domains/declarations"
import {readUser} from "@/domains/user"
import {type FieldChecks, useFormChecks} from "@/composables/useFormChecks"
import {accepted, required} from "@/utils/checks"

defineOptions({name: "DeclarationPage"})

const route = useRoute()
const router = useRouter()
const store = useStore()

const name = ref("")
const declaration = ref<Declaration | null>(null)
const missing = ref(false)
const loaded = ref(false)

const blankExpense = (): Expense => ({
  id: crypto.randomUUID(),
  paidOn: "",
  description: "",
  amount: "",
  for: null,
  receipts: [],
})

const draft = ref<DeclarationDraft>({expenses: [blankExpense()], receipts: [], confirmed: false})
const account = ref<BankAccountPreview | null>(null)

const {committees, events} = useBookingChoices()

const editing = computed(() => declaration.value == null || declaration.value.state === DeclarationState.DRAFT)
const returned = computed(() => (declaration.value ? sentBack(declaration.value) : null))

const hold = (found: Declaration) => {
  declaration.value = found
  draft.value = {expenses: found.expenses, receipts: found.receipts, confirmed: found.confirmed}
}

onMounted(async () => {
  const id = route.params.id === "new" ? null : Number(route.params.id)
  const userId = store.getters.getLogin?.userId as number
  const [user, held, found] = await Promise.all([
    readUser(userId).catch(() => null),
    readOwnBankAccount(),
    id == null ? Promise.resolve(null) : readOwnDeclaration(id),
  ])
  name.value = user?.fullName ?? store.getters.getLogin?.username ?? ""
  account.value = held
  if (id != null && !found) missing.value = true
  if (found) hold(found)
  loaded.value = true
})

/* What the form preview reads: the draft as it stands, in the declaration it belongs to. */
const sheet = computed(() => ({
  submitter: declaration.value?.submitter ?? name.value,
  state: declaration.value?.state ?? DeclarationState.DRAFT,
  steps: declaration.value?.steps ?? [],
  ...draft.value,
}))

const total = computed(() => totalOf(draft.value.expenses))
/* Each field says what it is missing, as the event form's do; the bank account has its own notice.
   TWIN: `Declarations.requireComplete` refuses the same submit on the api. */
const above = (value: unknown) => (Number.parseFloat(value as string) > 0 ? null : "Enter an amount above zero.")
const NO_RECEIPT = "Add at least one receipt, for an expense or for the whole declaration."
const someReceipt = () => (receiptsOf(draft.value).length > 0 ? null : NO_RECEIPT)
const checks = useFormChecks((): Record<string, FieldChecks> => ({
  ...Object.fromEntries(draft.value.expenses.flatMap((_, index) => [
    [`${index}.paidOn`, {value: () => draft.value.expenses[index]?.paidOn, checks: [required]}],
    [`${index}.amount`, {value: () => draft.value.expenses[index]?.amount, checks: [above]}],
    [`${index}.description`, {value: () => draft.value.expenses[index]?.description.trim(), checks: [required]}],
  ])),
  receipts: {value: () => receiptsOf(draft.value).length, checks: [someReceipt]},
  confirmed: {value: () => draft.value.confirmed, checks: [accepted("Tick the box to confirm.")]},
}))
const receiptsWrong = computed(() => checks.errorsOf("receipts"))
/* The first expense says a receipt is missing; every receipt input turns red with it. */
const errorsFor = (index: number) => ({
  paidOn: checks.errorsOf(`${index}.paidOn`),
  amount: checks.errorsOf(`${index}.amount`),
  description: checks.errorsOf(`${index}.description`),
  receipts: index === 0 ? receiptsWrong.value : receiptsWrong.value.length ? [""] : [],
})
const tried = ref(false)
const saving = ref(false)
const failure = ref<string | null>(null)

const addExpense = () => {
  draft.value.expenses = [...draft.value.expenses, blankExpense()]
}

const removeExpense = (id: string) => {
  draft.value.expenses = draft.value.expenses.filter(one => one.id !== id)
}

const save = async (): Promise<Declaration | null> => {
  const saved = await saveDraft(declaration.value?.id ?? null, draft.value)
  if (!saved.ok) {
    failure.value = saved.reason
    return null
  }
  declaration.value = saved.saved
  if (route.params.id === "new") void router.replace(`/account/declarations/${saved.saved.id}`)
  return saved.saved
}

// Every button that starts one is disabled while another runs.
const working = async (task: () => Promise<void>) => {
  saving.value = true
  failure.value = null
  try {
    await task()
  } finally {
    saving.value = false
  }
}

const saveOnly = () => working(async () => {
  if (await save()) store.commit("setStatusSnackbarMessage", "Your draft is saved.")
})

const submit = async () => {
  tried.value = true
  if (!checks.attempt() || !account.value) return
  await working(async () => {
    const saved = await save()
    if (!saved) return
    const sent = await submitDeclaration(saved.id)
    if (!sent.ok) {
      failure.value = sent.reason
      return
    }
    hold(sent.saved)
    window.scrollTo({top: 0})
  })
}

const confirmingWithdraw = ref(false)
const withdraw = () => working(async () => {
  confirmingWithdraw.value = false
  const back = await withdrawDeclaration(declaration.value!.id)
  if (!back.ok) {
    failure.value = back.reason
    return
  }
  hold(back.saved)
  store.commit("setStatusSnackbarMessage", "Your declaration is a draft again.")
})

const confirmingDelete = ref(false)
const remove = async () => {
  confirmingDelete.value = false
  const gone = await deleteDraft(declaration.value!.id)
  if (gone.ok) void router.push("/account/declarations")
  else failure.value = gone.reason
}

const paidOn = computed(() => (declaration.value ? stepAt(declaration.value, DeclarationState.PAID)?.at : null) ?? null)
const submittedOn = computed(() => (declaration.value ? stepAt(declaration.value, DeclarationState.SUBMITTED)?.at : null) ?? null)

const facts = computed<Fact[]>(() => {
  // Drawn only once a declaration is on screen.
  const held = declaration.value!
  return [
    {label: "Total", value: euros(totalOf(held.expenses)), testid: "declaration-total"},
    {label: "Submitted", value: submittedOn.value ? day(submittedOn.value) : "Not yet"},
    {label: "Paid", value: paidOn.value ? day(paidOn.value) : "Not yet", testid: "declaration-paid"},
  ]
})

const heading = computed(() => {
  if (missing.value) return "Declaration"
  if (!editing.value && declaration.value) return titleOf(declaration.value)
  return declaration.value ? "Edit declaration" : "New declaration"
})

const previewing = ref(false)

const expenseMeta = (expense: Expense) => [
  expense.paidOn ? `Paid ${day(expense.paidOn)}` : null,
  expense.for?.name ?? null,
  expense.receipts.length === 1 ? "1 receipt" : `${expense.receipts.length} receipts`,
].filter(Boolean).join(" · ")

const NEXT: Record<string, string> = {
  SUBMITTED: "The board is checking it. We'll email you when the board sends it back or sends the money.",
  APPROVED: "The board has approved it and will send you the money soon.",
  PAID: "The board has sent the money.",
  REJECTED: "The board has rejected it. Email the board if you think this is a mistake.",
}
</script>

<template>
  <account-frame
    :crumb="{label: 'Declarations', to: '/account/declarations'}"
    eyebrow="Declarations"
    :heading="heading"
    island-content
  >
    <template
      v-if="loaded && !editing && declaration"
      #actions
    >
      <state-tag
        :tone="stateOf(declaration).tone"
        testid="declaration-state"
      >
        {{ stateOf(declaration).label }}
      </state-tag>
    </template>

    <template v-if="loaded">
      <p
        v-if="missing"
        class="declaration__none"
      >
        This declaration does not exist, or it is not yours.
      </p>

      <task-layout
        v-else-if="editing"
        aside-title="How it works"
        data-testid="declaration-edit"
        wide
      >
        <notice-box
          v-if="returned"
          testid="declaration-sent-back"
          title="The board has sent this back"
          tone="warning"
        >
          {{ returned.reason }} Make the changes the board asked for. Then submit it again.
        </notice-box>

        <expense-editor
          v-for="(expense, index) in draft.expenses"
          :key="expense.id"
          v-model="draft.expenses[index]!"
          :committees="committees"
          :events="events"
          :number="index + 1"
          :removable="draft.expenses.length > 1"
          :errors="errorsFor(index)"
          :testid="`declaration-expense-${index}`"
          @remove="removeExpense(expense.id)"
          @touch="checks.touch(`${index}.${$event}`)"
        />
        <button
          class="declaration__add"
          data-testid="declaration-add-expense"
          type="button"
          @click="addExpense"
        >
          <declaration-glyph
            class="declaration__add-glyph"
            name="plus"
          />
          Add another expense
        </button>

        <section
          class="island-panel declaration__whole"
          data-testid="declaration-whole"
        >
          <h3 class="island-panel__title">
            Receipts for the whole declaration
          </h3>
          <p class="declaration__whole-lead">
            Add a receipt here if it covers more than one expense.
          </p>
          <receipt-picker
            v-model="draft.receipts"
            :invalid="receiptsWrong.length > 0"
            testid="declaration-whole"
          />
        </section>

        <notice-box
          v-if="!account"
          testid="declaration-no-bank"
          title="You have not added a bank account yet"
          :tone="tried ? 'danger' : 'warning'"
        >
          <p>The board needs your bank account to pay you back. Add it before you submit.</p>
          <cut-button
            class="declaration__bank-way"
            href="/account/payment-details/bank-account"
            small
            testid="declaration-add-bank"
          >
            Add a bank account
          </cut-button>
        </notice-box>

        <div class="island-panel">
          <check-box
            v-model="draft.confirmed"
            :error-messages="checks.errorsOf('confirmed')"
            label="I paid these expenses for Blueshell. The receipts are real."
            testid="declaration-confirm"
          />
        </div>

        <notice-box
          v-if="failure"
          testid="declaration-failure"
          tone="danger"
        >
          {{ failure }}
        </notice-box>

        <div class="form-save declaration__savebar">
          <span
            class="form-save__words"
            data-testid="declaration-edit-total"
          >
            <span>Total</span>
            <strong class="declaration__total">{{ euros(total) }}</strong>
          </span>
          <cut-button
            v-if="declaration"
            testid="declaration-delete"
            tone="quiet"
            @click="confirmingDelete = true"
          >
            Delete draft
          </cut-button>
          <cut-button
            testid="declaration-preview-open"
            tone="quiet"
            @click="previewing = true"
          >
            Preview
          </cut-button>
          <cut-button
            :disabled="saving"
            testid="declaration-save"
            tone="quiet"
            @click="saveOnly"
          >
            Save draft
          </cut-button>
          <cut-button
            :disabled="saving"
            testid="declaration-submit"
            tone="solid"
            @click="submit"
          >
            {{ saving ? "Saving" : "Submit" }}
          </cut-button>
        </div>

        <template #aside>
          <p>Add all the expenses you want paid back. For each expense, enter the date you paid and the amount. Attach a photo or PDF of each receipt.</p>
          <p>Choose the committee or event each expense was for, so the board knows where it belongs.</p>
          <p>The board checks it and sends the money to your bank account. We'll email you when the board sends the money or sends it back.</p>
          <router-link
            class="form-link"
            data-testid="declaration-bank-link"
            to="/account/payment-details/bank-account"
          >
            Your bank account
          </router-link>
        </template>
      </task-layout>

      <template v-else-if="declaration">
        <fact-list
          class="declaration__facts"
          :facts="facts"
        />
        <p
          class="declaration__next"
          data-testid="declaration-next"
        >
          {{ NEXT[declaration.state] }}
        </p>

        <h2 class="declaration__head">
          Expenses<count-badge
            :count="declaration.expenses.length"
            said="expenses"
          />
        </h2>
        <div class="declaration__rows">
          <cut-row
            v-for="(expense, index) in declaration.expenses"
            :key="expense.id"
            :meta="expenseMeta(expense)"
            :testid="`declaration-expense-row-${index}`"
            :title="expense.description"
          >
            <template #glyph>
              <declaration-glyph name="receipt" />
            </template>
            <template #end>
              <receipt-strip
                :receipts="expense.receipts"
                :testid="`declaration-receipts-${index}`"
              />
              <span class="declaration__amount">{{ euros(Number.parseFloat(expense.amount) || 0) }}</span>
            </template>
          </cut-row>
          <cut-row
            v-if="declaration.receipts.length"
            :meta="declaration.receipts.length === 1 ? '1 receipt' : `${declaration.receipts.length} receipts`"
            testid="declaration-whole-row"
            title="Receipts for the whole declaration"
          >
            <template #glyph>
              <declaration-glyph name="receipt" />
            </template>
            <template #end>
              <receipt-strip
                :receipts="declaration.receipts"
                testid="declaration-receipts-whole"
              />
            </template>
          </cut-row>
        </div>

        <notice-box
          v-if="failure"
          class="declaration__failure"
          testid="declaration-failure"
          tone="danger"
        >
          {{ failure }}
        </notice-box>

        <div class="declaration__ways">
          <button
            class="form-link"
            data-testid="declaration-preview-open"
            type="button"
            @click="previewing = true"
          >
            View declaration form
          </button>
          <cut-button
            v-if="declaration.state === DeclarationState.SUBMITTED"
            :disabled="saving"
            small
            testid="declaration-withdraw"
            tone="quiet"
            @click="confirmingWithdraw = true"
          >
            Withdraw
          </cut-button>
        </div>
      </template>
    </template>

    <modal-dialog
      v-model:open="previewing"
      :cancel="false"
      testid="declaration-preview"
      title="Declaration form"
      wide
    >
      <declaration-sheet
        :pay-to="account ? {holder: null, iban: account.preview, bic: null} : null"
        :declaration="sheet"
      />
    </modal-dialog>

    <confirm-dialog
      v-model:open="confirmingDelete"
      confirm-label="Delete draft"
      question="This deletes the draft and its receipts."
      testid="declaration-delete-dialog"
      title="Delete this draft?"
      @confirm="remove"
    />

    <confirm-dialog
      v-model:open="confirmingWithdraw"
      confirm-label="Withdraw"
      question="It goes back to a draft, so you can change it. Tick the box to confirm again before you submit."
      testid="declaration-withdraw-dialog"
      title="Withdraw this declaration?"
      working-label="Withdrawing"
      @confirm="withdraw"
    />
  </account-frame>
</template>

<style scoped>
.declaration__none {
  margin-top: 1.5rem;
  color: var(--color-ash);
}

.declaration__add {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  padding: 1rem;
  border: 1px dashed color-mix(in oklab, var(--color-chalk) 28%, transparent);
  background: none;
  color: var(--color-ash);
  font-family: var(--font-display);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
  cursor: pointer;
  transition: color 160ms ease, border-color 160ms ease;
}

.declaration__add:hover,
.declaration__add:focus-visible {
  border-color: var(--color-brand-ink);
  color: var(--color-chalk);
}

.declaration__add-glyph {
  width: 18px;
  height: 18px;
}

/* The edit pages' save bar: it rides along at the foot of the window while the form scrolls. */
.declaration__savebar {
  position: sticky;
  bottom: 1rem;
  z-index: 3;
  margin-top: 0.5rem;
  padding: 1rem 1.25rem;
  background-color: var(--color-surface);
  border-top: 3px solid var(--color-brand);
  box-shadow: 0 14px 34px rgb(0 0 0 / 30%);
}

.declaration__total {
  font-family: var(--font-display);
  font-size: 1.5rem;
  line-height: 1;
  color: var(--color-chalk);
}

.declaration__facts {
  padding: 1.6rem 0 1rem;
}

.declaration__next {
  max-width: 60ch;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.declaration__head {
  margin: 2.5rem 0 0.8rem;
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.declaration__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.declaration__amount {
  font-family: var(--font-display);
  font-size: 1.05rem;
  white-space: nowrap;
}

.declaration__ways {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  margin-top: 1.5rem;
}

.declaration__failure {
  margin-top: 1.5rem;
}

.declaration__whole-lead {
  margin-bottom: 0.6rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.declaration__bank-way {
  margin-top: 0.7rem;
}
</style>
