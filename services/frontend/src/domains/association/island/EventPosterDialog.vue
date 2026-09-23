<script lang="ts" setup>
/**
 * One past event, read where it was clicked.
 *
 * The events page pages through what is upcoming, so an event from two years ago has no address
 * of its own there: reaching it would mean knowing which page it falls on. It opens here
 * instead, and the page's own address carries the event's id, so the link somebody copies opens
 * the same event on the same page.
 */
import {DateTime} from "luxon"
import $markdownToHtml from "@/plugins/markdownToHtml"
import ModalDialog from "@/components/island/ModalDialog.vue"
import type {EventOnShow} from "@/domains/association/adapters/association"

const {event = undefined, testid} = defineProps<{
  event?: EventOnShow
  testid: string
}>()

const open = defineModel<boolean>("open", {default: false})

const said = (iso: string): string => DateTime.fromISO(iso).toFormat("cccc d LLLL yyyy, HH:mm")
</script>

<template>
  <modal-dialog
    v-model:open="open"
    :testid="testid"
    :title="event?.title ?? ''"
  >
    <div
      v-if="event"
      class="flex flex-col gap-4"
    >
      <img
        alt=""
        class="w-full object-cover"
        :src="event.banner.url"
        :srcset="event.banner.renditions.map(one => `${one.url} ${one.width}w`).join(', ')"
      >

      <dl class="flex flex-wrap gap-x-8 gap-y-1 font-bitmap text-[0.68rem] tracking-wider text-ash uppercase">
        <div class="flex gap-2">
          <dt class="sr-only">
            When
          </dt>
          <dd>{{ said(event.startTime) }}</dd>
        </div>
        <div
          v-if="event.location"
          class="flex gap-2"
        >
          <dt class="sr-only">
            Where
          </dt>
          <dd>{{ event.location }}</dd>
        </div>
        <div
          v-if="event.membersOnly"
          class="flex gap-2"
        >
          <dt class="sr-only">
            Who
          </dt>
          <dd>Members only</dd>
        </div>
      </dl>

      <!-- eslint-disable-next-line vue/no-v-html -->
      <div
        v-if="event.description"
        class="prose-island text-sm leading-relaxed text-chalk"
        v-html="$markdownToHtml(event.description)"
      />
    </div>
  </modal-dialog>
</template>
