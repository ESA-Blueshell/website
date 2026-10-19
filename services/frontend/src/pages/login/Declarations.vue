<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import StateTag from "@/components/island/StateTag.vue"
import {
  type Declaration,
  DeclarationGlyph,
  DeclarationState,
  euros,
  listOwnDeclarations,
  metaOf,
  stateOf,
  titleOf,
  totalOf,
} from "@/domains/declarations"

defineOptions({name: "DeclarationsPage"})

const declarations = ref<Declaration[] | null>(null)
const loaded = ref(false)

onMounted(async () => {
  declarations.value = await listOwnDeclarations()
  loaded.value = true
})

const count = (n: number, noun: string) => `${n} ${n === 1 ? noun : `${noun}s`}`
const sum = (list: Declaration[]) => list.reduce((total, one) => total + totalOf(one.expenses), 0)

const DONE = new Set([DeclarationState.PAID, DeclarationState.REJECTED])

const open = computed(() => (declarations.value ?? []).filter(one => !DONE.has(one.state)))
const done = computed(() => (declarations.value ?? []).filter(one => DONE.has(one.state)))
const waiting = computed(() => open.value.filter(one => one.state !== DeclarationState.DRAFT))
const paid = computed(() => done.value.filter(one => one.state === DeclarationState.PAID))
const drafts = computed(() => open.value.filter(one => one.state === DeclarationState.DRAFT))

const facts = computed<Fact[]>(() => [
  {
    label: "Waiting",
    value: waiting.value.length ? euros(sum(waiting.value)) : "Nothing",
    sub: waiting.value.length ? count(waiting.value.length, "declaration") : "No declarations are waiting for the board",
    testid: "declarations-waiting",
  },
  {
    label: "Paid",
    value: paid.value.length ? euros(sum(paid.value)) : "Nothing yet",
    sub: paid.value.length ? count(paid.value.length, "declaration") : undefined,
    testid: "declarations-paid",
  },
  {
    label: "Drafts",
    value: String(drafts.value.length),
    sub: drafts.value.length ? "Not submitted yet" : undefined,
    testid: "declarations-drafts",
  },
])
</script>

<template>
  <account-frame
    heading="Declarations"
    island-content
  >
    <template v-if="loaded">
      <fact-list
        class="declarations__facts"
        :facts="facts"
      />

      <div class="declarations__rows">
        <cut-row
          meta="Did you pay for something for Blueshell? Add the receipts and we'll pay you back."
          testid="declarations-new"
          title="New declaration"
          to="/account/declarations/new"
        >
          <template #glyph>
            <declaration-glyph name="plus" />
          </template>
        </cut-row>
      </div>

      <p
        v-if="!declarations"
        class="declarations__none"
        data-testid="declarations-unread"
      >
        We could not load your declarations. Try again later.
      </p>

      <section
        v-else
        data-testid="declarations-list"
      >
        <h2 class="declarations__head">
          Your declarations<count-badge
            v-if="declarations.length"
            :count="declarations.length"
            said="declarations"
          />
        </h2>
        <p
          v-if="declarations.length === 0"
          class="declarations__none"
          data-testid="declarations-none"
        >
          You have not added a declaration yet.
        </p>

        <template
          v-for="group in [{name: 'Open', list: open}, {name: 'Done', list: done}]"
          :key="group.name"
        >
          <template v-if="group.list.length">
            <h3 class="declarations__group">
              {{ group.name }}
            </h3>
            <div class="declarations__rows">
              <cut-row
                v-for="declaration in group.list"
                :key="declaration.id"
                :meta="metaOf(declaration)"
                :testid="`declarations-row-${declaration.id}`"
                :title="titleOf(declaration)"
                :to="`/account/declarations/${declaration.id}`"
              >
                <template #glyph>
                  <declaration-glyph :name="declaration.state === DeclarationState.PAID ? 'paid' : 'receipt'" />
                </template>
                <template #end>
                  <state-tag :tone="stateOf(declaration).tone">
                    {{ stateOf(declaration).label }}
                  </state-tag>
                </template>
              </cut-row>
            </div>
          </template>
        </template>
      </section>
    </template>
  </account-frame>
</template>

<style scoped>
.declarations__facts {
  padding: 1.6rem 0 1.8rem;
}

.declarations__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.declarations__head {
  margin: 2.5rem 0 0.4rem;
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.declarations__group {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.2rem 0 0.6rem;
  font-family: var(--font-display);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.declarations__group::after {
  content: "";
  flex: 1 1 auto;
  height: 1px;
  background: var(--color-hairline);
}

.declarations__none {
  margin-top: 0.6rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}
</style>
