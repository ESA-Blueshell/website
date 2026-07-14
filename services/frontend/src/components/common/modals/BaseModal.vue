<script lang="ts" setup>
import {computed} from "vue"

defineOptions({name: "BaseModal"})

interface Props {
  modelValue: boolean
  title: string
  maxWidth?: string
  scrollable?: boolean
  testid?: string
  showSave?: boolean
  saveLabel?: string
  saveColor?: string
  saveLoading?: boolean
  saveDisabled?: boolean
  saveTestid?: string
  showDelete?: boolean
  deleteLabel?: string
  deleteTestid?: string
  showCancel?: boolean
  cancelLabel?: string
  cancelTestid?: string
}

const props = withDefaults(defineProps<Props>(), {
  maxWidth: "760",
  scrollable: true,
  testid: "base-modal",
  showSave: false,
  saveLabel: "Save",
  saveColor: "primary",
  saveLoading: false,
  saveDisabled: false,
  saveTestid: undefined,
  showDelete: false,
  deleteLabel: "Delete",
  deleteTestid: undefined,
  showCancel: true,
  cancelLabel: "Cancel",
  cancelTestid: undefined,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "save"): void
  (e: "delete"): void
  (e: "cancel"): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (val: boolean) => emit("update:modelValue", val),
})

function onCancel() {
  open.value = false
  emit("cancel")
}
</script>

<template>
  <v-dialog
    v-model="open"
    :data-testid="testid"
    :max-width="maxWidth"
    :scrollable="scrollable"
  >
    <v-card>
      <v-card-title class="text-h5">
        {{ title }}
      </v-card-title>

      <v-card-text>
        <slot />
      </v-card-text>

      <v-card-actions>
        <v-btn
          v-if="showDelete"
          :data-testid="deleteTestid"
          color="red"
          variant="tonal"
          @click="emit('delete')"
        >
          {{ deleteLabel }}
        </v-btn>

        <v-spacer />

        <v-btn
          v-if="showCancel"
          :data-testid="cancelTestid"
          @click="onCancel"
        >
          {{ cancelLabel }}
        </v-btn>

        <v-btn
          v-if="showSave"
          :color="saveColor"
          :data-testid="saveTestid"
          :disabled="saveDisabled"
          :loading="saveLoading"
          @click="emit('save')"
        >
          {{ saveLabel }}
        </v-btn>

        <slot name="actions-append" />
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>
