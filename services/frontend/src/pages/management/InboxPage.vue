<script lang="ts" setup>
/* Mail to an @esa-blueshell.nl address with no mailbox of its own, replies to the site's emails
   among it, newest first. Bounces show on the email that bounced, not here. */
import {computed, onMounted, ref} from "vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import {emailTypeLabel} from "@/domains/emails"
import {type InboxCounts, type InboxEntry, InboxState, followsOf, inboxStateWord, loadInboxPage, readInboxCounts} from "@/domains/mail"
import {usePagedTable} from "@/composables/usePagedTable"
import {formatDateNoSeconds} from "@/utils/timestamps"

defineOptions({name: "InboxPage"})

const counts = ref<InboxCounts | null>(null)

const table = usePagedTable<InboxEntry>((query) => {
  void readInboxCounts().then((read) => {
    counts.value = read
  })
  return loadInboxPage(query)
}, {pageSize: 50})
const {rows, page, totalPages, search, pageRangeLabel, refresh} = table

const oldest = computed(() => (counts.value?.oldestNewAt ? `Oldest from ${formatDateNoSeconds(counts.value.oldestNewAt)}` : "Nothing waits"))
const markOf = (entry: InboxEntry) => (entry.automatic ? "not-compared" : entry.state === InboxState.NEW ? "not-created" : "in-step")
const follows = (entry: InboxEntry) => followsOf(entry, emailTypeLabel)

onMounted(refresh)
</script>

<template>
  <div
    class="inbox"
    data-testid="inbox"
  >
    <header class="inbox__head">
      <div>
        <h1 class="inbox__title">
          Inbox
        </h1>
        <p class="inbox__note">
          Mail sent to an @esa-blueshell.nl address that has no mailbox of its own, including replies to the site's
          emails. Bounces are not here: they show on the email that bounced.
        </p>
      </div>
      <button
        class="inbox__action"
        data-testid="inbox-refresh"
        type="button"
        @click="refresh"
      >
        Refresh
      </button>
    </header>

    <dl
      class="inbox__facts"
      data-testid="inbox-facts"
    >
      <div>
        <dt>New</dt>
        <dd data-testid="inbox-new">
          {{ counts?.new ?? 0 }}
        </dd>
        <dd class="inbox__fact-sub">
          {{ oldest }}
        </dd>
      </div>
      <div>
        <dt>Replied or handled</dt>
        <dd data-testid="inbox-done">
          {{ counts?.done ?? 0 }}
        </dd>
        <dd class="inbox__fact-sub">
          Since the inbox started
        </dd>
      </div>
      <div>
        <dt>Automatic replies</dt>
        <dd data-testid="inbox-automatic">
          {{ counts?.automatic ?? 0 }}
        </dd>
        <dd class="inbox__fact-sub">
          Kept apart from what needs an answer
        </dd>
      </div>
    </dl>

    <search-box
      label="Search the inbox"
      :model-value="search ?? ''"
      testid="inbox-search"
      @update:model-value="search = $event"
    />

    <p
      v-if="rows.length === 0"
      class="inbox__note"
      data-testid="inbox-empty"
    >
      Nothing has arrived.
    </p>

    <ul class="inbox__rows">
      <li
        v-for="entry in rows"
        :key="entry.id"
        class="inbox__row"
        :data-testid="`inbox-row-${entry.id}`"
      >
        <span class="inbox__sub">{{ formatDateNoSeconds(entry.receivedAt) }}</span>
        <span class="inbox__who">
          <router-link
            v-if="entry.senderUserId"
            :to="`/management/users/${entry.senderUserId}`"
          >{{ entry.senderName ?? entry.fromName ?? entry.fromAddress }}</router-link>
          <span v-else>{{ entry.fromName ?? "Unknown sender" }}</span>
          <span class="inbox__sub">{{ entry.fromAddress }}</span>
        </span>
        <span class="inbox__what">
          <span class="inbox__subject">{{ entry.subject || "(no subject)" }}</span>
          <span
            v-if="follows(entry)"
            class="inbox__sub"
            :data-testid="`inbox-follows-${entry.id}`"
          >{{ follows(entry) }}</span>
        </span>
        <state-mark
          :kind="markOf(entry)"
          :testid="`inbox-state-${entry.id}`"
        >
          {{ inboxStateWord(entry) }}
        </state-mark>
        <span class="inbox__sub">{{ entry.handledByName ?? "" }}</span>
      </li>
    </ul>

    <nav
      v-if="totalPages > 1"
      class="inbox__pager"
      data-testid="inbox-pager"
    >
      <span>{{ pageRangeLabel }}</span>
      <button
        class="inbox__action"
        data-testid="inbox-previous"
        :disabled="page <= 1"
        type="button"
        @click="page -= 1"
      >
        Previous
      </button>
      <button
        class="inbox__action"
        data-testid="inbox-next"
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
.inbox {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.inbox__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.inbox__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.inbox__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.inbox__facts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 1rem;
  margin: 0;
}

.inbox__facts > div {
  padding: 0.9rem 1rem;
  background-color: var(--band-ground);
}

.inbox__facts dt {
  font-size: 0.75rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.inbox__facts dd {
  margin: 0.2rem 0;
  font-family: var(--font-display);
  font-size: 1.6rem;
}

.inbox__facts .inbox__fact-sub {
  margin: 0;
  font-family: inherit;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.inbox__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.inbox__row {
  display: grid;
  grid-template-columns: 8.5rem minmax(0, 14rem) minmax(0, 1fr) 9rem 7rem;
  align-items: center;
  gap: 1rem;
  padding: 0.7rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.inbox__who,
.inbox__what {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  min-width: 0;
}

.inbox__who a {
  color: var(--color-chalk);
}

.inbox__subject {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inbox__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.inbox__action {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.inbox__action:disabled {
  opacity: 0.45;
  cursor: default;
}

.inbox__pager {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 0.6rem;
  font-size: 0.84rem;
  color: var(--color-ash);
}

@media (max-width: 839px) {
  .inbox {
    padding: 1.2rem 1.1rem 2rem;
  }

  .inbox__facts {
    grid-template-columns: minmax(0, 1fr);
  }

  .inbox__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .inbox__row > .inbox__sub,
  .inbox__who {
    display: none;
  }
}
</style>
