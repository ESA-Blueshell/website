<script lang="ts" setup>
/* One job: what it did, why it failed, and what it concerns, each thing linked to its own page. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {type Job, canRetry, effectLabel, errorSummary, hasStackTrace, jobDescription, loadJob, payloadChips, previewTitle, relatedEntityLabel, relatedEntityLink, relatedEntityTypeLabel, retryJob, retryLabel, stackTrace, statusTitle, titleCase, triggerLabel, actorDisplay} from "@/domains/jobs"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "JobDetailPage"})

const route = useRoute()
const id = computed(() => Number(route.params.id))

const job = ref<Job | null>(null)
const loaded = ref(false)

const load = async () => {
  job.value = await loadJob(id.value)
  loaded.value = true
}

const facts = computed(() => {
  const one = job.value
  if (!one) return []
  return [
    {label: "Status", value: statusTitle(one.status)},
    {label: "Kind", value: titleCase(one.category ?? "other")},
    {label: "Attempts", value: attemptsLabel(one.attempts)},
    {label: "Queued by", value: one.trigger ? triggerLabel(one) : actorDisplay(one)},
    {label: "Queued", value: formatDate(one.queuedAt)},
    {label: "Started", value: formatDate(one.startedAt)},
    {label: "Finished", value: formatDate(one.finishedAt)},
    ...(one.nextAttemptAt ? [{label: "Next attempt", value: formatDate(one.nextAttemptAt)}] : []),
  ]
})

const retry = async () => {
  const retried = await retryJob(id.value)
  if (!retried.ok) {
    store.commit("setStatusSnackbarMessage", retried.reason)
    return
  }
  await load()
}

onMounted(load)
</script>

<template>
  <div
    class="job-detail-page"
    data-testid="job-detail-page"
  >
    <router-link
      class="job-detail-page__back"
      to="/management/jobs"
    >
      Jobs
    </router-link>

    <p
      v-if="loaded && !job"
      class="job-detail-page__note"
      data-testid="job-detail-missing"
    >
      There is no job {{ id }}.
    </p>

    <template v-if="job">
      <header class="job-detail-page__head">
        <h1 class="job-detail-page__title">
          {{ previewTitle(job) }}
        </h1>
        <div class="job-detail-page__actions">
          <button
            v-if="canRetry(job)"
            class="job-detail-page__action"
            data-testid="job-detail-retry"
            type="button"
            @click="retry"
          >
            {{ retryLabel(job) }}
          </button>
          <router-link
            class="job-detail-page__action"
            data-testid="job-detail-run-again"
            :to="{path: '/management/jobs', query: {again: String(job.id)}}"
          >
            Run again
          </router-link>
        </div>
      </header>

      <p
        v-if="jobDescription(job)"
        class="job-detail-page__note"
      >
        {{ jobDescription(job) }}
      </p>

      <dl
        class="job-detail-page__facts"
        data-testid="job-detail-facts"
      >
        <div
          v-for="fact in facts"
          :key="fact.label"
        >
          <dt>{{ fact.label }}</dt>
          <dd>{{ fact.value }}</dd>
        </div>
      </dl>

      <section class="job-detail-page__part">
        <h2>What it concerns</h2>
        <ul
          v-if="job.relatedEntities.length"
          class="job-detail-page__concerns"
          data-testid="job-detail-concerns"
        >
          <li
            v-for="entity in job.relatedEntities"
            :key="`${entity.type}-${entity.id}`"
          >
            <span class="job-detail-page__kind">{{ relatedEntityTypeLabel(entity.type) }}</span>
            <router-link
              v-if="relatedEntityLink(entity)"
              :data-testid="`job-detail-concern-${entity.type}-${entity.id}`"
              :to="relatedEntityLink(entity) ?? ''"
            >
              {{ relatedEntityLabel(entity) }}
            </router-link>
            <span v-else>{{ relatedEntityLabel(entity) }}</span>
          </li>
        </ul>
        <p
          v-else
          class="job-detail-page__note"
        >
          Nothing in particular.
        </p>
        <p
          v-if="payloadChips(job.payload).length"
          class="job-detail-page__payload"
        >
          <span
            v-for="chip in payloadChips(job.payload)"
            :key="chip.key"
          >{{ chip.label }}: {{ chip.value }}</span>
        </p>
      </section>

      <section
        v-if="job.effect"
        class="job-detail-page__part"
      >
        <h2>Outcome</h2>
        <p>{{ effectLabel(job) }}</p>
      </section>

      <section
        v-if="job.skipReason"
        class="job-detail-page__part"
      >
        <h2>Skipped</h2>
        <p data-testid="job-detail-skip-reason">
          {{ job.skipReason }}
        </p>
      </section>

      <section
        v-if="job.errorType || errorSummary(job) !== '-'"
        class="job-detail-page__part"
      >
        <h2>Failure</h2>
        <p data-testid="job-detail-failure">
          {{ errorSummary(job) }}
        </p>
        <pre
          v-if="hasStackTrace(job)"
          class="job-detail-page__trace"
          data-testid="job-detail-stacktrace"
        >{{ stackTrace(job) }}</pre>
      </section>
    </template>
  </div>
</template>

<style scoped>
.job-detail-page {
  display: flex;
  flex-direction: column;
  gap: 1.2rem;
  max-width: 60rem;
  padding: 2rem 2.4rem 3rem;
}

.job-detail-page__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.job-detail-page__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
}

.job-detail-page__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.3rem, 3vw, 1.9rem);
}

.job-detail-page__actions {
  display: flex;
  gap: 0.6rem;
}

.job-detail-page__action {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  text-decoration: none;
  cursor: pointer;
}

.job-detail-page__note {
  margin: 0;
  color: var(--color-ash);
}

.job-detail-page__facts {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(11rem, 1fr));
  gap: 0.9rem 1.5rem;
  margin: 0;
}

.job-detail-page__facts dt,
.job-detail-page__kind {
  font-size: 0.7rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.job-detail-page__facts dd {
  margin: 0.2rem 0 0;
}

.job-detail-page__part h2 {
  margin: 0 0 0.5rem;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.job-detail-page__concerns {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.job-detail-page__concerns li {
  display: flex;
  align-items: baseline;
  gap: 0.8rem;
}

.job-detail-page__concerns a {
  color: var(--color-brand);
}

.job-detail-page__payload {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1rem;
  margin: 0.6rem 0 0;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.job-detail-page__trace {
  max-height: 28rem;
  overflow: auto;
  padding: 1rem;
  font-size: 0.78rem;
  white-space: pre;
  background: var(--band-ground);
  border: 1px solid var(--color-hairline);
}

@media (max-width: 839px) {
  .job-detail-page {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
