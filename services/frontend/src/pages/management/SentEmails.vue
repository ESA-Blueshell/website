<script lang="ts" setup>
/* Every email the site sends, newest first, from the moment its job is queued. A failed one is
   retried as it was; any other is resent from the person's current address. */
import {computed, onMounted, ref} from "vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import {
  type EmailStats,
  type SentEmail,
  canResend,
  canRetry,
  emailTypeLabel,
  loadEmailPage,
  loadEmailStats,
  resendEmail,
  retrySend,
  sentFacts,
  stateKindOf,
  statusWord,
} from "@/domains/emails"
import {usePagedTable} from "@/composables/usePagedTable"
import store from "@/plugins/store"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "SentEmailsPage"})

const stats = ref<EmailStats | null>(null)
const acting = ref<number | null>(null)

const loadStats = async () => {
  stats.value = await loadEmailStats()
}

const table = usePagedTable<SentEmail>((query) => {
  void loadStats()
  return loadEmailPage(query)
}, {pageSize: 50})
const {rows, page, totalPages, search, pageRangeLabel, refresh} = table

const facts = computed(() => sentFacts(stats.value))

/** Retry sends this email again; Resend makes a new one for the person's current address. */
const act = async (email: SentEmail, how: "retry" | "resend") => {
  if (email.id == null || acting.value !== null) return
  acting.value = email.id
  try {
    const answered = how === "retry" ? await retrySend(email.id) : await resendEmail(email.id)
    if (!answered.ok) {
      store.commit("setStatusSnackbarMessage", answered.reason)
      return
    }
    await refresh()
  } finally {
    acting.value = null
  }
}

onMounted(refresh)
</script>

<template>
  <div
    class="sent"
    data-testid="sent-emails"
  >
    <header class="sent__head">
      <div>
        <h1 class="sent__title">
          Sent
        </h1>
        <p class="sent__note">
          Every email the site sends, from the moment it is queued, and whether it arrived.
        </p>
      </div>
      <button
        class="sent__action"
        data-testid="sent-emails-refresh"
        type="button"
        @click="refresh"
      >
        Refresh
      </button>
    </header>

    <dl
      class="sent__facts"
      data-testid="sent-emails-facts"
    >
      <div>
        <dt>Queued</dt>
        <dd data-testid="sent-emails-queued">
          {{ facts.queued }}
        </dd>
        <dd class="sent__fact-sub">
          Waiting for their job to send them
        </dd>
      </div>
      <div>
        <dt>Delivered</dt>
        <dd data-testid="sent-emails-delivered">
          {{ facts.delivered }}%
        </dd>
        <dd class="sent__fact-sub">
          Of {{ facts.sent }} sent, all time; {{ facts.opened }}% opened
        </dd>
      </div>
      <div>
        <dt>Needs a look</dt>
        <dd data-testid="sent-emails-needs-look">
          {{ facts.needsLook }}
        </dd>
        <dd class="sent__fact-sub">
          {{ facts.bounced }} bounced, {{ facts.failed }} failed
        </dd>
      </div>
    </dl>

    <search-box
      label="Search emails"
      :model-value="search ?? ''"
      testid="sent-emails-search"
      @update:model-value="search = $event"
    />

    <p
      v-if="rows.length === 0"
      class="sent__note"
      data-testid="sent-emails-empty"
    >
      No emails to show.
    </p>

    <ul class="sent__rows">
      <li
        v-for="email in rows"
        :key="email.id ?? undefined"
        class="sent__row"
        :data-testid="`sent-email-row-${email.id}`"
      >
        <span class="sent__when">{{ formatDateNoSeconds(email.sentAt ?? email.createdAt) }}</span>
        <span class="sent__what">
          <router-link
            class="sent__subject"
            :data-testid="`sent-email-open-${email.id}`"
            :to="`/management/mail/sent/${email.id}`"
          >{{ email.subject }}</router-link>
          <span class="sent__sub">{{ emailTypeLabel(email.emailType) }}</span>
        </span>
        <span class="sent__sub sent__to">{{ email.recipientEmail }}</span>
        <state-mark
          :kind="stateKindOf(email.deliveryStatus)"
          :testid="`sent-email-status-${email.id}`"
        >
          {{ statusWord(email.deliveryStatus) }}
        </state-mark>
        <span class="sent__sub">{{ email.attempts ?? 0 }}</span>
        <span class="sent__acts">
          <button
            v-if="canRetry(email)"
            class="sent__mini"
            :data-testid="`sent-email-retry-${email.id}`"
            :disabled="acting !== null"
            type="button"
            @click="act(email, 'retry')"
          >Retry</button>
          <button
            v-else-if="canResend(email)"
            class="sent__mini"
            :data-testid="`sent-email-resend-${email.id}`"
            :disabled="acting !== null"
            type="button"
            @click="act(email, 'resend')"
          >Resend</button>
          <span
            v-else-if="email.deliveryStatus === 'QUEUED'"
            class="sent__sub"
          >Job waiting</span>
        </span>
      </li>
    </ul>

    <nav
      v-if="totalPages > 1"
      class="sent__pager"
      data-testid="sent-emails-pager"
    >
      <span>{{ pageRangeLabel }}</span>
      <button
        class="sent__mini"
        data-testid="sent-emails-previous"
        :disabled="page <= 1"
        type="button"
        @click="page -= 1"
      >
        Previous
      </button>
      <button
        class="sent__mini"
        data-testid="sent-emails-next"
        :disabled="page >= totalPages"
        type="button"
        @click="page += 1"
      >
        Next
      </button>
    </nav>
  </div>
</template>

<style scoped>
.sent {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 72rem;
  padding: 2rem 2.4rem 3rem;
}

.sent__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.sent__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.sent__note {
  margin: 0;
  color: var(--color-ash);
}

.sent__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin: 0;
}

.sent__facts > div {
  padding: 0.9rem 1rem;
  background-color: var(--band-ground);
}

.sent__facts dt {
  font-size: 0.75rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.sent__facts dd {
  margin: 0.2rem 0;
  font-family: var(--font-display);
  font-size: 1.6rem;
}

.sent__facts .sent__fact-sub {
  margin: 0;
  font-family: inherit;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.sent__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.sent__row {
  display: grid;
  grid-template-columns: 8.5rem minmax(0, 1fr) minmax(0, 14rem) 7rem 3rem 6rem;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.sent__what {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.sent__subject {
  overflow: hidden;
  font-weight: 600;
  color: var(--color-chalk);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sent__sub,
.sent__when {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sent__acts {
  display: flex;
  justify-content: flex-end;
}

.sent__action,
.sent__mini {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.sent__mini:disabled {
  opacity: 0.45;
  cursor: default;
}

.sent__pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.6rem;
  font-size: 0.84rem;
  color: var(--color-ash);
}

@media (max-width: 839px) {
  .sent {
    padding: 1.2rem 1.1rem 2rem;
  }

  .sent__facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .sent__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .sent__when,
  .sent__to,
  .sent__row > .sent__sub {
    display: none;
  }
}
</style>
