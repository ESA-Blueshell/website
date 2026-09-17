<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import {safeFormatISO} from "@/utils/datetime"
import {
  listRoleChanges,
  readRoleStanding,
  type Role,
  type RoleChange,
  RoleSource,
  type RoleStanding,
  saveRolesOrReason,
} from "@/domains/user"

defineOptions({name: "UserRolesDialog"})

/**
 * What one person may reach, and every change they have been through.
 *
 * The tick boxes are drawn from the set the api says it will accept, so a role that stops being
 * assignable stops being offered without an edit here. A derived role is shown read-only beside
 * the record that owns it, because granting one is a lie the listener would undo (ADR-028).
 */
const props = defineProps<{
  modelValue: boolean
  userId: number
  userName: string
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
  changed: [standing: RoleStanding]
}>()

const open = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
})

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
  [RoleSource.GRANT]: "granted",
}

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
    standing.value = result.standing
    chosen.value = [...result.standing.granted]
    note.value = ""
    history.value = (await listRoleChanges(props.userId)) ?? history.value
    emit("changed", result.standing)
  } finally {
    saving.value = false
  }
}

function label(role: Role): string {
  return `${role}`.toLocaleLowerCase()
}

watch(
  () => [props.modelValue, props.userId] as const,
  ([isOpen]) => {
    if (isOpen) void load()
  },
  {immediate: true},
)
</script>

<template>
  <base-modal
    v-model="open"
    :title="`Roles — ${userName}`"
    testid="user-roles-dialog"
    show-save
    save-label="Save roles"
    save-testid="user-roles-save-btn"
    save-icon="mdi-content-save"
    :save-loading="saving"
    :save-disabled="!standing || !dirty"
    show-cancel
    cancel-label="Close"
    @save="save"
    @cancel="open = false"
  >
    <v-alert
      v-if="loadFailure"
      class="mb-4"
      data-testid="user-roles-load-failure"
      type="error"
      variant="tonal"
    >
      {{ loadFailure }}
    </v-alert>

    <v-progress-linear
      v-if="loading"
      indeterminate
    />

    <template v-if="standing">
      <div class="mb-2 text-subtitle-2">
        Roles an admin grants
      </div>
      <v-checkbox
        v-for="role in standing.assignable"
        :key="role"
        v-model="chosen"
        class="text-capitalize"
        :data-testid="`user-roles-checkbox-${label(role)}`"
        hide-details
        :label="label(role)"
        :value="role"
      />

      <div class="mb-2 mt-4 text-subtitle-2">
        Roles that follow a record
      </div>
      <div
        v-if="standing.derived.length"
        class="d-flex flex-wrap ga-2"
      >
        <v-chip
          v-for="entry in standing.derived"
          :key="entry.role"
          :data-testid="`user-roles-derived-${label(entry.role)}`"
          size="small"
          variant="flat"
        >
          <span class="text-capitalize">{{ label(entry.role) }}</span>
          <span class="ml-1 text-medium-emphasis">— {{ sourceLabels[entry.source] ?? entry.source }}</span>
        </v-chip>
      </div>
      <div
        v-else
        class="text-medium-emphasis"
      >
        None.
      </div>

      <template v-if="standing.implied.length">
        <div class="mb-2 mt-4 text-subtitle-2">
          Roles that come with the ones above
        </div>
        <div class="d-flex flex-wrap ga-2">
          <v-chip
            v-for="role in standing.implied"
            :key="role"
            :data-testid="`user-roles-implied-${label(role)}`"
            size="small"
            variant="outlined"
          >
            <span class="text-capitalize">{{ label(role) }}</span>
            <span class="ml-1 text-medium-emphasis">— implied</span>
          </v-chip>
        </div>
      </template>

      <v-text-field
        v-model="note"
        class="mt-4"
        data-testid="user-roles-note"
        label="Why (optional)"
        maxlength="1023"
      />

      <v-alert
        v-if="failure"
        class="mb-4"
        data-testid="user-roles-failure"
        type="error"
        variant="tonal"
      >
        {{ failure }}
      </v-alert>

      <div class="mb-2 mt-2 text-subtitle-2">
        History
      </div>
      <div
        v-if="!history.length"
        class="text-medium-emphasis"
        data-testid="user-roles-history-empty"
      >
        No role has been changed here yet.
      </div>
      <v-list
        v-else
        density="compact"
        data-testid="user-roles-history"
      >
        <v-list-item
          v-for="change in history"
          :key="change.id"
          :data-testid="`user-roles-history-${change.id}`"
        >
          <v-list-item-title>
            {{ change.before.map(label).join(", ") || "nothing" }}
            →
            {{ change.after.map(label).join(", ") || "nothing" }}
          </v-list-item-title>
          <v-list-item-subtitle>
            {{ change.actorName }}, {{ safeFormatISO(change.changedAt, "dd LLL yyyy HH:mm") }}
          </v-list-item-subtitle>
          <v-list-item-subtitle v-if="change.note">
            {{ change.note }}
          </v-list-item-subtitle>
        </v-list-item>
      </v-list>
    </template>
  </base-modal>
</template>
