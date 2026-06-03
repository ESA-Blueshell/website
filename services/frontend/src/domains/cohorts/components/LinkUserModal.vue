<script lang="ts" setup>
import { ref } from "vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import type { ExternalUserConflict } from "../types"

const props = defineProps<{
  modelValue: boolean
  externalUserId: string
  link: (userId: number) => Promise<{ type: "ok" } | { type: "conflict"; conflict: ExternalUserConflict }>
}>()

const emit = defineEmits<{
  "update:modelValue": [value: boolean]
}>()

const userId = ref<number | undefined>(undefined)
const submitting = ref(false)
const conflict = ref<ExternalUserConflict | null>(null)

function close() {
  userId.value = undefined
  conflict.value = null
  emit("update:modelValue", false)
}

async function submit() {
  if (userId.value == null) return
  submitting.value = true
  conflict.value = null
  try {
    const result = await props.link(userId.value)
    if (result.type === "ok") {
      close()
    } else {
      conflict.value = result.conflict
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <v-dialog
    :model-value="modelValue"
    max-width="440"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <v-card title="Link external user to local account">
      <v-card-text>
        <p class="text-body-2 text-medium-emphasis mb-3">
          External id: <code>{{ externalUserId }}</code>
        </p>

        <user-picker
          v-model="userId"
          label="Local user"
          required
        />

        <v-alert
          v-if="conflict"
          class="mt-2"
          density="compact"
          type="warning"
          variant="tonal"
        >
          That external id is already linked to
          <strong>{{ conflict.existingUserFullName ?? `User #${conflict.existingUserId}` }}</strong>.
          Resolve the existing mapping first or choose a different user.
        </v-alert>
      </v-card-text>

      <v-card-actions>
        <v-spacer />
        <v-btn
          variant="text"
          @click="close"
        >
          Cancel
        </v-btn>
        <v-btn
          :disabled="userId == null || submitting"
          :loading="submitting"
          color="primary"
          variant="flat"
          @click="submit"
        >
          Link
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
