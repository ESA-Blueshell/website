<script lang="ts" setup>
/* A committee's Brevo list on its form: the list its people are on. A committee without one gets a
   new list by default, or links a list already in Brevo that follows nothing. Hidden where Brevo
   cannot be asked. */
import {computed, onMounted, ref, watch} from "vue"
import FormField from "@/components/island/FormField.vue"
import FormSection from "@/components/island/FormSection.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import type {BrevoPlace, BrevoPlaceRequest} from "@/services/api"
import {ARCHIVE_FOLDER} from "../listOverview"
import {type ListedTarget, TargetSystem, readTargetOverview} from "../adapters/cohorts"

defineOptions({name: "BrevoListFields"})

const NEW_LIST = "__new__"

const {read, name, folder = "Committees", holders = "Everyone with a seat is on it.", testid = "committee-edit-brevo"} = defineProps<{
  /** Reads the list as it stands, or nothing where the record is being added. */
  read: (() => Promise<BrevoPlace | null>) | null
  name: string
  /** The folder a new list goes in. */
  folder?: string
  holders?: string
  testid?: string
}>()

/** What the form will ask of Brevo once the record is saved, or nothing where Brevo is away. */
const choice = defineModel<BrevoPlaceRequest | null>({default: null})

const adding = read == null
const state = ref<BrevoPlace | null>(null)
const free = ref<ListedTarget[] | null>(null)
const listKey = ref<string | null>(NEW_LIST)

const available = computed(() => (adding ? free.value != null : state.value?.available === true))
const linked = computed(() => state.value?.listId ?? null)
const options = computed(() => [
  {key: NEW_LIST, label: `A new list, ${name.trim() || "named after it"}`, note: folder},
  ...(free.value ?? []).map((one) => ({key: one.externalId, label: one.label, note: one.folderLabel ?? undefined})),
])

watch([available, linked, listKey], () => {
  choice.value = available.value && linked.value == null
    ? {listId: listKey.value !== NEW_LIST ? listKey.value : null, createList: listKey.value === NEW_LIST}
    : null
}, {immediate: true})

onMounted(async () => {
  const [found, overview] = await Promise.all([read ? read() : Promise.resolve(null), readTargetOverview(TargetSystem.BREVO)])
  state.value = found
  free.value = overview ? overview.lists.filter((one) => one.targetId == null && one.folderLabel !== ARCHIVE_FOLDER) : null
})
</script>

<template>
  <form-section
    v-if="available"
    title="Brevo"
  >
    <div
      class="brevo-place"
      :data-testid="testid"
    >
      <p
        v-if="linked"
        class="brevo-place__list"
        :data-testid="`${testid}-list`"
      >
        {{ state?.listName ?? linked }}<span class="brevo-place__note">In the {{ state?.folder ?? folder }} folder. {{ holders }}</span>
      </p>
      <form-field
        v-else
        label="List"
        :testid="`${testid}-list-field`"
      >
        <template #default="{controlId, labelId}">
          <search-picker
            :control-id="controlId"
            :labelled-by="labelId"
            :options="options"
            placeholder="Link a list"
            :selected-key="listKey"
            :testid-prefix="`${testid}-list-picker`"
            @pick="(key: string) => listKey = key"
          />
        </template>
      </form-field>
    </div>
  </form-section>
</template>

<style scoped>
.brevo-place {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.brevo-place__list {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin: 0;
  font-weight: 600;
}

.brevo-place__note {
  font-weight: 400;
  color: var(--color-ash);
}
</style>
