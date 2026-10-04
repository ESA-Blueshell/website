<template>
  <section
    class="account-security"
    data-testid="account-security-panel"
  >
    <template v-if="standing">
      <p class="account-security__tags">
        <state-tag
          testid="account-security-two-factor-chip"
          :tone="standing.twoFactorOn ? 'ok' : 'quiet'"
        >
          Two-factor {{ standing.twoFactorOn ? "on" : "off" }}
        </state-tag>
        <state-tag
          v-if="standing.awaitingReenrolment"
          testid="account-security-awaiting-chip"
          tone="warn"
        >
          Waiting to set up two-factor again
        </state-tag>
        <state-tag
          v-if="standing.locked"
          testid="account-security-locked-chip"
          tone="warn"
        >
          Locked
        </state-tag>
      </p>

      <template v-if="standing.locked || (standing.twoFactorOn && !isSelf)">
        <p class="account-security__note">
          Make sure the request really comes from this person before you unlock the account or reset two-factor. Ask them
          in person, by phone or on Discord, not by an email to this account.
        </p>
        <div class="account-security__fields">
          <form-field
            v-slot="field"
            :filled="reason !== ''"
            hint="Kept in the security log."
            label="Reason"
            testid="account-security-reason-field"
            variant="inside"
          >
            <text-input
              v-model="reason"
              :control-id="field.controlId"
            />
          </form-field>
          <form-field
            v-if="standing.locked"
            v-slot="field"
            :filled="correctedEmail !== ''"
            hint="Optional. Fill it in if the address on the account is wrong."
            label="New email address"
            testid="account-security-email-field"
            variant="inside"
          >
            <text-input
              v-model="correctedEmail"
              :control-id="field.controlId"
              type="email"
            />
          </form-field>
        </div>
      </template>

      <div class="account-security__acts">
        <cut-button
          v-if="standing.locked"
          :disabled="!reason.trim()"
          testid="account-security-unlock-btn"
          tone="solid"
          @click="act(() => unlockAccount(userId, reason.trim(), correctedEmail.trim()), 'Unlocked. A password reset is on its way.')"
        >
          Unlock
        </cut-button>
        <cut-button
          v-if="standing.twoFactorOn && !isSelf"
          :disabled="!reason.trim()"
          testid="account-security-reset-btn"
          tone="danger"
          @click="act(() => resetTwoFactorOf(userId, reason.trim()), 'Two-factor reset. A re-enrolment link is on its way.')"
        >
          Reset two-factor
        </cut-button>
        <cut-button
          v-if="standing.awaitingReenrolment && !isSelf"
          testid="account-security-resend-btn"
          @click="showPreview(() => previewReenrolment(userId))"
        >
          Resend the set-up link
        </cut-button>
      </div>

      <list-head title="Security log" />
      <p
        v-if="log.length === 0"
        class="account-security__note"
      >
        Nothing has happened on this account yet.
      </p>
      <pair-list
        v-else
        :pairs="log"
      />
    </template>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      confirm-label="Resend the set-up link"
      title="Two-factor set-up email"
      @confirm="resend"
    />
    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="true"
      @proved="stepUpProved"
    />
  </section>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import StepUpDialog from "./StepUpDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import StateTag from "@/components/island/StateTag.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import PairList from "@/components/management/PairList.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {
  previewReenrolment,
  readAccountStanding,
  readSecurityLogOf,
  resendReenrolment,
  resetTwoFactorOf,
  unlockAccount,
  type Written,
} from "../adapters/accountSecurity"
import {describeSecurityEvent, describeSecurityEventContext} from "../securityEvents"
import {useStepUp} from "../composables/useStepUp"
import type {AccountStandingResponse, SecurityEventResponse} from "@/services/api"
import type {TypedStore} from "@/plugins/store"

defineOptions({name: "AccountSecurityPanel"})

const props = defineProps<{ userId: number }>()

const store = useStore() as TypedStore
const standing = ref<AccountStandingResponse | null>(null)
const events = ref<SecurityEventResponse[]>([])
const reason = ref("")
const correctedEmail = ref("")

const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)

const {open: stepUpOpen, attempt: withStepUp, proved: stepUpProved} = useStepUp(tell)

const isSelf = computed(() => store.getters.getLogin?.userId === props.userId)

const {open: previewOpen, loading: previewLoading, error: previewError, preview, show: showPreview} = useEmailPreview()

const resend = async () => {
  previewOpen.value = false
  await act(() => resendReenrolment(props.userId), "A new re-enrolment link is on its way.")
}

const log = computed(() => events.value.map((event) => ({
  label: describeSecurityEvent(event), value: describeSecurityEventContext(event), testid: "account-security-log-entry",
})))

const load = async () => {
  standing.value = await readAccountStanding(props.userId)
  events.value = (await readSecurityLogOf(props.userId))?.events ?? []
}

const act = (write: () => Promise<Written>, done: string) =>
  withStepUp(async () => {
    const result = await write()
    if (result.ok) {
      tell(done)
      reason.value = ""
      correctedEmail.value = ""
      await load()
    }
    return result
  })

watch(() => props.userId, load, {immediate: true})
</script>

<style scoped>
.account-security__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-bottom: 0.9rem;
}

.account-security__note {
  max-width: 44rem;
  margin-bottom: 0.9rem;
  font-size: 0.92rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.account-security__fields {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(18rem, 1fr));
  gap: 0.5rem;
  max-width: 48rem;
}

.account-security__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  padding-top: 0.4rem;
}
</style>
