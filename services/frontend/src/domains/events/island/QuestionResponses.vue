<script lang="ts" setup>
import {computed} from "vue"
import {type QuestionResponse, QuestionType} from ".."
import type {RosterRow} from "./SignUpRoster.vue"

/**
 * What everybody answered to one question. An open question lists each answer, with "not yet
 * answered" for the rest; a choice question shows a tally beside the grid of ticks, with the
 * totals under it.
 */
defineOptions({name: "QuestionResponses"})

const {question, rows} = defineProps<{
  question: QuestionResponse
  rows: RosterRow[]
}>()

const open = computed(() => question.type === QuestionType.OPEN)
const choices = computed<string[]>(() => question.choiceLabels ?? [])

type Mark = "chosen" | "not-chosen" | "missing"

/* An answer written before the question had these options answers a different question. */
const markOf = (row: RosterRow, at: number): Mark => {
  const selections = row.answers.get(question.id)?.optionSelections
  if (!selections || selections.length !== choices.value.length) return "missing"
  return selections[at] ? "chosen" : "not-chosen"
}

const answered = computed(() => rows.filter(row => (open.value
  ? (row.answers.get(question.id)?.textResponse ?? "").trim() !== ""
  : markOf(row, 0) !== "missing")).length)

const totals = computed<number[]>(() =>
  choices.value.map((_, at) => rows.filter(row => markOf(row, at) === "chosen").length))

/* Each option's bar, as a share of the most chosen one. */
const widths = computed<number[]>(() => {
  const most = Math.max(1, ...totals.value)
  return totals.value.map(total => (total / most) * 100)
})

const kindSaid = computed(() => (question.type === QuestionType.RADIO ? "Pick one · " : open.value ? "" : "Pick any · "))

/** What one person wrote, or why there is nothing to read. */
const saidBy = (row: RosterRow): {text: string, none: boolean} => {
  const answer = row.answers.get(question.id)
  if (!answer) return {text: "not yet answered", none: true}
  const text = answer.textResponse?.trim() ?? ""
  return text === "" ? {text: "left blank", none: true} : {text: answer.textResponse as string, none: false}
}

const MISSING = "No answer yet: this person has not edited their sign-up since the question was added"
</script>

<template>
  <div
    class="question"
    :data-testid="`signups-question-${question.id}`"
  >
    <div class="question__head">
      <h3 class="question__title">
        {{ question.idx + 1 }}. {{ question.label }}
      </h3>
      <span class="question__meta">{{ kindSaid }}{{ answered }} of {{ rows.length }} answered</span>
    </div>

    <div
      v-if="open"
      class="question__said open-table"
    >
      <div
        v-for="row in rows"
        :key="row.signUp.id"
        class="question__said-row"
      >
        <span class="question__who">{{ row.person.name }}</span>
        <span
          :class="{'question__none': saidBy(row).none}"
        >{{ saidBy(row).text }}</span>
      </div>
    </div>

    <div
      v-else
      class="question__split"
    >
      <div class="question__tally">
        <div
          v-for="(choice, at) in choices"
          :key="choice"
          class="question__tally-row"
        >
          <span class="question__choice">{{ choice }}</span>
          <span class="question__bar"><span :style="{width: `${widths[at]}%`}" /></span>
          <span class="question__count">{{ totals[at] }}</span>
        </div>
        <p
          v-if="rows.length - answered > 0"
          class="question__rest"
        >
          {{ rows.length - answered }} not yet answered
        </p>
      </div>

      <div class="question__matrix">
        <table class="question__table radio-table">
          <thead>
            <tr>
              <th>Name</th>
              <th
                v-for="choice in choices"
                :key="choice"
              >
                {{ choice }}
              </th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in rows"
              :key="row.signUp.id"
            >
              <td class="question__name">
                {{ row.person.name }}
              </td>
              <td
                v-for="(choice, at) in choices"
                :key="choice"
              >
                <span
                  class="question__mark"
                  :class="`question__mark--${markOf(row, at)}`"
                  :data-answer="markOf(row, at)"
                  :title="markOf(row, at) === 'missing' ? MISSING : undefined"
                >
                  <svg
                    v-if="markOf(row, at) === 'chosen'"
                    aria-label="Chosen"
                    fill="none"
                    role="img"
                    stroke="currentColor"
                    stroke-width="2"
                    viewBox="0 0 16 16"
                  ><path d="M3 8.5 6.5 12 13 4.5" /></svg>
                  <svg
                    v-else-if="markOf(row, at) === 'not-chosen'"
                    aria-label="Not chosen"
                    fill="none"
                    role="img"
                    stroke="currentColor"
                    stroke-width="1.6"
                    viewBox="0 0 16 16"
                  ><path d="M4.5 4.5l7 7M11.5 4.5l-7 7" /></svg>
                  <template v-else>–</template>
                </span>
              </td>
            </tr>
          </tbody>
          <tfoot>
            <tr>
              <td>Totals</td>
              <td
                v-for="(choice, at) in choices"
                :key="choice"
              >
                {{ totals[at] }}
              </td>
            </tr>
          </tfoot>
        </table>
      </div>
    </div>
  </div>
