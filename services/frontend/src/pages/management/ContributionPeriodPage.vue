<script lang="ts" setup>
/* One contribution period: its dates, fees and half-year cutoff, or an empty form for a new one.
   Deleting sits in a danger zone and asks for the period's name first. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import ContributionPeriodForm from "@/components/management/ContributionPeriodForm.vue"
import {type ContributionPeriodResponse, deletePeriod, listPeriods} from "@/domains/contribution"
import {$handleNetworkError} from "@/plugins/handleNetworkError"

defineOptions({name: "ContributionPeriodPage"})

const route = useRoute()
const router = useRouter()

const id = computed(() => (route.params.id ? Number(route.params.id) : null))
const period = ref<ContributionPeriodResponse | null>(null)
const loaded = ref(false)
const typed = ref("")

/** What the period is called on the strip and in the danger zone, such as 2025–26. */
const name = computed(() => (period.value ? `${period.value.startDate.slice(0, 4)}–${period.value.endDate.slice(2, 4)}` : ""))
const mayDelete = computed(() => typed.value.trim() === name.value)

const onSaved = (saved: ContributionPeriodResponse) => router.push(`/management/contributions/${saved.id}`)
const back = () => router.push(id.value ? `/management/contributions/${id.value}` : "/management/contributions")

const remove = async () => {
  if (!period.value || !mayDelete.value) return
  try {
    await deletePeriod(period.value.id)
    await router.push("/management/contributions")
  } catch (error) {
    $handleNetworkError(error)
  }
}

onMounted(async () => {
  if (id.value != null) {
    try {
      period.value = (await listPeriods()).find((one) => one.id === id.value) ?? null
    } catch (error) {
      $handleNetworkError(error)
    }
  }
  loaded.value = true
})
</script>

<template>
  <div
    class="period"
    data-testid="contribution-period-page"
  >
    <router-link
      class="period__back"
      to="/management/contributions"
    >
      Contributions
    </router-link>

    <h1 class="period__title">
      {{ id ? `Contribution period ${name}` : "New contribution period" }}
    </h1>

    <p
      v-if="loaded && id && !period"
      class="period__note"
      data-testid="contribution-period-missing"
    >
      There is no contribution period {{ id }}.
    </p>

    <template v-else-if="loaded">
      <contribution-period-form
        :contribution-period="period ?? undefined"
        @cancelled="back"
        @changed="onSaved"
      />

      <section
        v-if="period"
        class="period__danger"
        data-testid="contribution-period-danger"
      >
        <h2>Danger zone</h2>
        <p class="period__note">
          Deleting the period deletes its contributions with it. Type <strong>{{ name }}</strong> to delete it.
        </p>
        <input
          v-model="typed"
          aria-label="The period's name"
          class="period__confirm"
          data-testid="contribution-period-delete-name"
          :placeholder="name"
          type="text"
        >
        <button
          class="period__delete"
          data-testid="contribution-period-delete-btn"
          :disabled="!mayDelete"
          type="button"
          @click="remove"
        >
          Delete this period
        </button>
      </section>
    </template>
  </div>
</template>

<style scoped>
.period {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 44rem;
  padding: 2rem 2.4rem 3rem;
}

.period__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.period__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.period__note {
  margin: 0;
  color: var(--color-ash);
}

.period__danger {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem;
  border: 1px solid var(--color-error, #e5484d);
}

.period__danger h2 {
  margin: 0;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-error, #e5484d);
}

.period__confirm {
  max-width: 16rem;
  padding: 0.5rem 0.7rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  color: var(--color-chalk);
}

.period__delete {
  align-self: flex-start;
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-error, #e5484d);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-error, #e5484d);
  cursor: pointer;
}

.period__delete:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 839px) {
  .period {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
