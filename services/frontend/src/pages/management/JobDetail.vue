<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CutButton from "@/components/island/CutButton.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import PairList from "@/components/management/PairList.vue"
import {type Job, canRetry, effectLabel, errorSummary, foldedTriggerLabel, hasStackTrace, jobDescription, payloadChips, previewTitle, readJob, relatedEntityLabel, relatedEntityLink, relatedEntityTypeLabel, retryJob, retryLabel, stackTrace, statusTitle, titleCase, triggerLabel, actorDisplay} from "@/domains/jobs"
import store from "@/plugins/store"
import {attemptsLabel} from "@/utils/jobAttempts"
import {formatDate} from "@/utils/timestamps"

defineOptions({name: "JobDetailPage"})

const route = useRoute()
const id = computed(() => Number(route.params.id))

const job = ref<Job | null>(null)
const missing = ref(false)
const loaded = ref(false)

const load = async () => {
  const read = await readJob(id.value)
  job.value = read.job
  missing.value = read.missing
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
    ...(one.foldedTriggers ?? []).map((folded, index) => ({
      label: index === 0 ? "Also queued by" : `Also queued by (${index + 1})`,
      value: `${foldedTriggerLabel(folded)} · ${folded.initiatedByDisplay} · ${formatDate(folded.at)}`,
      testid: `job-detail-folded-${index}`,
    })),
    ...(one.forced ? [{label: "Forced", value: "It ran even though it would have been skipped", testid: "job-detail-forced"}] : []),
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

// The router keeps this page when only the id in the address changes.
watch(id, load, {immediate: true})
</script>

<template>
  <management-page
    v-if="loaded && !job"
    :back="{to: '/management/jobs', label: 'Jobs'}"
    eyebrow="System"
    testid="job-detail-page"
    :title="missing ? 'No such job' : `Job ${id}`"
  >
    <p
      v-if="missing"
      class="job-detail__note"
      data-testid="job-detail-missing"
    >
      There is no job {{ id }}.
    </p>
    <template v-else>
      <p
        class="job-detail__note"
        data-testid="job-detail-unread"
      >
        Job {{ id }} could not be read.
      </p>
      <cut-button
        testid="job-detail-reload"
        @click="load"
      >
        Try again
      </cut-button>
    </template>
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
        Re-run
      </cut-button>
    </template>

    <pair-list
      class="job-detail__facts"
      :pairs="facts"
      testid="job-detail-facts"
    />

    <list-head title="Related to" />
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
      Nothing.
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
        <a
          v-if="job.effectLink"
          class="job-detail__link"
          data-testid="job-detail-effect-link"
          :href="job.effectLink"
          rel="noopener"
          target="_blank"
        >Open in Discord</a>
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
  margin: 1.4rem 0 0.8rem;
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
