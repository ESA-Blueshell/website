<script lang="ts" setup>
/* One email: what happened to it, the email as it was sent, who it went to and what sent it, with
   Retry and Resend. A resend is a new email in Sent, linked to this one. */
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import {
  type EmailDetail,
  canResend,
  canRetry,
  emailTypeLabel,
  readEmail,
  readSentEmail,
  resendEmail,
  retrySend,
  statusWord,
  timelineOf,
} from "@/domains/emails"
import store from "@/plugins/store"
import {formatMoment} from "@/utils/timestamps"

defineOptions({name: "SentEmailPage"})

const route = useRoute()
const id = computed(() => Number(route.params.id))

const detail = ref<EmailDetail | null>(null)
const asSent = ref<string | null>(null)
const loaded = ref(false)
const acting = ref(false)

const email = computed(() => detail.value?.email ?? null)
const wrong = computed(() => email.value?.deliveryStatus === "BOUNCED" || email.value?.deliveryStatus === "FAILED")
const personLink = computed(() => `/management/users?search=${encodeURIComponent(email.value?.recipientEmail ?? "")}`)
const isAdmin = computed(() => Boolean(store.getters.isAdmin))

const load = async () => {
  loaded.value = false
  const [found, rendered] = await Promise.all([readEmail(id.value), readSentEmail(id.value)])
  detail.value = found
  asSent.value = rendered?.html ?? null
  loaded.value = true
}

const act = async (how: "retry" | "resend") => {
  if (!email.value?.id || acting.value) return
  acting.value = true
  try {
    const answered = how === "retry" ? await retrySend(email.value.id) : await resendEmail(email.value.id)
    if (!answered.ok) {
      store.commit("setStatusSnackbarMessage", answered.reason)
      return
    }
    await load()
  } finally {
    acting.value = false
  }
}

watch(id, load)
onMounted(load)
</script>

<template>
  <management-page
    v-if="loaded && !email"
    :back="{to: '/management/mail/sent', label: 'Sent'}"
    eyebrow="Mail"
    testid="sent-email"
    title="No such email"
  >
    <p
      class="email__note"
      data-testid="sent-email-missing"
    >
      There is no email {{ id }}.
    </p>
  </management-page>
  <management-page
    v-else-if="email"
    :back="{to: '/management/mail/sent', label: 'Sent'}"
    :eyebrow="`Email · ${emailTypeLabel(email.emailType)}`"
    testid="sent-email"
    :title="statusWord(email.deliveryStatus)"
  >
    <template #lede>
      {{ email.subject }}, to {{ email.recipientName || email.recipientEmail }}
    </template>
    <template
      v-if="canResend(email) || canRetry(email)"
      #actions
    >
      <cut-button
        v-if="canResend(email)"
        :disabled="acting"
        testid="sent-email-resend"
        tone="solid"
        @click="act('resend')"
      >
        Resend
      </cut-button>
      <cut-button
        v-if="canRetry(email)"
        :disabled="acting"
        testid="sent-email-retry"
        @click="act('retry')"
      >
        Retry
      </cut-button>
    </template>

    <notice-box
      v-if="wrong"
      testid="sent-email-problem"
      :title="email.deliveryStatus === 'BOUNCED' ? 'Bounced' : 'Failed'"
      tone="danger"
    >
      <p>
        {{ email.errorReason || email.errorType || "The mail server gave no reason." }}
        Retry sends this email again to the same address; Resend makes a new one from the person's current address, so fix it on the person first.
      </p>
      <router-link
        data-testid="sent-email-person-fix"
        :to="personLink"
      >
        Open {{ email.recipientName || email.recipientEmail }}
      </router-link>
    </notice-box>

    <task-layout aside-title="Recipient">
      <p class="email__part-title">
        As sent
      </p>
      <iframe
        v-if="asSent"
        class="email__frame"
        data-testid="sent-email-as-sent"
        sandbox=""
        :srcdoc="asSent"
        title="The email as sent"
      />
      <p
        v-else
        class="email__note"
        data-testid="sent-email-no-body"
      >
        This email was sent before the site stored what it sent, so it cannot be shown.
      </p>

      <template #aside>
        <p data-testid="sent-email-recipient">
          <router-link :to="personLink">
            {{ email.recipientName || email.recipientEmail }}
          </router-link><br>{{ email.recipientEmail }}
        </p>
        <template v-if="email.senderAddress">
          <p class="email__aside-title">
            From
          </p>
          <p data-testid="sent-email-sender">
            {{ email.senderAddress }}
          </p>
        </template>
        <p class="email__aside-title">
          What happened
        </p>
        <ol
          class="email__timeline"
          data-testid="sent-email-timeline"
        >
          <li
            v-for="moment in timelineOf(email)"
            :key="moment.what"
            :class="{'email__moment--wrong': moment.wrong}"
          >
            <span>{{ moment.what }}</span>
            <span>{{ formatMoment(moment.at) }}</span>
          </li>
        </ol>
        <template v-if="email.resentFromId">
          <p class="email__aside-title">
            Made again from
          </p>
          <router-link
            data-testid="sent-email-resent-from"
            :to="`/management/mail/sent/${email.resentFromId}`"
          >
            Email #{{ email.resentFromId }}
          </router-link>
        </template>
        <p class="email__aside-title">
          Resends
        </p>
        <ul
          v-if="detail && detail.resends.length"
          class="email__resends"
          data-testid="sent-email-resends"
        >
          <li
            v-for="again in detail.resends"
            :key="again.id ?? undefined"
          >
            <router-link :to="`/management/mail/sent/${again.id}`">
              {{ statusWord(again.deliveryStatus) }}, {{ formatMoment(again.createdAt) }}
            </router-link>
          </li>
        </ul>
        <p v-else>
          None yet. A resend is a new email in the list, linked to this one.
        </p>
        <template v-if="email.messageId">
          <p class="email__aside-title">
            Message ID
          </p>
          <p class="email__mono">
            {{ email.messageId }}
          </p>
        </template>
        <template v-if="email.jobExecutionId">
          <p class="email__aside-title">
            Sent by
          </p>
          <p data-testid="sent-email-job">
            <router-link
              v-if="isAdmin"
              :to="`/management/jobs/${email.jobExecutionId}`"
            >
              Job #{{ email.jobExecutionId }}
            </router-link>
            <span v-else>Job #{{ email.jobExecutionId }}</span>
            <template v-if="email.initiatedByUserId">
              , queued by
              <router-link :to="`/management/users/${email.initiatedByUserId}`">
                user #{{ email.initiatedByUserId }}
              </router-link>
            </template>
          </p>
        </template>
      </template>
    </task-layout>
  </management-page>
</template>

<style scoped>
.email__notice {
  margin-top: 1rem;
}

.email__eyebrow,
.email__aside-title,
.email__part-title {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.email__note {
  margin: 0;
  color: var(--color-ash);
}

.email__frame {
  width: 100%;
  min-height: 34rem;
  border: 0;
  background: #ffffff;
}

.email__timeline,
.email__resends {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.email__timeline li {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
}

.email__moment--wrong {
  color: var(--color-error, #e5484d);
}

.email__mono {
  font-family: monospace;
  font-size: 0.75rem;
  word-break: break-all;
}
</style>
