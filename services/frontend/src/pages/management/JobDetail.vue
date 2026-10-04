<script lang="ts" setup>
/* One job: what it did, why it failed, and what it concerns, each thing linked to its own page. */
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import PairList from "@/components/management/PairList.vue"
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
  <management-page
    v-if="loaded && !job"
    :back="{to: '/management/jobs', label: 'Jobs'}"
    eyebrow="System"
    testid="job-detail-page"
    title="No such job"
  >
    <p
      class="job-detail__note"
      data-testid="job-detail-missing"
    >
      There is no job {{ id }}.
    </p>
  </management-page>
  <management-page
    v-else-if="job"
    :back="{to: '/management/jobs', label: 'Jobs'}"
    eyebrow="System · Job"
    testid="job-detail-page"
    :title="previewTitle(job)"
  >
    <template
      v-if="jobDescription(job)"
      #lede
    >
      {{ jobDescription(job) }}
    </template>
    <template #actions>
      <cut-button
        v-if="canRetry(job)"
        testid="job-detail-retry"
        tone="solid"
        @click="retry"
      >
        {{ retryLabel(job) }}
      </cut-button>
      <cut-button
        :href="`/management/jobs?again=${job.id}`"
        testid="job-detail-run-again"
      >
        Run again
      </cut-button>
    </template>

    <pair-list
      class="job-detail__facts"
      :pairs="facts"
      testid="job-detail-facts"
    />

    <list-head title="What it concerns" />
    <ul
      v-if="job.relatedEntities.length"
      class="job-detail__concerns"
      data-testid="job-detail-concerns"
    >
      <li
        v-for="entity in job.relatedEntities"
        :key="`${entity.type}-${entity.id}`"
      >
        <span class="job-detail__kind">{{ relatedEntityTypeLabel(entity.type) }}</span>
        <router-link
          v-if="relatedEntityLink(entity)"
          class="job-detail__link"
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
      class="job-detail__line job-detail__line--quiet"
    >
      Nothing in particular.
    </p>
    <p
      v-if="payloadChips(job.payload).length"
      class="job-detail__payload"
    >
      <span
        v-for="chip in payloadChips(job.payload)"
        :key="chip.key"
      >{{ chip.label }}: {{ chip.value }}</span>
    </p>

    <template v-if="job.effect">
      <list-head title="Outcome" />
      <p class="job-detail__line">
        {{ effectLabel(job) }}
      </p>
    </template>

    <template v-if="job.skipReason">
      <list-head title="Skipped" />
      <p
        class="job-detail__line"
        data-testid="job-detail-skip-reason"
      >
        {{ job.skipReason }}
      </p>
    </template>

    <template v-if="job.errorType || errorSummary(job) !== '-'">
      <list-head title="Failure" />
      <p
        class="job-detail__line"
        data-testid="job-detail-failure"
      >
        {{ errorSummary(job) }}
      </p>
      <pre
        v-if="hasStackTrace(job)"
        class="job-detail__trace"
        data-testid="job-detail-stacktrace"
      >{{ stackTrace(job) }}</pre>
    </template>
  </management-page>
</template>

<style scoped>
.job-detail__facts {
  margin-top: 1.2rem;
}

.job-detail__concerns {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.job-detail__concerns li {
  display: flex;
  gap: 0.8rem;
  padding: 0.5rem 0.8rem;
  font-size: 0.9rem;
  background-color: var(--color-pit);
}

.job-detail__kind {
  min-width: 8rem;
  color: var(--color-ash);
}

.job-detail__link {
  color: var(--color-brand-ink);
  text-decoration: underline;
  text-underline-offset: 3px;
}

.job-detail__payload {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem 1.2rem;
  margin-top: 0.6rem;
  font-size: 0.84rem;
  color: var(--color-ash);
}

.job-detail__line {
  margin-bottom: 0.8rem;
  font-size: 0.92rem;
}

.job-detail__line--quiet,
.job-detail__note {
  color: var(--color-ash);
}

.job-detail__note {
  margin-top: 1.4rem;
}

.job-detail__trace {
  max-height: 28rem;
  overflow: auto;
  padding: 1rem 1.2rem;
  font-family: ui-monospace, monospace;
  font-size: 0.78rem;
  line-height: 1.5;
  white-space: pre;
  color: var(--color-ash);
  background-color: var(--color-pit);
  box-shadow: inset 0 0 0 1px var(--color-hairline);
}
</style>
