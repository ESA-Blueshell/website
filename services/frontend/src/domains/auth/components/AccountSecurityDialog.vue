<template>
  <v-dialog
    :model-value="modelValue"
    max-width="640"
    scrollable
    @update:model-value="emit('update:modelValue', $event)"
  >
    <v-card
      :title="`Account security of ${userName}`"
      data-testid="account-security-dialog"
    >
      <v-card-text>
        <v-progress-circular
          v-if="!standing"
          indeterminate
        />
        <template v-else>
          <div class="d-flex flex-wrap ga-2 mb-4">
            <v-chip
              :color="standing.twoFactorOn ? 'success' : undefined"
              data-testid="account-security-two-factor-chip"
            >
              Two-factor {{ standing.twoFactorOn ? "on" : "off" }}
            </v-chip>
            <v-chip
              v-if="standing.awaitingReenrolment"
              color="warning"
              data-testid="account-security-awaiting-chip"
            >
              Awaiting re-enrolment
            </v-chip>
            <v-chip
              v-if="standing.locked"
              color="error"
              data-testid="account-security-locked-chip"
            >
              Locked
            </v-chip>
          </div>

          <p class="mb-3">
            Before you act, hear from the person themselves, somewhere other than the account's own email.
          </p>
          <v-textarea
            v-model="reason"
            data-testid="account-security-reason-field"
            auto-grow
            label="Reason"
            rows="2"
          />
          <v-text-field
            v-if="standing.locked"
            v-model="correctedEmail"
            data-testid="account-security-email-field"
            label="Correct the email address first (optional)"
            type="email"
          />

          <div class="d-flex flex-wrap ga-2 mb-6">
            <v-btn
              v-if="standing.locked"
              :disabled="!reason.trim()"
              color="primary"
              data-testid="account-security-unlock-btn"
              @click="run(() => unlockAccount(userId, reason.trim(), correctedEmail.trim()), 'Unlocked. A password reset is on its way.')"
            >
              Unlock
            </v-btn>
            <v-btn
              v-if="standing.twoFactorOn && !isSelf"
              :disabled="!reason.trim()"
              color="error"
              data-testid="account-security-reset-btn"
              variant="outlined"
              @click="run(() => resetTwoFactorOf(userId, reason.trim()), 'Two-factor reset. A re-enrolment link is on its way.')"
            >
              Reset two-factor
            </v-btn>
            <v-btn
              v-if="standing.awaitingReenrolment && !isSelf"
              data-testid="account-security-resend-btn"
              variant="outlined"
              @click="showPreview(() => previewReenrolment(userId))"
            >
              Resend re-enrolment link
            </v-btn>
          </div>

          <p class="text-subtitle-1 mb-1">
            Security log
          </p>
          <v-list density="compact">
            <v-list-item
              v-for="event in events"
              :key="event.id"
              :subtitle="`${formatDate(event.occurredAt)}${event.browser ? `, ${event.browser}` : ''}${event.note ? ` · ${event.note}` : ''}`"
              :title="describeSecurityEvent(event)"
              data-testid="account-security-log-entry"
            />
          </v-list>
        </template>
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn @click="emit('update:modelValue', false)">
          Close
        </v-btn>
      </v-card-actions>
    </v-card>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      confirm-label="Resend re-enrolment link"
      title="Re-enrolment email"
      @confirm="resend"
    />

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="true"
      @proved="retry?.()"
    />
  </v-dialog>
</template>

<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"
import StepUpDialog from "./StepUpDialog.vue"
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
import {describeSecurityEvent} from "../securityEvents"
import type {AccountStandingResponse, SecurityEventResponse} from "@/services/api"
import type {TypedStore} from "@/plugins/store"

const props = defineProps<{ modelValue: boolean; userId: number; userName: string }>()
const emit = defineEmits<{ "update:modelValue": [open: boolean] }>()

const store = useStore() as TypedStore
const standing = ref<AccountStandingResponse | null>(null)
const events = ref<SecurityEventResponse[]>([])
const reason = ref("")
const correctedEmail = ref("")
const stepUpOpen = ref(false)
const retry = ref<(() => void) | null>(null)

/** Another admin resets an admin's own two-factor; nobody resets their own. */
const isSelf = computed(() => store.getters.getLogin?.userId === props.userId)

const {open: previewOpen, loading: previewLoading, error: previewError, preview, show: showPreview} = useEmailPreview()

const resend = async () => {
  previewOpen.value = false
  await run(() => resendReenrolment(props.userId), "A new re-enrolment link is on its way.")
}

const formatDate = (iso: string) => DateTime.fromISO(iso).toLocaleString(DateTime.DATETIME_MED)

const load = async () => {
  standing.value = await readAccountStanding(props.userId)
  events.value = (await readSecurityLogOf(props.userId))?.events ?? []
}

const run = async (write: () => Promise<Written>, done: string) => {
  const result = await write()
  if (result.ok) {
    store.commit("setStatusSnackbarMessage", done)
    reason.value = ""
    correctedEmail.value = ""
    await load()
  } else if (result.needsStepUp) {
    retry.value = () => void run(write, done)
    stepUpOpen.value = true
  } else {
    store.commit("setStatusSnackbarMessage", result.reason)
  }
}

watch(() => [props.modelValue, props.userId], ([open]) => {
  if (open) void load()
}, {immediate: true})
</script>
