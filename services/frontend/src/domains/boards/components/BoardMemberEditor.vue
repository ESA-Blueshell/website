<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import EditPage from "@/components/island/EditPage.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import IconButton from "@/components/island/IconButton.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"
import {loadMemberAccounts, type MemberAccount} from "@/domains/user"
import {
  addMemberOrReason,
  dropMemberOrReason,
  linkMemberAccountOrReason,
  memberTitle,
  saveMemberOrReason,
  storeMemberPortrait,
  type Board,
  type BoardMember,
} from "../adapters/boards"
import {membersInOrder} from "../memberOrder"
import {boardName} from "../reading"

/**
 * One board membership written down or corrected on its own page, with the board's row of faces
 * drawn beside the form and this member open in it. A membership is not a person: the name is
 * its own, and an account is something it may additionally have.
 */
defineOptions({name: "BoardMemberEditor"})

const props = defineProps<{
  board: Board
  /** The membership being corrected, or nothing where one is being added. */
  member: BoardMember | null
  back: string
}>()

const emit = defineEmits<{
  (event: "saved"): void
  (event: "removed"): void
  (event: "cancel"): void
}>()

const DRAFT = "draft"
const adding = computed(() => props.member == null)
const accent = computed(() => props.board.accent?.trim() || "var(--color-brand)")

const name = ref("")
const nickname = ref("")
const role = ref("")
const description = ref("")
const startDate = ref("")
const endDate = ref("")
const userId = ref<number | null>(null)
const portrait = ref<Picture | null>(null)
const failure = ref<string | null>(null)
const saving = ref(false)
const accounts = ref<MemberAccount[]>([])
const accountsUnknown = ref(false)

const dayOf = (date?: string | null): string => (date ?? "").trim().slice(0, 10)

watch(() => [props.member, props.board] as const, async ([membership, board]) => {
  name.value = membership?.name ?? ""
  nickname.value = membership?.nickname ?? ""
  role.value = membership?.role ?? ""
  description.value = membership?.description ?? ""
  startDate.value = dayOf(membership?.startDate ?? board.startDate)
  endDate.value = dayOf(membership?.endDate ?? board.endDate)
  userId.value = membership?.userId ?? null
  portrait.value = membership?.portrait ?? null
  failure.value = null
  if (accounts.value.length === 0) {
    const read = await loadMemberAccounts()
    accountsUnknown.value = read == null
    accounts.value = read ?? []
  }
}, {immediate: true})

const accountOptions = computed(() => accounts.value.map(account =>
  ({key: String(account.id), label: account.name, note: account.email ?? undefined})))
const accountsNote = computed(() => (accountsUnknown.value
  ? "Those accounts could not be read, so none can be attached. Go back and open this again."
  : "Nobody has an account here yet."))
const nameOf = (id: number | null): string =>
  (id == null ? "" : accounts.value.find(account => account.id === id)?.name ?? `Member ${id}`)

const complete = computed(() => name.value.trim() !== "" && role.value.trim() !== "" && startDate.value !== "")

/** This membership as the board page will draw it, from what is typed now. */
const drafted = computed<BoardMember>(() => ({
  ...(props.member ?? {id: DRAFT as unknown as number}),
  name: name.value.trim() || "New member",
  nickname: nickname.value.trim() || null,
  role: role.value.trim() || "Role",
  description: description.value.trim() || null,
  startDate: startDate.value,
  endDate: endDate.value || null,
  userId: userId.value,
  portrait: portrait.value,
}) as BoardMember)

const portraitOf = (member: BoardMember): string => {
  const stored = member.portrait
  if (!stored) return ""
  return stored.renditions[stored.renditions.length - 1]?.url ?? stored.url
}

const sliceOf = (member: BoardMember) => ({
  id: member.id,
  title: memberTitle(member),
  meta: member.role,
  banner: portraitOf(member),
  srcset: srcsetOf(member.portrait),
  expandable: true,
  ...sizeOf(member.portrait),
})

const slices = computed(() => membersInOrder([
  ...(props.board.members ?? []).filter(one => one.id !== props.member?.id),
  drafted.value,
]).map(sliceOf))

const blurbOf = (id: number | string) => (id === drafted.value.id
  ? drafted.value.description ?? ""
  : props.board.members?.find(one => one.id === id)?.description ?? "")

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)

const question = computed(() => {
  const membership = props.member
  if (!membership) return ""
  const said = membership.description?.trim() ? " What they wrote about themselves goes with it." : ""
  return `${memberTitle(membership)} held ${membership.role} on this board. Removing the member takes that place `
    + `out of the association's history.${said}`
})

