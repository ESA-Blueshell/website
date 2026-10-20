<template>
  <account-frame
    :crumb="{label: 'Payment details', to: '/account/payment-details'}"
    eyebrow="Payment details"
    heading="Payment method"
    island-content
  >
    <task-layout
      aside-title="How direct debit works"
      data-testid="payment-method"
    >
      <notice-box
        v-if="loaded && !own"
        testid="payment-method-unread"
        tone="danger"
      >
        We cannot load your payment method right now. Try again in a moment.
      </notice-box>
      <template v-else-if="loaded">
        <section class="island-panel">
          <h3 class="island-panel__title">
            How do you want to pay?
          </h3>
          <radio-group
            v-model="choice"
            :options="CHOICES"
            testid="payment-method-choice"
          />
        </section>

        <template v-if="choice === 'debit' && collecting">
          <fact-list
            :columns="2"
            :facts="facts"
          />
          <div class="method__rows">
            <cut-row
              meta="Adding a new bank account stops direct debit until you set it up again."
              testid="payment-method-bank-row"
              title="Bank account"
              to="/account/payment-details/bank-account"
            >
              <template #glyph>
                <bank-glyph />
              </template>
            </cut-row>
          </div>
        </template>

        <template v-else-if="choice === 'debit'">
          <notice-box
            v-if="!account"
            testid="payment-method-no-bank"
            title="Add a bank account first"
            tone="warning"
          >
            <p>Direct debit takes the money from the bank account shown under Payment details.</p>
            <cut-button
              class="method__way"
              href="/account/payment-details/bank-account"
              small
              testid="payment-method-add-bank"
            >
              Add bank account
            </cut-button>
          </notice-box>

          <form
            v-else
            class="island-panel"
            data-testid="payment-method-form"
            @submit.prevent="setUp"
          >
            <h3 class="island-panel__title">
              Set up direct debit
            </h3>
            <ul class="method__pairs">
              <li>
                <span>Taken from</span>
                <span data-testid="payment-method-account">{{ account.preview }}</span>
              </li>
              <li>
                <span>Collected by</span>
                <span>ESA Blueshell, Enschede</span>
              </li>
              <li>
                <span>How often</span>
                <span>Once a year</span>
              </li>
            </ul>
            <check-box
              v-model="agreed"
              :error-messages="checks.errorsOf('agreed')"
              :label="MANDATE_WORDING.text"
              testid="payment-method-agree"
            />
            <notice-box
              v-if="failure"
              testid="payment-method-failure"
              tone="danger"
            >
              {{ failure }}
            </notice-box>
            <div class="form-save">
              <cut-button
                :disabled="working"
                submit
                testid="payment-method-set-up"
                tone="solid"
              >
                {{ working ? "Saving" : "Set up direct debit" }}
              </cut-button>
            </div>
          </form>
        </template>

        <section
          v-else-if="standing"
          class="method__danger"
          data-testid="payment-method-stop-panel"
        >
          <div>
            <p class="method__danger-title">
              Stop direct debit
            </p>
            <p class="method__note">
              After this, you pay by bank transfer or in cash.
            </p>
          </div>
          <cut-button
            testid="payment-method-stop"
            tone="danger"
            @click="stopping = true"
          >
            Stop direct debit
          </cut-button>
        </section>

        <div
          v-else
          class="method__manual"
          data-testid="payment-method-manual"
        >
          <p class="method__note">
            You pay each contribution yourself, by bank transfer or in cash.
          </p>
          <cut-button
            href="/account/payment-details#how-to-pay"
            small
            testid="payment-method-how-to-pay"
          >
            See how to pay
          </cut-button>
        </div>
      </template>

      <template #aside>
        <p>We email you the amount and the date before we collect your contribution.</p>
        <p>You can stop direct debit here at any time. Your bank can refund a payment up to 8 weeks after it was taken.</p>
        <p>Direct debit may not cover what you already owe. Pay that amount by bank transfer or in cash.</p>
      </template>
    </task-layout>

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="twoFactorOn"
      @proved="afterStepUp === 'stop' ? stop() : setUp()"
    />
    <confirm-dialog
      v-model:open="stopping"
      confirm-label="Stop direct debit"
      question="We will stop taking your contribution from your bank account. From then on, you pay by bank transfer or in cash."
      testid="payment-method-stop-dialog"
      title="Stop direct debit?"
      :working="working"
      @confirm="stop"
    />
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {type FieldChecks, useFormChecks} from "@/composables/useFormChecks"
import {StepUpDialog, readTwoFactor} from "@/domains/auth"
import {type BankAccountPreview, BankGlyph, readOwnBankAccount} from "@/domains/banking"
import {
  IncassoStanding,
  MANDATE_WORDING,
  type OwnMandateResponse,
  readOwnMandate,
  setUpOwnDirectDebit,
  stopOwnDirectDebit,
} from "@/domains/user"
import {accepted} from "@/utils/checks"
import {formatDay} from "@/utils/timestamps"

