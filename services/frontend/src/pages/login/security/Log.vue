<template>
  <account-frame
    :crumb="SECURITY_CRUMB"
    eyebrow="Security"
    heading="Security log"
    island-content
  >
    <section
      class="log"
      data-testid="security-log"
    >
      <p class="log__intro">
        Sign-ins and every change to how your account is signed in to, for twelve months.
      </p>
      <p
        v-if="loaded && !events.length"
        class="log__intro"
      >
        Nothing in the last twelve months.
      </p>
      <template
        v-for="day in days"
        :key="day.key"
      >
        <h2
          class="log__day"
          data-testid="security-log-day"
        >
          {{ day.name }}
        </h2>
        <ol class="log__entries">
          <li
            v-for="event in day.events"
            :key="event.id"
            class="log__entry"
            data-testid="security-log-entry"
          >
            <span class="log__when">{{ formatSecurityClock(event.occurredAt) }}</span>
            <span>
              <span class="log__what">{{ securityEventParts(event).what }}</span>
              <span
                v-if="byline(event)"
                class="log__who"
              >{{ byline(event) }}</span>
            </span>
            <span class="log__where">{{ event.browser && event.platform ? describeBrowser(event.browser, event.platform) : "" }}</span>
          </li>
        </ol>
      </template>
      <cut-button
        v-if="morePages"
        class="log__older"
        testid="security-log-older-btn"
        @click="load(page + 1)"
      >
        Show older
      </cut-button>
    </section>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import {
  describeBrowser,
  formatSecurityClock,
  readMySecurityLog,
  SECURITY_CRUMB,
  securityEventParts,
  type SecurityEventResponse,
  securityLogByDay,
} from "@/domains/auth"

const events = ref<SecurityEventResponse[]>([])
const page = ref(0)
const morePages = ref(false)
const loaded = ref(false)

const days = computed(() => securityLogByDay(events.value))

const byline = (event: SecurityEventResponse) => [securityEventParts(event).who, event.note].filter(Boolean).join(" · ")

const load = async (next: number) => {
  const read = await readMySecurityLog(next)
  loaded.value = true
  if (!read) return
  events.value = next === 0 ? read.events : [...events.value, ...read.events]
  page.value = next
  morePages.value = read.page + 1 < read.totalPages
}

onMounted(() => load(0))
</script>

<style scoped>
.log {
  padding-top: 1.5rem;
}

.log__intro {
  font-size: 0.85rem;
  color: var(--color-ash);
}

.log__intro + .log__intro {
  margin-top: 0.75rem;
}

.log__day {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.4rem 0 0.6rem;
  font-family: var(--font-display);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.log__day::after {
  content: "";
  flex: 1 1 auto;
  height: 1px;
  background: var(--color-hairline);
}

.log__entries {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.log__entry {
  display: grid;
  grid-template-columns: 6rem minmax(0, 1fr) 14rem;
  gap: 0 1rem;
  padding: 0.85rem 1rem 0.85rem 1.4rem;
  background-color: var(--band-ground);
  font-size: 0.92rem;
}

.log__when,
.log__where,
.log__who {
  font-size: 0.84rem;
  color: var(--color-ash);
}

.log__where {
  text-align: right;
}

.log__what {
  display: block;
  color: var(--color-chalk);
}

.log__who {
  display: block;
  margin-top: 0.2rem;
}

.log__older {
  margin-top: 1.5rem;
}

@media (max-width: 767px) {
  .log__entry {
    grid-template-columns: 3.4rem minmax(0, 1fr);
    padding-left: 1rem;
  }

  .log__where {
    grid-column: 2;
    text-align: left;
  }
}
</style>
