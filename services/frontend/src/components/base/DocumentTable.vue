<template>
  <ul class="documents">
    <li
      v-for="document in documents"
      :key="document.title"
      class="documents__row"
    >
      <span class="documents__title">{{ document.title }}</span>
      <!-- The cell is kept when a document has no Dutch edition, so the English
           buttons stay in one column down the list. -->
      <span class="documents__cell">
        <cut-button
          v-if="document.dutch"
          :download="document.dutch.fileName"
          :href="documentUrl(document.dutch.path)"
          small
        >
          Dutch
        </cut-button>
      </span>
      <span class="documents__cell">
        <cut-button
          :download="document.english.fileName"
          :href="documentUrl(document.english.path)"
          small
        >
          English
        </cut-button>
      </span>
    </li>
  </ul>
</template>

<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"
import {$require} from "@/plugins/require.ts"
import {
  ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES,
  ACTIVE_COOKIE_POLICY_PATHS,
} from "@/config/policies"

type DocumentEdition = {
  path: string
  fileName: string
}

/** A document is English-only when the association only ever produced one edition of it. */
type AssociationDocument = {
  title: string
  dutch?: DocumentEdition
  english: DocumentEdition
}

const documents: AssociationDocument[] = [
  {
    title: "Statutes",
    dutch: {
      path: "@/assets/documents/20171212 - ESA Blueshell Statuten.pdf",
      fileName: "ESA Blueshell - Statuten.pdf",
    },
    english: {
      path: "@/assets/documents/20171212 - ESA Blueshell Statutes.pdf",
      fileName: "ESA Blueshell - Statutes.pdf",
    },
  },
  {
    title: "Domestic Regulations",
    dutch: {
      path: "@/assets/documents/20180109 - ESA Blueshell Huishoudelijk Reglement.pdf",
      fileName: "ESA Blueshell - Huishoudelijk Reglement.pdf",
    },
    english: {
      path: "@/assets/documents/20180109 - ESA Blueshell Domestic Regulations.pdf",
      fileName: "ESA Blueshell - Domestic Regulations.pdf",
    },
  },
  {
    title: "Privacy Policy",
    dutch: {
      path: "@/assets/documents/20261003 - ESA Blueshell Privacybeleid.pdf",
      fileName: "ESA Blueshell - Privacybeleid.pdf",
    },
    english: {
      path: "@/assets/documents/20261003 - ESA Blueshell Privacy Policy.pdf",
      fileName: "ESA Blueshell - Privacy Policy.pdf",
    },
  },
  {
    title: "Code of Conduct",
    dutch: {
      path: "@/assets/documents/20210324 - ESA Blueshell Gedragscode.pdf",
      fileName: "ESA Blueshell - Gedragscode.pdf",
    },
    english: {
      path: "@/assets/documents/20210324 - ESA Blueshell Code of Conduct.pdf",
      fileName: "ESA Blueshell - Code of Conduct.pdf",
    },
  },
  {
    title: "Direct Debit Mandate",
    english: {
      path: "@/assets/documents/20210801 - ESA Blueshell Direct Debit Mandate.pdf",
      fileName: "ESA Blueshell - Direct Debit Mandate.pdf",
    },
  },
  {
    title: "Cookie Policy",
    dutch: {
      path: ACTIVE_COOKIE_POLICY_PATHS.dutch,
      fileName: ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES.dutch,
    },
    english: {
      path: ACTIVE_COOKIE_POLICY_PATHS.english,
      fileName: ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES.english,
    },
  },
]

function documentUrl(path: string): string {
  return path.startsWith("http") ? path : $require(path)
}
</script>

<style scoped>
.documents {
  margin: 0;
  padding: 0;
  list-style: none;
  background-color: var(--color-surface);
}

.documents__row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 0.6rem;
  align-items: center;
  padding: 0.9rem 1.2rem;
}

.documents__row + .documents__row {
  border-top: 1px solid var(--color-hairline);
}

.documents__title {
  font-weight: 600;
}

.documents__cell {
  min-width: 6.5rem;
  display: flex;
  justify-content: flex-end;
}
</style>
