<script lang="ts" setup>
import {type PropType, toRef} from "vue"
import $markdownToHtml from "@/plugins/markdownToHtml.ts"
import {type CommitteeDetailResponse} from "@/services/api"

const props = defineProps({
  committee: {
    type: Object as PropType<CommitteeDetailResponse>,
    required: true,
  },
})

const committee = toRef(props, "committee")
</script>

<template>
  <v-card
    :id="`committee-${committee.id ?? committee.name}`"
    class="committee-card py-4 px-4"
    rounded="md"
  >
    <v-container class="pa-0">
      <v-row
        class="align-stretch flex-nowrap"
        no-gutters
      >
        <v-col class="flex-grow-1 min-w-0">
          <div class="d-flex align-center justify-space-between ga-3">
            <div
              class="text-h5 font-weight-light text-wrap"
              style="word-break: break-word;"
            >
              {{ committee.name }}
            </div>
          </div>
          <v-divider
            class="mb-1"
            style="border-color: grey"
          />

          <div
            class="text-wrap"
            style="word-break: break-word"
            v-html="committee.description ? $markdownToHtml(committee.description) : 'No description...'"
          />
        </v-col>
      </v-row>
    </v-container>
  </v-card>
</template>

<style lang="scss" scoped>
.flex-nowrap {
  flex-wrap: nowrap !important;
}

.medium-emphasis-opacity {
  opacity: var(--v-medium-emphasis-opacity);
}

.committee-card {
  min-height: 160px;
}

// A flex child will not shrink below its content without this, so a long committee name
// pushed the row wider instead of ellipsing.
.min-w-0 {
  min-width: 0;
}
</style>