defineOptions({name: "PaymentMethodPage"})

const store = useStore()
const account = ref<BankAccountPreview | null>(null)
const own = ref<OwnMandateResponse | null>(null)
const loaded = ref(false)
const agreed = ref(false)
const working = ref(false)
const stopping = ref(false)
const failure = ref<string | null>(null)
const stepUpOpen = ref(false)
const twoFactorOn = ref(false)
// What a step-up was asked for, so proving it carries on with that.
const afterStepUp = ref<"set-up" | "stop">("set-up")

/* What the reader picks; only setting up or confirming the stop changes what is on file. */
type Choice = "manual" | "debit"
const choice = ref<Choice | null>("manual")
const CHOICES: {key: Choice; label: string; hint: string}[] = [
  {key: "manual", label: "Bank transfer or cash", hint: "You pay each contribution yourself."},
  {key: "debit", label: "Direct debit", hint: "We take your contribution from your bank account once a year, automatically."},
]

// A paper mandate may stand before its account is on file: it can be stopped, but there is nothing to collect from yet.
const standing = computed(() => !!own.value && own.value.standing !== IncassoStanding.NONE)
const collecting = computed(() => own.value?.standing === IncassoStanding.MANDATE_RECORDED)

onMounted(async () => {
  const [held, signed, twoFactor] = await Promise.all([readOwnBankAccount(), readOwnMandate(), readTwoFactor()])
  account.value = held
  own.value = signed
  twoFactorOn.value = twoFactor?.on === true
  choice.value = standing.value ? "debit" : "manual"
  loaded.value = true
})

const checks = useFormChecks((): Record<string, FieldChecks> => ({
  // TWIN: `SignOwnMandateRequest` in the api refuses the same.
  agreed: {value: () => agreed.value, checks: [accepted("Tick the box to allow direct debit.")]},
}))

const facts = computed<Fact[]>(() => [
  {label: "How you pay", value: "Direct debit", sub: `Since ${formatDay(own.value?.signedOn)}`, testid: "payment-method-on"},
  {label: "Account", value: `${own.value?.ibanCountry} •• … ••${own.value?.ibanLastTwo}`, sub: `Direct debit reference ${own.value?.reference}`},
])

const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)

const setUp = async () => {
  if (!checks.attempt() || working.value) return
  working.value = true
  failure.value = null
  try {
    const answered = await setUpOwnDirectDebit()
    if (answered.ok) {
      own.value = answered.saved
      checks.settle()
      tell("Direct debit is set up.")
    } else if (answered.needsStepUp) {
      afterStepUp.value = "set-up"
      stepUpOpen.value = true
    } else {
      failure.value = answered.reason
    }
  } finally {
    working.value = false
  }
}

const stop = async () => {
  working.value = true
  try {
    const answered = await stopOwnDirectDebit()
    if (answered.ok) {
      own.value = answered.saved
      agreed.value = false
      choice.value = "manual"
      tell("Direct debit has stopped.")
    } else if (answered.needsStepUp) {
      afterStepUp.value = "stop"
      stepUpOpen.value = true
    } else {
      tell(answered.reason)
    }
  } finally {
    working.value = false
    stopping.value = false
  }
}
</script>

<style scoped>
.method__manual {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 0.8rem 1.5rem;
}

.method__way {
  margin-top: 0.7rem;
}

/* The account and who collects, read down a column as the Management pages list facts. */
.method__pairs {
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 0;
  margin: 0;
  list-style: none;
}

.method__pairs li {
  display: flex;
  gap: 0.5rem;
  padding: 0.5rem 0.8rem;
  font-size: 0.88rem;
  background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.method__pairs li > :last-child {
  flex: 1;
  text-align: right;
  color: var(--color-ash);
}

.method__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.method__danger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem 2rem;
  margin-top: 1.5rem;
  padding: 1.1rem 1.3rem;
  background-color: color-mix(in oklab, var(--color-danger) 8%, transparent);
}

.method__danger-title {
  font-family: var(--font-display);
  font-size: 1rem;
  text-transform: uppercase;
}

.method__note {
  margin-top: 0.25rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}
</style>
