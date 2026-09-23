<script lang="ts">
import type {SignUpPerson, SignUpRow} from "@/utils/eventSignUpRows"

/** A sign-up with the person it is for read off it, which is what a row draws. */
export type RosterRow = SignUpRow & {person: SignUpPerson}
</script>

<script lang="ts" setup>
import IconButton from "@/components/island/IconButton.vue"
import {isSignUpEditable, signUpKindLabel} from "@/utils/eventSignUpRows"

/**
 * Who signed up, as flat rows: their number, name, kind, Discord, email and phone, and for the
 * board a way to edit or remove each. On a phone each row folds into a card; the table stays
 * one table, so nothing on it is drawn twice.
 */
defineOptions({name: "SignUpRoster"})

const {rows, mayManage = false, hasForm = false, sortSaid, sortOn} = defineProps<{
  rows: RosterRow[]
  mayManage?: boolean
  /** Whether the event asks questions, which is what there is to edit on an account's sign-up. */
  hasForm?: boolean
  /** Which order the kind column is in, said as the words the header's name ends on. */
  sortSaid: string
  sortOn: boolean
}>()

const emit = defineEmits<{sort: []; edit: [row: RosterRow]; remove: [row: RosterRow]}>()

const KIND_TONE: Record<string, string> = {MEMBER: "", NON_MEMBER: "roster__kind--quiet", GUEST: "roster__kind--guest"}
</script>

<template>
  <table class="roster attendees-table">
    <thead>
      <tr>
        <th>#</th>
        <th>Name</th>
        <th>
          <button
            :aria-label="`Sort by kind, ${sortSaid}`"
            class="roster__sort"
            :class="{'roster__sort--on': sortOn}"
            data-testid="signups-kind-sort"
            type="button"
            @click="emit('sort')"
          >
            Kind
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 16 16"
            ><path d="M5 6.5 8 3.5l3 3M5 9.5l3 3 3-3" /></svg>
          </button>
        </th>
        <th>Discord</th>
        <th>Email</th>
        <th>Phone</th>
        <th v-if="mayManage">
          Actions
        </th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="(row, index) in rows"
        :key="row.signUp.id"
      >
        <td class="roster__number">
          {{ index + 1 }}
        </td>
        <td class="roster__name">
          {{ row.person.name }}
        </td>
        <td class="roster__kind-cell">
          <span
            class="roster__kind"
            :class="KIND_TONE[row.signUp.kind]"
            :data-testid="`signup-kind-${row.signUp.id}`"
          >{{ signUpKindLabel(row.signUp.kind) }}</span>
        </td>
        <td class="roster__muted">
          {{ row.person.discord }}
        </td>
        <td>{{ row.person.email }}</td>
        <td class="roster__muted">
          {{ row.person.phoneNumber }}
        </td>
        <td
          v-if="mayManage"
          class="roster__acts"
        >
          <icon-button
            :disabled="!isSignUpEditable(row.signUp, hasForm)"
            :label="isSignUpEditable(row.signUp, hasForm) ? 'Edit sign-up' : 'Nothing to edit: the event asks nothing, and an account holds its own details'"
            :testid="`signup-edit-btn-${row.signUp.id}`"
            @click="emit('edit', row)"
          >
            <svg
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" /></svg>
          </icon-button>
          <icon-button
            danger
            label="Remove sign-up"
            :testid="`signup-remove-btn-${row.signUp.id}`"
            @click="emit('remove', row)"
          >
            <svg
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M5 7h14M10 7V4h4v3M7 7l1 13h8l1-13" /></svg>
          </icon-button>
        </td>
      </tr>
    </tbody>
  </table>
</template>

<style scoped>
.roster {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0 2px;
}

.roster th {
  padding: 0.5rem 0.9rem;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-align: left;
  text-transform: uppercase;
  color: var(--color-ash);
  white-space: nowrap;
}

.roster td {
  padding: 0.65rem 0.9rem;
  font-size: 0.9rem;
  color: var(--color-chalk);
  white-space: nowrap;
  background-color: var(--band-ground);
  transition: background-color 220ms ease;
}

.roster tbody tr:hover td {
  background-color: color-mix(in oklab, var(--color-surface) 94%, transparent);
}

.roster__number {
  position: relative;
  padding-left: 1.4rem !important;
  font-size: 0.8rem !important;
  color: var(--color-ash) !important;
}

.roster__number::before {
  content: "";
  position: absolute;
  top: 0.55rem;
  bottom: 0.55rem;
  left: 0.5rem;
  width: 3px;
  background: var(--color-brand);
  transform: skewX(-12deg);
  scale: 1 0;
  transition: scale 320ms var(--ease-out-quint);
}

.roster tbody tr:hover .roster__number::before {
  scale: 1 1;
}

.roster__name {
  font-weight: 600;
}

.roster__muted {
  color: var(--color-ash) !important;
}

.roster__sort {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0;
  font: inherit;
  letter-spacing: inherit;
  text-transform: inherit;
  color: inherit;
  cursor: pointer;
  background: none;
  border: 0;
}

.roster__sort:hover,
.roster__sort--on {
  color: var(--color-chalk);
}

.roster__sort svg {
  width: 13px;
  height: 13px;
}

.roster__kind {
  display: inline-flex;
  padding: 0.2rem 0.5rem;
  font-size: 0.64rem;
  font-weight: 600;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
  white-space: nowrap;
  border: 1px solid currentColor;
}

.roster__kind--quiet {
  color: var(--color-ash);
}

.roster__kind--guest {
  color: var(--color-ash);
  border-style: dashed;
}

.roster__acts {
  padding-top: 0.2rem !important;
  padding-bottom: 0.2rem !important;
  text-align: right;
}

.roster__acts :deep(.icon-button) {
  display: inline-grid;
}

@media (prefers-reduced-motion: reduce) {
  .roster td,
  .roster__number::before {
    transition: none;
  }
}

/* On a phone a row is a card: name and kind, then each way to reach them, the actions beside. */
@media (max-width: 767px) {
  .roster thead th:not(:nth-child(3)) {
    display: none;
  }

  .roster thead tr {
    display: flex;
    justify-content: flex-end;
  }

  .roster tbody tr {
    display: grid;
    grid-template-columns: minmax(0, 1fr) auto;
    column-gap: 0.75rem;
    margin-bottom: 2px;
    padding: 0.6rem 0.6rem 0.7rem 1.1rem;
    background-color: var(--band-ground);
  }

  .roster td {
    grid-column: 1;
    padding: 0.05rem 0 !important;
    overflow: hidden;
    text-overflow: ellipsis;
    background: none !important;
  }

  .roster__number {
    display: none;
  }

  .roster__kind-cell {
    padding-bottom: 0.25rem !important;
  }

  .roster__acts {
    grid-column: 2;
    grid-row: 1 / span 5;
    align-self: start;
  }
}
</style>
