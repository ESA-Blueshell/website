<script lang="ts" setup>
/* The folders of a system that want a hand: the ones that share a name, which are merged into the
   oldest, and the ones holding no list, which are removed once ticked. Nothing is drawn while
   every folder has its own name and holds something. */
import {computed, ref, watch} from "vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PairList from "@/components/management/PairList.vue"
import store from "@/plugins/store"
import {type FolderState, type TargetSystem, mergeFolders, readFolderStates, removeFolder} from "../adapters/cohorts"

defineOptions({name: "FolderCare"})

const {system, after = undefined, testid = "folder-care"} = defineProps<{
  system: TargetSystem
  /** Anything that changes when the page has read the system again, so the folders are read again with it. */
  after?: unknown
  testid?: string
}>()

const emit = defineEmits<{changed: []}>()

const folders = ref<FolderState[]>([])
const acting = ref(false)
const said = (message: string) => store.commit("setStatusSnackbarMessage", message)
const plural = (count: number, one: string, many: string) => `${count} ${count === 1 ? one : many}`

const read = async () => {
  folders.value = await readFolderStates(system)
}
watch(() => after, read, {immediate: true})

const shared = computed(() => {
  const byName = new Map<string, FolderState[]>()
  for (const folder of folders.value) byName.set(folder.name.toLowerCase(), [...(byName.get(folder.name.toLowerCase()) ?? []), folder])
  return [...byName.values()].filter((copies) => copies.length > 1)
})
const sharedIds = computed(() => new Set(shared.value.flat().map((one) => one.id)))
const empty = computed(() => folders.value.filter((one) => one.targets === 0 && !sharedIds.value.has(one.id)))
const sharedPairs = computed(() => shared.value.map((copies) => ({
  label: copies[0]!.name,
  value: `${plural(copies.length, "folder", "folders")} holding ${plural(copies.reduce((sum, one) => sum + one.targets, 0), "list", "lists")}. One stays with every list in it.`,
})))

const merging = ref(false)
const merge = async () => {
  if (acting.value) return
  acting.value = true
  const answered = await mergeFolders(system)
  acting.value = false
  if (!answered.ok) return said(answered.reason)
  merging.value = false
  said(`${plural(answered.saved.removed, "folder", "folders")} removed, ${plural(answered.saved.moved, "list", "lists")} moved.`)
  await read()
  emit("changed")
}

/* An empty folder is removed only once it is ticked: none is ticked for the reader. */
const removing = ref(false)
const picked = ref<Set<string>>(new Set())
const openRemove = () => {
  picked.value = new Set()
  removing.value = true
}
const pick = (id: string, ticked: boolean) => {
  const next = new Set(picked.value)
  if (ticked) next.add(id)
  else next.delete(id)
  picked.value = next
}
const remove = async () => {
  if (acting.value || picked.value.size === 0) return
  acting.value = true
  const answers = await Promise.all([...picked.value].map((id) => removeFolder(system, id)))
  acting.value = false
  const refused = answers.find((one) => !one.ok)
  const removed = answers.filter((one) => one.ok).length
  said(refused && !refused.ok ? refused.reason : `${plural(removed, "folder", "folders")} removed.`)
  if (!refused) removing.value = false
  await read()
  if (removed > 0) emit("changed")
}
</script>

<template>
  <notice-box
    v-if="shared.length > 0 || empty.length > 0"
    class="folder-care"
    :testid="testid"
    title="Folders"
    tone="warning"
  >
    <p
      v-if="shared.length > 0"
      :data-testid="`${testid}-shared`"
    >
      {{ shared.length === 1 ? `Two or more folders are called ${shared[0]![0]!.name}.` : `${shared.length} folder names are each used by two or more folders.` }}
      A list can end up in either, and a folder picker shows the name once.
    </p>
    <p
      v-if="empty.length > 0"
      :data-testid="`${testid}-empty`"
    >
      {{ plural(empty.length, "folder holds", "folders hold") }} no list: {{ empty.map((one) => one.name).join(", ") }}.
    </p>
    <div class="folder-care__acts">
      <cut-button
        v-if="shared.length > 0"
        :disabled="acting"
        small
        :testid="`${testid}-merge`"
        @click="merging = true"
      >
        Merge folders with the same name
      </cut-button>
      <cut-button
        v-if="empty.length > 0"
        :disabled="acting"
        small
        :testid="`${testid}-remove`"
        @click="openRemove"
      >
        Remove empty folders
      </cut-button>
    </div>

    <modal-dialog
      :open="merging"
      :testid="`${testid}-merge-dialog`"
      title="Merge folders with the same name"
      @update:open="merging = $event"
    >
      <p class="folder-care__note">
        For each name the oldest folder stays. The lists in the others move into it, with their people, and the others are then removed.
      </p>
      <pair-list :pairs="sharedPairs" />
      <template #footer>
        <cut-button
          :disabled="acting"
          :testid="`${testid}-merge-confirm`"
          tone="solid"
          @click="merge"
        >
          Merge the folders
        </cut-button>
      </template>
    </modal-dialog>

    <modal-dialog
      danger
      :open="removing"
      :testid="`${testid}-remove-dialog`"
      title="Remove empty folders"
      @update:open="removing = $event"
    >
      <p class="folder-care__note">
        Tick the folders to remove. Each holds no list, so no list and nobody on one is touched.
      </p>
      <check-box
        v-for="folder in empty"
        :key="folder.id"
        :label="folder.name"
        :model-value="picked.has(folder.id)"
        :testid="`${testid}-pick-${folder.id}`"
        @update:model-value="pick(folder.id, $event)"
      />
      <template #footer>
        <cut-button
          :disabled="acting || picked.size === 0"
          :testid="`${testid}-remove-confirm`"
          tone="danger"
          @click="remove"
        >
          Remove {{ plural(picked.size, "folder", "folders") }}
        </cut-button>
      </template>
    </modal-dialog>
  </notice-box>
</template>

<style scoped>
.folder-care__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.folder-care__note {
  margin: 0 0 0.8rem;
}
</style>
