<template>
  <v-dialog
    v-model="showDialog"
    data-testid="deletion-confirmation-dialog"
    max-width="400"
  >
    <v-card>
      <v-card-title class="text-h5">
        {{ title }}
      </v-card-title>
      <v-card-text>
        {{ message }}
      </v-card-text>
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="red"
          data-testid="deletion-confirmation-confirm-btn"
          @click="confirm"
        >
          Delete
        </v-btn>
        <v-btn
          data-testid="deletion-confirmation-cancel-btn"
          @click="closeDialog"
        >
          Cancel
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script lang="ts" setup>
import {computed} from "vue"

interface Props {
  modelValue?: boolean;
  title?: string;
  message?: string;
}

const props = withDefaults(defineProps<Props>(), {
  modelValue: false,
  title: "",
  message: "",
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void;
  (e: "confirm"): void;
}>()

const showDialog = computed({
  get: () => props.modelValue,
  set: (value: boolean) => emit("update:modelValue", value),
})

const closeDialog = () => {
  emit("update:modelValue", false)
}

const confirm = () => {
  emit("confirm")
}
</script>
