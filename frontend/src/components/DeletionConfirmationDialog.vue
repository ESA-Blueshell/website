<template>
  <v-dialog
    v-model="showDialog"
    max-width="400"
  >
    <v-card>
      <v-card-title class="text-h5">
        {{ title }}
      </v-card-title>
      <!-- eslint-disable-next-line vue/no-v-html -->
      <v-card-text v-html="DOMPurify.sanitize(message)" />
      <v-card-actions>
        <v-spacer />
        <v-btn
          color="red"
          @click="confirm"
        >
          Delete
        </v-btn>
        <v-btn @click="closeDialog">
          Cancel
        </v-btn>
      </v-card-actions>
    </v-card>
  </v-dialog>
</template>

<script>
import {computed, defineComponent} from 'vue';
import DOMPurify from "dompurify";

export default defineComponent({
  name: 'DeleteConfirmationDialog',
  props: {
    modelValue: Boolean,
    title: String,
    message: String,
  },
  emits: ['update:modelValue', 'confirm'],
  setup(props, {emit}) {
    const showDialog = computed({
      get: () => props.modelValue,
      set: (value) => emit('update:modelValue', value),
    });

    const closeDialog = () => {
      emit('update:modelValue', false);
    };

    const confirm = () => {
      emit('confirm');
    };

    return {
      showDialog,
      closeDialog,
      confirm,
    };
  },
  methods: {DOMPurify},
});
</script>
