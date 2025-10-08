<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/banners/TopBanner.vue"
import {
  type Answer,
  type Event,
  type EventSignUp,
  findEventById,
  findEventSignUpsByEventId,
  type PersonalInfo,
  type Question,
  QuestionType,
} from "@/lib"

const event = ref<Event>()
const eventSignUps = ref<EventSignUp[]>([])

export type EventSignUpPersons = {
  questionAnswers: Map<number, Answer>,
  person: PersonalInfo;
};
const eventSignUpPersons = ref<EventSignUpPersons[]>([])

const route = useRoute()

onMounted(async () => {
  try {
    const eventId: number = Number(route.params.id)
    const [eventResp, signupsResp] = await Promise.all([
      findEventById({path: {id: eventId}}),
      findEventSignUpsByEventId({path: {eventId}}),
    ])

    eventSignUps.value = signupsResp.data ?? []
    event.value = (eventResp.data ?? {}) as Event

    // Build per-person map: personal info + answers keyed by questionId
    // eventSignUps.value.forEach((es: EventSignUp) => {
      // const grouped = [...Map.groupBy(es.answers ?? [], a => a.questionId)]
      //   // .filter(([, arr]) => arr.length)
      //   // .map(([k, arr]) => [k, arr[0]]) as Map<number, Answer>,
      //
      // console.log("grouped: ", grouped)
      // eventSignUpPersons.value.push({
      //   questionAnswers: grouped,
      //   person: (es.guest ?? es.user)! as PersonalInfo,
      // })
    // })
  } catch (err) {
    console.error(err)
  }
})

const participants = computed(() =>
  [...eventSignUpPersons.value].sort((a, b) =>
    a.person.fullName.localeCompare(b.person.fullName),
  ),
)

const allQuestions = computed<Question[]>(() => {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const sf: any = (event.value as any)?.signUpForm
  if (!sf) return []
  return Array.isArray(sf) ? (sf as Question[]) : Object.values(sf as Record<string, Question>)
})

const checkboxQuestions = computed<Question[]>(() =>
  allQuestions.value.filter(q => q.type === QuestionType.CHECKBOX),
)

const radioQuestions = computed<Question[]>(() =>
  allQuestions.value.filter(q => q.type === QuestionType.RADIO),
)

const openQuestions = computed<Question[]>(() =>
  allQuestions.value.filter(q => q.type === QuestionType.OPEN),
)

watch(radioQuestions, (radioQuestions: Question[]) => {
  console.log(radioQuestions)
})
watch(allQuestions, (allQuestions: Question[]) => {
  console.log("all questions: ", allQuestions)
})


/** Stable key for questions even if id is optional */
function qKey(q: Question) {
  return q.id ?? q.idx
}

/** Given a person + questionId, return boolean[] for option selections (safe-normalized) */
function selectionsFor(esp: EventSignUpPersons, qid: number, optionCount: number): boolean[] {
  const sel = esp.questionAnswers[qid]?.optionSelections ?? []
  return Array.from({length: optionCount}, (_, i) => !!sel[i])
}

/** Totals per radio question (column sums) */
const radioTotals = computed(() => {
  const totals: Record<number, number[]> = {}
  radioQuestions.value.forEach((q: Question) => {
    const id = qKey(q) as number
    const cols = q.choiceLabels?.length ?? 0
    const init = Array.from({length: cols}, () => 0)
    totals[id] = init
    participants.value.forEach(esp => {
      const sel = selectionsFor(esp, id, cols)
      sel.forEach((v, idx) => {
        if (v) totals[id][idx]++
      })
    })
  })
  return totals
})

/** Open-answer text, empty if none */
function openTextFor(esp: EventSignUpPersons, qid: number): string {
  return esp.questionAnswers[qid]?.textResponse?.trim?.() ?? ""
}
</script>

