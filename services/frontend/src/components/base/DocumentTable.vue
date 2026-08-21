<template>
  <v-sheet
    class="pa-2"
    color="grey-darken-4"
    rounded="lg"
  >
    <template
      v-for="(document, index) in documents"
      :key="document.title"
    >
      <v-divider
        v-if="index > 0"
        class="my-2"
      />

      <v-row class="text-center py-4">
        <v-col
          class="text-h6"
          cols="4"
        >
          {{ document.title }}
        </v-col>
        <v-col cols="4">
          <v-btn
            :download="document.dutch.fileName"
            :href="documentUrl(document.dutch.path)"
            class="w-100"
            color="primary"
          >
            Dutch
          </v-btn>
        </v-col>
        <v-col cols="4">
          <v-btn
            :download="document.english.fileName"
            :href="documentUrl(document.english.path)"
            class="w-100"
            color="primary"
          >
            English
          </v-btn>
        </v-col>
      </v-row>
    </template>
  </v-sheet>
</template>

<script lang="ts" setup>
import {$require} from "@/plugins/require.ts"
import {
  ACTIVE_COOKIE_POLICY_DOWNLOAD_NAMES,
  ACTIVE_COOKIE_POLICY_PATHS,
} from "@/config/policies"

const documents = [
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
      path: "@/assets/documents/20260223 - ESA Blueshell Privacybeleid.pdf",
      fileName: "ESA Blueshell - Privacybeleid.pdf",
    },
    english: {
      path: "@/assets/documents/20260223 - ESA Blueshell Privacy Policy.pdf",
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
