<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import RoleMark from "@/components/island/RoleMark.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import PersonLink from "@/components/management/PersonLink.vue"
import {formatMoment} from "@/utils/timestamps"
import {
  listRoleChanges,
  readRoleStanding,
  type Role,
  type RoleChange,
  RoleSource,
  type RoleStanding,
  saveRolesOrReason,
} from "@/domains/user"

defineOptions({name: "UserRolesPanel"})

/**
 * What one person may reach, and every change they have been through.
 *
 * The tick boxes are drawn from the set the api says it will accept, so a role that stops being
 * assignable stops being offered without an edit here. A derived role is shown read-only beside
 * the record that owns it, because granting one is a lie the listener would undo (ADR-028).
 */
const props = defineProps<{
  userId: number
  /** Only an admin adds or removes a role; everybody else reads them. */
  editable: boolean
}>()

const emit = defineEmits<{
  changed: [standing: RoleStanding]
}>()

const standing = ref<RoleStanding | null>(null)
const history = ref<RoleChange[]>([])
const chosen = ref<Role[]>([])
const note = ref("")
const loading = ref(false)
const saving = ref(false)
const failure = ref<string | null>(null)
const loadFailure = ref<string | null>(null)

const sourceLabels: Record<string, string> = {
  [RoleSource.ACCOUNT]: "every account has it",
  [RoleSource.MEMBERSHIP]: "from their membership",
  [RoleSource.COMMITTEE_SEAT]: "from their committee seat",
  [RoleSource.GRANT]: "added by an admin",
}

const HISTORY: TableColumn[] = [
  {key: "when", label: "When"},
  {key: "change", label: "Change", wrap: true},
  {key: "by", label: "By"},
  {key: "why", label: "Why", wrap: true},
]

const dirty = computed(() => {
  const before = [...(standing.value?.granted ?? [])].sort()
  const now = [...chosen.value].sort()
  return before.length !== now.length || before.some((role, index) => role !== now[index])
})

async function load(): Promise<void> {
  loading.value = true
  failure.value = null
  loadFailure.value = null
  const [roles, changes] = await Promise.all([
    readRoleStanding(props.userId),
    listRoleChanges(props.userId),
  ])
  if (!roles) {
    loadFailure.value = "These roles could not be read."
  } else {
    standing.value = roles
    chosen.value = [...roles.granted]
  }
  history.value = changes ?? []
  loading.value = false
}

async function save(): Promise<void> {
  if (saving.value) return
  saving.value = true
  failure.value = null
  try {
    const result = await saveRolesOrReason(props.userId, [...chosen.value], note.value.trim() || null)
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    standing.value = result.saved
    chosen.value = [...result.saved.granted]
    note.value = ""
    history.value = (await listRoleChanges(props.userId)) ?? history.value
    emit("changed", result.saved)
  } finally {
    saving.value = false
  }
}

function label(role: Role): string {
  return `${role}`.toLocaleLowerCase()
}

/** A role as its mark writes it: "Board". */
const named = (role: Role) => `${role}`.charAt(0) + label(role).slice(1)

const tick = (role: Role, on: boolean) => {
  chosen.value = on ? [...chosen.value, role] : chosen.value.filter((one) => one !== role)
}

watch(() => props.userId, load, {immediate: true})
</script>

<template>
  <section
    class="roles-panel"
    data-testid="user-roles-panel"
  >
    <p
      v-if="loadFailure"
      class="roles-panel__failure"
      data-testid="user-roles-load-failure"
      role="alert"
    >
      {{ loadFailure }}
    </p>
    <p
      v-if="loading"
      class="roles-panel__note"
      role="status"
    >
      Reading the roles.
    </p>

    <template v-if="standing">
      <list-head title="Roles an admin adds" />
      <div class="roles-panel__ticks">
        <span
          v-for="role in standing.assignable"
          :key="role"
          :data-testid="`user-roles-checkbox-${label(role)}`"
        >
          <check-box
            :disabled="!editable"
            :hint="standing.dormant?.includes(role) ? 'Dormant' : ''"
            :label="named(role)"
            :model-value="chosen.includes(role)"
            @update:model-value="tick(role, $event)"
          />
        </span>
      </div>
      <p
        v-if="standing.dormant?.length"
        class="roles-panel__note"
        data-testid="user-roles-dormant"
      >
        A dormant role allows nothing until this person sets up two-factor authentication.
      </p>

      <list-head title="Roles that follow a record" />
      <ul
        v-if="standing.derived.length"
        class="roles-panel__marks"
      >
        <li
          v-for="entry in standing.derived"
          :key="entry.role"
          :data-testid="`user-roles-derived-${label(entry.role)}`"
        >
          <role-mark :role="named(entry.role)" />
          <span>{{ sourceLabels[entry.source] ?? entry.source }}</span>
        </li>
      </ul>
      <p
        v-else
        class="roles-panel__note"
      >
        None.
      </p>

      <template v-if="standing.implied.length">
        <list-head title="Roles that come with the ones above" />
        <ul class="roles-panel__marks">
          <li
            v-for="role in standing.implied"
            :key="role"
            :data-testid="`user-roles-implied-${label(role)}`"
          >
            <role-mark :role="named(role)" />
          </li>
        </ul>
      </template>

      <form
        v-if="editable"
        class="roles-panel__save"
        @submit.prevent="save"
      >
        <form-field
          v-slot="field"
          label="Why (optional)"
          testid="user-roles-note"
        >
          <text-input
            v-model="note"
            :control-id="field.controlId"
            maxlength="1023"
          />
        </form-field>
        <cut-button
          :disabled="!dirty || saving"
          submit
          testid="user-roles-save-btn"
          tone="solid"
        >
          {{ saving ? "Saving" : "Save roles" }}
        </cut-button>
      </form>

      <p
        v-if="failure"
        class="roles-panel__failure"
        data-testid="user-roles-failure"
        role="alert"
      >
        {{ failure }}
      </p>

      <list-head title="History" />
      <p
        v-if="!history.length"
        class="roles-panel__note"
        data-testid="user-roles-history-empty"
      >
        No role has been changed here yet.
      </p>
      <management-table
        v-else
        :columns="HISTORY"
        :row-key="(change) => change.id"
        :row-testid="(change) => `user-roles-history-${change.id}`"
        search-label="Search changes"
        :search-text="(change) => `${change.actorName} ${change.note ?? ''} ${[...change.before, ...change.after].join(' ')}`"
        :rows="history"
        testid="user-roles-history"
      >
        <template #when="{row}">
          {{ formatMoment(row.changedAt) }}
        </template>
        <template #change="{row}">
          {{ row.before.map(named).join(", ") || "Nothing" }} to {{ row.after.map(named).join(", ") || "nothing" }}
        </template>
        <template #by="{row}">
          <person-link
            :name="row.actorName"
            :user-id="row.actorId"
          />
        </template>
        <template #why="{row}">
          <span class="mg-quiet">{{ row.note }}</span>
        </template>
      </management-table>
    </template>
  </section>
</template>

<style scoped>
.roles-panel {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.roles-panel__ticks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem 2rem;
}

.roles-panel__note {
  font-size: 0.88rem;
  color: var(--color-ash);
}

.roles-panel__failure {
  color: var(--color-danger);
}

.roles-panel__marks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem 1.6rem;
}

.roles-panel__marks li {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}

.roles-panel__save {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  gap: 0.8rem 1.2rem;
  margin-top: 0.6rem;
}

.roles-panel__save :deep(.island-field) {
  flex: 1 1 20rem;
  max-width: 36rem;
}
</style>