</template>

<style scoped>
.question__head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 1rem;
  margin-bottom: 0.8rem;
}

.question__title {
  font-family: var(--font-display);
  font-size: 1.1rem;
  text-transform: uppercase;
  overflow-wrap: break-word;
}

.question__meta {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.question__said {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.question__said-row {
  display: grid;
  grid-template-columns: 13rem minmax(0, 1fr);
  gap: 1rem;
  padding: 0.65rem 0.9rem 0.65rem 1.4rem;
  font-size: 0.9rem;
  white-space: pre-wrap;
  background-color: var(--band-ground);
}

.question__who {
  color: var(--color-ash);
}

.question__none {
  font-style: italic;
  color: var(--color-ash);
}

.question__split {
  display: grid;
  grid-template-columns: 22rem minmax(0, 1fr);
  gap: 2.5rem;
  align-items: start;
}

.question__tally {
  display: flex;
  flex-direction: column;
  gap: 0.7rem;
  padding-top: 0.4rem;
}

.question__tally-row {
  display: grid;
  grid-template-columns: 7.5rem minmax(0, 1fr) 1.8rem;
  gap: 0.8rem;
  align-items: center;
  font-size: 0.9rem;
}

.question__choice {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.question__bar {
  position: relative;
  height: 0.8rem;
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.question__bar > span {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand);
}

.question__count {
  font-family: var(--font-display);
  text-align: right;
}

.question__rest {
  font-size: 0.82rem;
  color: var(--color-ash);
}

.question__matrix {
  overflow-x: auto;
}

.question__table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0 2px;
}

.question__table th {
  max-width: 7rem;
  padding: 0.5rem 0.9rem;
  overflow: hidden;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.2em;
  text-align: center;
  text-overflow: ellipsis;
  text-transform: uppercase;
  color: var(--color-ash);
  white-space: nowrap;
}

.question__table td {
  padding: 0.55rem 0.9rem;
  font-size: 0.9rem;
  text-align: center;
  white-space: nowrap;
  background-color: var(--band-ground);
}

.question__table th:first-child,
.question__table td:first-child {
  text-align: left;
}

.question__name {
  font-weight: 600;
}

.question__table tfoot td {
  font-family: var(--font-display);
  background: none;
}

.question__mark {
  display: inline-grid;
  place-items: center;
  width: 1.2rem;
  height: 1.2rem;
}

.question__mark svg {
  width: 15px;
  height: 15px;
}

.question__mark--chosen {
  color: var(--color-ok);
}

.question__mark--not-chosen {
  color: color-mix(in oklab, var(--color-ash) 70%, transparent);
}

.question__mark--missing {
  color: var(--color-ash);
}

@media (max-width: 767px) {
  .question__said-row {
    grid-template-columns: 1fr;
    gap: 0.15rem;
  }

  .question__split {
    grid-template-columns: 1fr;
    gap: 1.25rem;
  }

  .question__tally-row {
    grid-template-columns: 6rem minmax(0, 1fr) 1.6rem;
  }
}
</style>
