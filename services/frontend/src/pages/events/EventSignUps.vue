<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {
  type AnswerResponse,
  type EventResponse,
  type EventSignUpResponse,
  findEventById,
  findEventSignUpsByEventId,
  type QuestionResponse,
  QuestionType,
  type SurveyResponse,
} from "@/services/api"

const event = ref<EventResponse>()

type PersonInfo = {
  fullName: string;
  discord?: string;
  email?: string;
  phoneNumber?: string;
}

export type Response = {
  answers: Map<number, AnswerResponse>,
  person: PersonInfo;
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

    responses.value = (signupsResp.data ?? []).map((es: EventSignUpResponse) => {
      const answers: Map<number, AnswerResponse> = new Map()
      es.answers?.forEach((answer: AnswerResponse) => {
        answers.set(answer.questionId, answer)
      })

      const person: PersonInfo = es.user
        ? {
          fullName: es.user.fullName,
          discord: es.user.discord,
          email: es.user.email,
          phoneNumber: es.user.phoneNumber,
        }
        : {
          fullName: es.guest?.name ?? "",
          discord: es.guest?.discord,
          email: es.guest?.email,
          phoneNumber: es.guest?.phoneNumber,
        }

      return {
        answers,
        person,
      } as Response
    })
    event.value = eventResp.data
  } catch (err) {
    console.error(err)
  }
})

const sortedQuestions = computed<QuestionResponse[]>(() => {
  const sf: SurveyResponse | undefined = event.value?.signUpForm
  if (!sf?.questions?.length) return []
  return [...sf.questions].sort((a: QuestionResponse, b: QuestionResponse) => a.idx - b.idx)
})

function totalForQuestion(question: QuestionResponse): number[] | undefined {
  if (!question) return
  if (question.type === QuestionType.CHECKBOX || question.type === QuestionType.RADIO) {
    const numOptions = question.choiceLabels?.length ?? 0
    const counts = Array.from({length: numOptions}, () => 0)

    responses.value.forEach((r: Response) => {
      const selections: boolean[] = r.answers.get(question.id!)?.optionSelections ?? []
      for (let i = 0; i < numOptions; i++) {
        if (selections[i]) {
          counts[i] = (counts[i] ?? 0) + 1
        }
      }
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
                  <th class="w-1/10">
                    #
                  </th>
                  <th class="w-3/10">
                    Name
                  </th>
                  <th class="w-2/10">
                    Discord
                  </th>
                  <th class="w-2/10">
                    Email
                  </th>
                  <th class="w-2/10">
                    Phone
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(response, idx) in responses"
                  :key="response.person.discord + response.person.email"
                >
                  <td>{{ idx + 1 }}</td>
                  <td>{{ response.person?.fullName }}</td>
                  <td class="font-mono">
                    {{ response.person?.discord }}
                  </td>
                  <td class="font-mono">
                    {{ response.person?.email }}
                  </td>
                  <td class="font-mono">
                    {{ response.person?.phoneNumber }}
                  </td>
                </tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-card>

        <v-card
          v-for="question in sortedQuestions"
          :key="question.id!"
          class="mb-8"
        >
          <v-card-title
            class="text-h6 text-wrap flex items-center gap-2"
            style="word-break: break-word"
          >
            <span>{{ question.idx + 1 }}. {{ question.label }}</span>
          </v-card-title>

          <v-card-text>
            <!-- OPEN questions -->
            <v-table
              v-if="question.type === QuestionType.OPEN"
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
                  :key="question.id! + '-' + response.person.email"
                >
                  <td>{{ response.person.fullName }}</td>
                  <td class="whitespace-pre-wrap">
                    {{ response.answers.get(question.id!)?.textResponse }}
                  </td>
                </tr>
              </tbody>
            </v-table>

            <!-- Choice questions: RADIO or CHECKBOX -->
            <v-table
              v-else
              class="rounded-lg radio-table"
              density="compact"
            >
              <thead>
                <tr>
                  <th class="sticky-col">
                    Name
                  </th>
                  <th
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="idx"
                    class="text-center choice-col"
                  >
                    <v-tooltip
                      :text="opt"
                      location="bottom"
                    >
                      <template #activator="{ props }">
                        <span
                          v-bind="props"
                          class="choice-label"
                        >{{ opt }}</span>
                      </template>
                    </v-tooltip>
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
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="idx"
                    class="text-center check-cell"
                  >
                    <v-icon
                      v-if="(response.answers.get(question.id!)?.optionSelections ?? [])[idx]"
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
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="'t-' + idx"
                    class="text-center font-weight-bold"
                  >
                    {{ (totalForQuestion(question) ?? [])[idx] ?? 0 }}
                  </td>
                </tr>
              </tfoot>
            </v-table>
          </v-card-text>
        </v-card>
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

.radio-table .check-cell,
.radio-table .choice-col {
  width: 96px;
  max-width: 96px;
  overflow: hidden;
}

.choice-label {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  cursor: default;
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

tbody tr:nth-child(odd) {
  background: rgba(0, 0, 0, 0.02);
}
</style>
