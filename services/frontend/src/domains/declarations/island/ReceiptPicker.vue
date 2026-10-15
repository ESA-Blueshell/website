<script lang="ts" setup>
import {ref} from "vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import type {Picture} from "@/components/island/pictures"
import {type Crop, uploadReceipt} from "../adapters/declarations"
import type {Receipt} from "../declarations"
import ReceiptCropper from "./ReceiptCropper.vue"

/** Receipts as picture inputs, one per receipt and one to add the next; a photo is cropped first. */
defineOptions({name: "ReceiptPicker"})

const {testid, invalid = false, wrong = null} = defineProps<{
  testid: string
  /** The form says a receipt is missing, which turns the inputs red. */
  invalid?: boolean
  /** What is missing, said under the inputs. */
  wrong?: string | null
}>()

const receipts = defineModel<Receipt[]>({required: true})

/* The picker hands back only the picture, so the receipt it was stored as waits here by path. */
const stored = new Map<string, Receipt>()
const asPicture = (receipt: Receipt): Picture => ({path: receipt.path, url: receipt.url, renditions: []})

/* A photo waits here while its owner frames the bill; a PDF goes straight through. */
const cropping = ref<{src: string; settle: (crop: Crop | null) => void} | null>(null)

const cropOf = (file: File) => new Promise<Crop | null>(resolve => {
  const src = URL.createObjectURL(file)
  cropping.value = {
    src,
    settle: crop => {
      URL.revokeObjectURL(src)
      cropping.value = null
      resolve(crop)
    },
  }
})

const store = async (file: File) => {
  const crop = file.type.startsWith("image/") ? await cropOf(file) : undefined
  // Cancelled: nothing to say, the picker stays as it was.
  if (crop === null) return {ok: false as const, reason: ""}
  const sent = await uploadReceipt(file, crop).catch(() => null)
  if (!sent?.ok) return {ok: false as const, reason: sent?.reason ?? "We could not read that file. Try a photo or a PDF."}
  stored.set(sent.saved.path, sent.saved)
  return {ok: true as const, saved: asPicture(sent.saved)}
}

const put = (at: number, picture: Picture | null) => {
  const receipt = picture ? stored.get(picture.path) : undefined
  const kept = [...receipts.value]
  if (receipt) kept.splice(at, 1, receipt)
  else kept.splice(at, 1)
  receipts.value = kept
}
</script>

<template>
  <div class="receipt-picker">
    <image-picker
      v-for="(receipt, index) in receipts"
      :key="receipt.path"
      label="Receipt"
      may-be-pdf
      :picture="asPicture(receipt)"
      shape="portrait"
      :store="store"
      :testid="`${testid}-receipt-${index}`"
      @update:picture="put(index, $event)"
    />
    <image-picker
      :invalid="invalid"
      :label="receipts.length ? 'Another receipt' : 'Receipt'"
      may-be-pdf
      :picture="null"
      :say="receipts.length ? 'Add another receipt' : 'Add a receipt'"
      shape="portrait"
      :store="store"
      :testid="`${testid}-receipts`"
      @update:picture="put(receipts.length, $event)"
    />
    <p
      v-if="wrong"
      class="receipt-picker__wrong"
      :data-testid="`${testid}-receipts-wrong`"
    >
      {{ wrong }}
    </p>
    <receipt-cropper
      :src="cropping?.src ?? null"
      @cancel="cropping?.settle(null)"
      @done="cropping?.settle($event)"
    />
  </div>
</template>

<style scoped>
.receipt-picker {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

/* As a field says what it is missing, under the picture input. */
.receipt-picker__wrong {
  margin-top: -0.2rem;
  font-size: 0.75rem;
  color: var(--color-danger);
}
</style>
