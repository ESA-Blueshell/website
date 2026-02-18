<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {type JobExecution, list, retry as retryJob} from "@/services/api"

defineOptions({name: "JobManagerPage"})

const executions = ref<JobExecution[]>([])
const loading = ref<boolean>(false)

const sortedExecutions = computed<JobExecution[]>(() => {
  return [...executions.value].sort((a, b) => (b.id ?? 0) - (a.id ?? 0))
})

const refresh = async () => {
  loading.value = true
  try {
    const response = await list()
    if (response.status === 200) {
      executions.value = response.data ?? []
    } else {
      console.log(response.error)
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
}

const retry = async (execution: JobExecution) => {
  if (!execution?.id) return
  try {
    const response = await retryJob({path: {id: execution.id}})
    if (response.status === 200 && response.data) {
      const idx = executions.value.findIndex((item) => item.id === response.data!.id)
      if (idx >= 0) executions.value.splice(idx, 1, response.data)
      else executions.value.unshift(response.data)
    } else {
      console.log(response.error)
    }
  } catch (error) {
    $handleNetworkError(error)
  }
}

onMounted(async () => {
  await refresh()
})
</script>

<template>
  <v-main>
    <top-banner title="Job Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 1200px"
      >
        <div class="d-flex align-center mb-4">
          <v-btn
            :disabled="loading"
            class="mr-3"
            color="primary"
            @click="refresh"
          >
            Refresh
          </v-btn>
          <span
            v-if="loading"
            class="text-caption"
          >
            Loading job executions...
          </span>
        </div>

        <v-table density="compact">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Attempts</th>
              <th>Queued</th>
              <th>Started</th>
              <th>Finished</th>
              <th>Payload</th>
              <th>Error</th>
              <th />
            </tr>
          </thead>
          <tbody>
            <tr v-if="sortedExecutions.length === 0">
              <td colspan="10">
                No job executions found.
              </td>
            </tr>
            <tr
              v-for="execution in sortedExecutions"
              :key="execution.id"
            >
              <td>{{ execution.id }}</td>
              <td>{{ execution.jobType }}</td>
              <td>
                <v-chip
                  :color="execution.status === 'FAILED' ? 'red' : execution.status === 'SUCCESS' ? 'green' : 'blue'"
                  size="small"
                  variant="flat"
                >
                  {{ execution.status }}
                </v-chip>
              </td>
              <td>{{ execution.attempts }}</td>
              <td>{{ execution.queuedAt ?? "-" }}</td>
              <td>{{ execution.startedAt ?? "-" }}</td>
              <td>{{ execution.finishedAt ?? "-" }}</td>
              <td class="cell">
                {{ execution.payload ?? "-" }}
              </td>
              <td class="cell">
                {{ execution.errorMessage ?? "-" }}
              </td>
              <td>
                <v-btn
                  v-if="execution.status === 'FAILED'"
                  size="small"
                  variant="outlined"
                  @click="retry(execution)"
                >
                  Retry
                </v-btn>
              </td>
            </tr>
          </tbody>
        </v-table>
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.cell {
  max-width: 300px;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
