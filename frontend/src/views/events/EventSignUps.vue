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
  type Survey,
} from "@/lib"

const event = ref<Event>()
const eventSignUps = ref<EventSignUp[]>([])

export type Response = {
  answers: Map<number, Answer>,
  person: PersonalInfo;
};
const responses = ref<Response[]>([])

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

    responses.value = eventSignUps.value.map((es: EventSignUp) => {
      const answers: Map<number, Answer> = new Map()
      es.answers?.forEach((answer: Answer) => {
        answers.set(answer.questionId, answer)
      })

      return {
        answers,
        person: (es.guest ?? es.user)! as PersonalInfo,
      } as Response
    })
  } catch (err) {
    console.error(err)
  }
})

const allQuestions = computed<Question[]>(() => {
  const sf: Survey | undefined = event.value?.signUpForm
  if (!sf) return []
  return sf.questions
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
  console.log("radio questions:", radioQuestions)
})
watch(allQuestions, (allQuestions: Question[]) => {
  console.log("all questions: ", allQuestions)
})

watch(responses, (responses: Response[]) => {
  console.log("responses ", responses)
})

function totalForQuestion(question: Question): number[] | undefined {
  if (!question) return

  const answers: Answer[] = responses.value.flatMap((r: Response) =>
    Array.from(r.answers.values())
      .filter((a: Answer) => a.questionId === question.id),
  )

  if (question.type === QuestionType.CHECKBOX || question.type === QuestionType.RADIO) {
    const selections: boolean[][] = answers.map(a => a.optionSelections ?? [])
    const maxOptions = Math.max(...selections.map(arr => arr.length), 0)
    const counts = new Array(maxOptions).fill(0)

    selections.forEach(arr => {
      arr.forEach((val, i) => {
        if (val) counts[i] += 1
      })
    })

    return counts
  }
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
        <v-card class="mb-10">
          <v-card-title class="text-h5">
            Respondents
          </v-card-title>
          <v-card-text>
            <v-table
              class="rounded-lg attendees-table"
              density="comfortable"
            >
              <thead>
                <tr>
                  <th
                    class="w-1/10"
                  >
                    #
                  </th>
                  <th class="w-3/10">
                    Name
                  </th>
                  <th class="w-3/10">
                    Discord
                  </th>
                  <th class="w-3/10">
                    Email
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(response, idx) in responses"
                  :key="response.person.discord + response.person.email"
                >
                  <td>{{ idx + 1 }}</td>
                  <td>{{ response.person.fullName }}</td>
                  <td class="font-mono">
                    {{ response.person.discord }}
                  </td>
                  <td class="font-mono">
                    {{ response.person.email }}
                  </td>
                </tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-card>

        <template v-if="radioQuestions.length">
          <h2 class="text-h5">
            Multiple choice questions
          </h2>
          <v-divider class="mt-1 mb-3" />

          <v-card
            v-for="question in radioQuestions"
            :key="question.id!"
            class="mb-8"
          >
            <v-card-title
              class="text-h6 text-wrap"
              style="word-break: break-word"
            >
              {{ question.label }}
            </v-card-title>

            <v-card-text>
              <v-table
                class="rounded-lg radio-table"
                density="comfortable"
              >
                <thead>
                  <tr>
                    <th class="sticky-col">
                      Name
                    </th>
                    <th
                      v-for="(opt, idx) in question.choiceLabels"
                      :key="idx"
                      class="text-center"
                    >
                      {{ opt }}
                    </th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="response in responses"
                    :key="question.id! + '-' + response.person.email"
                  >
                    <td class="sticky-col">
                      {{ response.person.fullName }}
                    </td>
                    <td
                      v-for="(selection, idx) in response.answers.get(question.id).optionSelections"
                      :key="idx"
                      class="text-center check-cell"
                    >
                      <v-icon
                        v-if="selection"
                        icon="mdi-check-bold"
                        size="18"
                      />
                      <v-icon
                        v-else
                        icon="mdi-close-thick"
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
                      v-for="(val, idx) in totalForQuestion(question)"
                      :key="'t-' + idx"
                      class="text-center font-weight-bold"
                    >
                      {{ val ?? 0 }}
                    </td>
                  </tr>
                </tfoot>
              </v-table>
            </v-card-text>
          </v-card>
        </template>

        <template v-if="checkboxQuestions.length">
          <h2 class="text-h5">
            Questions with checkboxes
          </h2>
          <v-divider class="mt-1 mb-3" />

          <v-card
            v-for="question in checkboxQuestions"
            :key="question.id!"
            class="mb-8"
          >
            <v-card-title
              class="text-h6 text-wrap"
              style="word-break: break-word"
            >
              {{ question.label }}
            </v-card-title>

            <v-card-text>
              <v-table
                class="rounded-lg radio-table"
                density="comfortable"
              >
                <thead>
                  <tr>
                    <th class="sticky-col">
                      Name
                    </th>
                    <th
                      v-for="(opt, idx) in question.choiceLabels"
                      :key="idx"
                      class="text-center"
                    >
                      {{ opt }}
                    </th>
                  </tr>
                </thead>

                <tbody>
                  <tr
                    v-for="response in responses"
                    :key="question.id! + '-' + response.person.email"
                  >
                    <td class="sticky-col">
                      {{ response.person.fullName }}
                    </td>
                    <td
                      v-for="(selection, idx) in response.answers.get(question.id).optionSelections"
                      :key="idx"
                      class="text-center check-cell"
                    >
                      <v-icon
                        v-if="selection"
                        icon="mdi-check-bold"
                        size="18"
                      />
                      <v-icon
                        v-else
                        icon="mdi-close-thick"
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
                      v-for="(val, idx) in totalForQuestion(question)"
                      :key="'t-' + idx"
                      class="text-center font-weight-bold"
                    >
                      {{ val ?? 0 }}
                    </td>
                  </tr>
                </tfoot>
              </v-table>
            </v-card-text>
          </v-card>
        </template>

        <template v-if="openQuestions.length">
          <h2 class="text-h5">
            Open questions
          </h2>
          <v-divider class="mt-1 mb-3" />

          <v-card
            v-for="question in openQuestions"
            :key="question.id"
            class="mb-8"
          >
            <v-card-title
              class="text-h6 text-wrap"
              style="word-break: break-word"
            >
              {{ question.label }}
            </v-card-title>

            <v-card-text>
              <v-table
                class="rounded-lg open-table"
                density="comfortable"
              >
                <thead>
                  <tr>
                    <th class="w-1/4">
                      Name
                    </th>
                    <th>Answer</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="response in responses"
                    :key="question.id + '-' + response.person.email"
                  >
                    <td>{{ response.person.fullName }}</td>
                    <td class="whitespace-pre-wrap">
                      {{ response.answers.get(question.id)?.textResponse }}
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
