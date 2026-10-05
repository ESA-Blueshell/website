<script lang="ts" setup>
/* What the site's bot may do on the Discord server: each permission its work needs and whether it
   holds it, the channels it cannot see and the roles above its own, with what to change on Discord
   where something is missing. */
import {computed, onMounted, ref} from "vue"
import FactList from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PageTabs from "@/components/island/PageTabs.vue"
import StateMark from "@/components/island/StateMark.vue"
import ListHead from "@/components/management/ListHead.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type BotPermission, type BotStanding, DISCORD_TABS, botSteps, readBotStanding} from "@/domains/discord"

defineOptions({name: "DiscordBotPage"})

const standing = ref<BotStanding | null>(null)
const loaded = ref(false)

const COLUMNS: TableColumn<BotPermission>[] = [
  {key: "name", label: "Permission", wrap: true, sortBy: (one) => one.name},
  {key: "why", label: "Needed to", wrap: true, sortBy: (one) => one.neededFor},
  {key: "state", label: "State", sortBy: (one) => one.granted},
]

const lacking = computed(() => standing.value?.permissions.filter((one) => !one.granted) ?? [])
const steps = computed(() => (standing.value ? botSteps(standing.value) : []))
const facts = computed(() => {
  const read = standing.value
  if (!read) return []
  const held = read.permissions.length - lacking.value.length
  return [
    {label: "Bot's role", value: read.botRole ? `@${read.botRole.name}` : "None", sub: "It adds only the roles below its own", testid: "discord-bot-role"},
    {label: "Permissions", value: `${held} of ${read.permissions.length}`, sub: lacking.value.length === 0 ? "Everything the site needs" : `${lacking.value.length} missing`, testid: "discord-bot-held"},
    {label: "Channels it cannot see", value: String(read.hidden.length), sub: "The site can neither read nor change these", testid: "discord-bot-hidden-count"},
  ]
})

onMounted(async () => {
  standing.value = await readBotStanding()
  loaded.value = true
})
</script>

<template>
  <management-page
    eyebrow="Platforms"
    testid="discord-bot-page"
    title="Discord"
  >
    <template #lede>
      What the site's bot may do on the Discord server, and what to change on Discord where it may not.
    </template>

    <page-tabs
      :entries="DISCORD_TABS"
      label="Discord"
      testid="discord-tabs"
    />

    <p
      v-if="loaded && !standing?.connected"
      class="bot__note"
      data-testid="discord-bot-away"
    >
      The bot is not in the server now, or no bot is set up, so nothing can be checked.
    </p>

    <template v-if="standing?.connected">
      <fact-list
        class="bot__facts"
        :facts="facts"
      />

      <notice-box
        v-if="lacking.length > 0"
        class="bot__notice"
        testid="discord-bot-lacking"
        :title="lacking.length === 1 ? 'The bot lacks a permission' : `The bot lacks ${lacking.length} permissions`"
        tone="danger"
      >
        <p>{{ lacking.map((one) => one.name).join(", ") }}. What the site does with them is refused by Discord until they are turned on.</p>
      </notice-box>

      <template v-if="steps.length > 0">
        <list-head title="What to change on Discord" />
        <ol
          class="bot__steps"
          data-testid="discord-bot-steps"
        >
          <li
            v-for="step in steps"
            :key="step"
          >
            {{ step }}
          </li>
        </ol>
        <p class="bot__note">
          Only somebody who may manage the server on Discord can do this. Giving the bot's role Administrator covers every permission
          and every channel at once, though not the order of the roles.
        </p>
      </template>

      <list-head title="Permissions" />
      <management-table
        :columns="COLUMNS"
        :row-key="(one) => one.name"
        :row-testid="(one) => `discord-bot-permission-${one.name}`"
        :rows="standing.permissions"
        testid="discord-bot-permissions"
      >
        <template #name="{row}">
          <span class="mg-name">{{ row.name }}</span>
        </template>
        <template #why="{row}">
          <span class="mg-quiet">{{ row.neededFor }}</span>
        </template>
        <template #state="{row}">
          <state-mark :kind="row.granted ? 'in-step' : 'not-created'">
            {{ row.granted ? "Granted" : "Missing" }}
          </state-mark>
        </template>
      </management-table>

      <template v-if="standing.hidden.length > 0">
        <list-head :title="`Channels the bot cannot see · ${standing.hidden.length}`" />
        <p
          class="bot__note"
          data-testid="discord-bot-hidden"
        >
          {{ standing.hidden.join(", ") }}
        </p>
      </template>

      <template v-if="standing.above.length > 0">
        <list-head :title="`Roles above the bot's own · ${standing.above.length}`" />
        <p
          class="bot__note"
          data-testid="discord-bot-above"
        >
          {{ standing.above.map((one) => `@${one.name}`).join(", ") }}. The bot cannot add these to anybody or remove them.
        </p>
      </template>
    </template>
  </management-page>
</template>

<style scoped>
.bot__facts {
  padding: 1.1rem 0 0.6rem;
}

.bot__notice {
  margin-bottom: 1rem;
}

.bot__note {
  margin: 0.6rem 0 1rem;
  color: var(--color-ash);
}

.bot__steps {
  margin: 0.6rem 0 0;
  padding-left: 1.4rem;
}

.bot__steps li {
  margin-bottom: 0.45rem;
}
</style>