const removeMember = async () => {
  const membership = props.member
  if (!membership || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropMemberOrReason(props.board.id, membership.id)
    if (!result.ok) {
      removalFailure.value = result.reason
      return
    }
    confirming.value = false
    emit("removed")
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const written = {
      role: role.value.trim(),
      startDate: startDate.value,
      endDate: endDate.value || null,
      displayName: name.value.trim(),
      nickname: nickname.value.trim() || null,
      description: description.value.trim() || null,
      portrait: portrait.value?.path ?? null,
    }
    const membership = props.member
    if (membership == null) {
      const added = await addMemberOrReason(props.board.id, {...written, userId: userId.value})
      if (!added.ok) {
        failure.value = added.reason
        return
      }
    } else {
      const saved = await saveMemberOrReason(props.board.id, membership.id, written)
      if (!saved.ok) {
        failure.value = saved.reason
        return
      }
      if (userId.value !== (membership.userId ?? null)) {
        const linked = await linkMemberAccountOrReason(props.board.id, membership.id, userId.value)
        if (!linked.ok) {
          failure.value = linked.reason
          return
        }
      }
    }
    emit("saved")
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <edit-page
    :accent="accent"
    :back="{to: back, label: boardName(board.number, board.name)}"
    :eyebrow="boardName(board.number, board.name)"
    testid="board-member-edit"
    :title="adding ? 'Add a member' : 'Edit member'"
  >
    <template
      v-if="member"
      #actions
    >
      <cut-button
        testid="board-member-edit-remove"
        tone="danger"
        @click="confirming = true"
      >
        Remove member
      </cut-button>
    </template>

    <form
      class="member-editor"
      @submit.prevent="submit"
    >
      <form-section title="The member">
        <form-fields>
          <div class="form-span">
            <image-picker
              label="Portrait"
              :picture="portrait"
              shape="portrait"
              :store="storeMemberPortrait"
              testid="board-member-edit-portrait"
              @update:picture="portrait = $event"
            />
          </div>
          <form-control
            v-model="name"
            label="Name*"
            testid="board-member-edit-name"
          />
          <form-control
            v-model="nickname"
            label="Nickname"
            testid="board-member-edit-nickname"
          />
          <div class="form-span">
            <form-control
              v-model="role"
              label="Role*"
              testid="board-member-edit-role"
            />
          </div>
          <div class="form-span">
            <form-control
              v-model="description"
              kind="textarea"
              label="Blurb"
              testid="board-member-edit-description"
            />
          </div>
        </form-fields>
      </form-section>

      <form-section title="Account">
        <div
          v-if="userId != null"
          class="member-editor__attached"
          data-testid="board-member-edit-attached"
        >
          <span>{{ nameOf(userId) }}</span>
          <icon-button
            danger
            :label="`Detach ${nameOf(userId)}`"
            testid="board-member-edit-detach"
            @click="userId = null"
          >
            <svg
              aria-hidden="true"
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            ><path d="M6 6l12 12M18 6 6 18" /></svg>
          </icon-button>
        </div>
        <form-field
          v-else
          label="Account"
          testid="board-member-edit-account"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              :empty-note="accountsNote"
              :labelled-by="labelId"
              :options="accountOptions"
              placeholder="No account"
              testid-prefix="board-member-edit-account"
              @pick="key => userId = Number(key)"
            />
          </template>
        </form-field>
      </form-section>

      <form-section title="Term">
        <form-fields>
          <form-control
            v-model="startDate"
            kind="date"
            label="Took office*"
            testid="board-member-edit-start"
          />
          <form-control
            v-model="endDate"
            kind="date"
            label="Left it"
            testid="board-member-edit-end"
          />
        </form-fields>
      </form-section>

      <p
        v-if="failure"
        class="member-editor__failure"
        data-testid="board-member-edit-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <template #footer>
      <div class="member-editor__save">
        <cut-button
          testid="board-member-edit-cancel"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          :disabled="!complete || saving"
          testid="board-member-edit-save"
          tone="solid"
          @click="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the member" : "Save" }}
        </cut-button>
      </div>
    </template>

    <template #preview>
      <preview-frame>
        <slice-band
          accent="var(--color-brand)"
          :items="slices"
          layout="aside"
          :open-id="drafted.id"
          testid-prefix="board-member-edit-preview"
        >
          <template #details="{item}">
            <p
              v-if="blurbOf(item.id)"
              class="member-editor__blurb"
            >
              {{ blurbOf(item.id) }}
            </p>
          </template>
        </slice-band>
      </preview-frame>
    </template>
  </edit-page>

  <confirm-dialog
    v-if="member"
    :accent="accent"
    confirm-label="Remove the member"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="board-member-remove-dialog"
    title="Remove this member?"
    :working="removing"
    @confirm="removeMember"
    @update:open="confirming = $event"
  />
</template>

<style scoped>
.member-editor__attached {
  display: flex;
  gap: 0.75rem;
  align-items: center;
  justify-content: space-between;
  max-width: 24rem;
  padding: 0.55rem 0.5rem 0.55rem 0.9rem;
  font-size: 0.95rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.member-editor__failure {
  margin: 0.5rem 0 0;
  font-size: 0.88rem;
  color: var(--color-danger);
}

.member-editor__save {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.member-editor__blurb {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.5;
  color: var(--color-chalk);
  opacity: 0.92;
}
</style>
