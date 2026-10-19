<template>
  <account-frame
    :crumb="{label: 'Payment details', to: '/account/payment-details'}"
    eyebrow="Payment details"
    heading="Bank account"
    island-content
  >
    <task-layout
      aside-title="How we store it"
      data-testid="bank-account"
    >
      <template v-if="loaded">
        <template v-if="preview && !replacing">
          <fact-list
            :columns="2"
            :facts="facts"
          />

          <h2 class="bank__head">
            Change your bank account
          </h2>
          <div class="bank__rows">
            <cut-row
              meta="Enter a different bank account. The old one is deleted, and incasso stops until you set it up again."
              testid="bank-account-replace-row"
              title="Use a different bank account"
            >
              <template #glyph>
                <bank-glyph />
              </template>
              <template #end>
                <cut-button
                  testid="bank-account-replace"
                  @click="replacing = true"
                >
                  Replace bank account
                </cut-button>
              </template>
            </cut-row>
          </div>

          <div class="bank__danger">
            <div>
              <p class="bank__danger-title">
                Delete bank account
              </p>
              <p class="bank__note">
                Until you add a bank account, your declarations cannot be paid and incasso stops.
              </p>
            </div>
            <cut-button
              testid="bank-account-delete"
              tone="danger"
              @click="deleting = true"
            >
              Delete
            </cut-button>
          </div>
        </template>

        <template v-else>
          <p
            v-if="replacing"
            class="bank__lede"
          >
            Enter the new bank account. It replaces your current one.
          </p>
          <bank-account-form
            :cancellable="replacing"
            @cancel="replacing = false"
            @saved="saved"
          />
        </template>
      </template>

      <template #aside>
        <p>The full number is encrypted. After you save it, the site shows only the country and the last two digits.</p>
        <p>Board members use your bank account only to pay back a declaration or to collect your contributions by direct debit (incasso).</p>
      </template>
    </task-layout>

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="twoFactorOn"
      @proved="remove"
    />
    <confirm-dialog
      v-model:open="deleting"
      confirm-label="Delete"
      question="Until you add a bank account, your declarations cannot be paid and incasso stops."
      testid="bank-account-delete-dialog"
      title="Delete your bank account?"
      :working="working"
      @confirm="remove"
    />
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {
  BankAccountForm,
  type BankAccountPreview,
  BankGlyph,
  readOwnBankAccount,
  removeOwnBankAccount,
} from "@/domains/banking"
import {StepUpDialog, readTwoFactor} from "@/domains/auth"
import {formatDay} from "@/utils/timestamps"

defineOptions({name: "BankAccountPage"})

const store = useStore()
const preview = ref<BankAccountPreview | null>(null)
const loaded = ref(false)
const replacing = ref(false)
const deleting = ref(false)
const working = ref(false)
const stepUpOpen = ref(false)
const twoFactorOn = ref(false)

onMounted(async () => {
  const [held, standing] = await Promise.all([readOwnBankAccount(), readTwoFactor()])
  preview.value = held
  twoFactorOn.value = standing?.on === true
  loaded.value = true
})

const facts = computed<Fact[]>(() => preview.value
  ? [
    {label: "Account", value: preview.value.preview, testid: "bank-account-preview"},
    {label: "Added", value: formatDay(preview.value.addedAt)},
  ]
  : [])

const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)

const saved = (held: BankAccountPreview) => {
  preview.value = held
  replacing.value = false
  tell("Your bank account is saved.")
}

const remove = async () => {
  working.value = true
  try {
    const answered = await removeOwnBankAccount()
    if (answered.ok) {
      preview.value = null
      tell("Your bank account is deleted.")
    } else if (answered.needsStepUp) {
      stepUpOpen.value = true
    } else {
      tell(answered.reason)
    }
  } finally {
    working.value = false
    deleting.value = false
  }
}
</script>

<style scoped>
.bank__lede {
  color: var(--color-ash);
}

.bank__head {
  margin-top: 1.4rem;
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.bank__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.bank__danger {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem 2rem;
  margin-top: 1.5rem;
  padding: 1.1rem 1.3rem;
  background-color: color-mix(in oklab, var(--color-danger) 8%, transparent);
}

.bank__danger-title {
  font-family: var(--font-display);
  font-size: 1rem;
  text-transform: uppercase;
}

.bank__note {
  margin-top: 0.25rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}
</style>