<template>
  <v-main>
    <top-banner :title="event?.title ? event.title + ' sign-ups' : 'Sign-ups'" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 1100px"
      >
        <p class="text-h5 mb-6">
          Total signups: {{ eventSignUps.length }}
        </p>

        <!-- ===================== Attendees ===================== -->
        <v-card class="mb-10">
          <v-card-title class="text-h5">
            Attendees
          </v-card-title>
          <v-card-text>
            <v-table
              density="comfortable"
              class="rounded-lg attendees-table"
            >
              <thead>
                <tr>
                  <th class="w-1/3">
                    Name
                  </th>
                  <th class="w-1/3">
                    Discord
                  </th>
                  <th class="w-1/3">
                    Email
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="esp in participants"
                  :key="esp.person.discord + esp.person.email"
                >
                  <td>{{ esp.person.fullName }}</td>
                  <td class="font-mono">
                    {{ esp.person.discord }}
                  </td>
                  <td class="font-mono">
                    {{ esp.person.email }}
                  </td>
                </tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-card>

        <template v-if="radioQuestions.length">
          <h2 class="text-h5 mb-4">
            Radio questions
          </h2>

          <v-card
            v-for="rq in radioQuestions"
            :key="qKey(rq)"
            class="mb-8"
          >
            <v-card-title class="text-h6">
              {{ rq.label }}
            </v-card-title>

            <v-card-text>
              <v-table
                density="comfortable"
                class="rounded-lg radio-table"
              >
                <thead>
                  <tr>
                    <th class="sticky-col">
                      User
                    </th>
                    <th
                      v-for="(opt, idx) in rq.choiceLabels"
                      :key="idx"
                      class="text-center"
                    >
                      {{ opt }}
                    </th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="esp in participants"
                    :key="qKey(rq) + '-' + esp.person.email"
                  >
                    <td class="sticky-col">
                      {{ esp.person.fullName }}
                    </td>
                    <td
                      v-for="(_, idx) in rq.choiceLabels"
                      :key="idx"
                      class="text-center check-cell"
                    >
                      <v-icon
                        v-if="selectionsFor(esp, qKey(rq) as number, rq.choiceLabels?.length ?? 0)[idx]"
                        icon="mdi-check-bold"
                        size="18"
                      />
                    </td>
                  </tr>
                </tbody>

                <tfoot>
                  <tr>
                    <td class="font-weight-bold sticky-col">
                      Totals
                    </td>
                    <td
                      v-for="(_, idx) in rq.choiceLabels"
                      :key="'t-' + idx"
                      class="text-center font-weight-bold"
                    >
                      {{ radioTotals[qKey(rq) as number]?.[idx] ?? 0 }}
                    </td>
                  </tr>
                </tfoot>
              </v-table>
            </v-card-text>
          </v-card>
        </template>

        <template v-if="openQuestions.length">
          <h2 class="text-h5 mb-4">
            Open questions
          </h2>

          <v-card
            v-for="oq in openQuestions"
            :key="qKey(oq)"
            class="mb-8"
          >
            <v-card-title class="text-h6">
              {{ oq.label }}
            </v-card-title>

            <v-card-text>
              <v-table
                density="comfortable"
                class="rounded-lg open-table"
              >
                <thead>
                  <tr>
                    <th class="w-1/4">
                      User
                    </th>
                    <th>Answer</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="esp in participants"
                    :key="qKey(oq) + '-' + esp.person.email"
                  >
                    <td>{{ esp.person.fullName }}</td>
                    <td class="whitespace-pre-wrap">
                      {{ openTextFor(esp, qKey(oq) as number) }}
                    </td>
                  </tr>
                </tbody>
              </v-table>
            </v-card-text>
          </v-card>
        </template>
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.attendees-table, .radio-table, .open-table {
  thead th {
    position: sticky;
    top: 0;
    background: rgb(var(--v-theme-surface));
    z-index: 2;
  }
}

.radio-table .check-cell {
  width: 64px;
}

.sticky-col {
  position: sticky;
  left: 0;
  z-index: 1;
  background: rgb(var(--v-theme-surface));
}

.whitespace-pre-wrap {
  white-space: pre-wrap;
}

/* Subtle zebra striping */
tbody tr:nth-child(odd) {
  background: rgba(0, 0, 0, 0.02);
}
</style>
