<template>
  <v-sheet
    class="pa-2"
    color="grey-darken-4"
    rounded="lg"
  >
    <!-- Statutes -->
    <v-row class="text-center py-4">
      <v-col
        class="text-h6"
        cols="4"
      >
        Statutes
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20171212 - ESA Blueshell Statuten.pdf', 'ESA Blueshell - Statuten.pdf')"
        >
          Dutch
        </v-btn>
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20171212 - ESA Blueshell Statutes.pdf', 'ESA Blueshell - Statutes.pdf')"
        >
          English
        </v-btn>
      </v-col>
    </v-row>

    <v-divider class="my-2" />

    <!-- Domestic Regulations -->
    <v-row class="text-center py-4">
      <v-col
        class="text-h6"
        cols="4"
      >
        Domestic Regulations
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20180109 - ESA Blueshell Huishoudelijk Reglement.pdf', 'ESA Blueshell - Huishoudelijk Reglement.pdf')"
        >
          Dutch
        </v-btn>
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20180109 - ESA Blueshell Domestic Regulations.pdf', 'ESA Blueshell - Domestic Regulations.pdf')"
        >
          English
        </v-btn>
      </v-col>
    </v-row>

    <v-divider class="my-2" />

    <!-- Privacy Policy -->
    <v-row class="text-center py-4">
      <v-col
        class="text-h6"
        cols="4"
      >
        Privacy Policy
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20191020 - ESA Blueshell Privacybeleid.pdf', 'ESA Blueshell - Privacybeleid.pdf')"
        >
          Dutch
        </v-btn>
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20191020 - ESA Blueshell Privacy Policy.pdf', 'ESA Blueshell - Privacy Policy.pdf')"
        >
          English
        </v-btn>
      </v-col>
    </v-row>

    <v-divider class="my-2" />

    <!-- Code of Conduct -->
    <v-row class="text-center py-4">
      <v-col
        class="text-h6"
        cols="4"
      >
        Code of Conduct
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20210324 - ESA Blueshell Gedragscode.pdf', 'ESA Blueshell - Gedragscode.pdf')"
        >
          Dutch
        </v-btn>
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20210324 - ESA Blueshell Code of Conduct.pdf', 'ESA Blueshell - Code of Conduct.pdf')"
        >
          English
        </v-btn>
      </v-col>
    </v-row>

    <v-divider class="my-2" />

    <!-- Cookie Policy (Only English) -->
    <v-row class="text-center py-4">
      <v-col
        class="text-h6"
        cols="4"
      >
        Cookie Policy
      </v-col>
      <v-col cols="4">
        <!-- No Dutch version available -->
      </v-col>
      <v-col cols="4">
        <v-btn
          class="w-100"
          color="primary"
          @click="downloadFile('@/assets/documents/20220813 - ESA Blueshell Cookie Policy.pdf', 'ESA Blueshell - Cookie Policy.pdf')"
        >
          English
        </v-btn>
      </v-col>
    </v-row>
  </v-sheet>
</template>

<script lang="ts" setup>
import {$require} from "@/plugins/require.ts"

function downloadFile(path: string, fileName: string) {
  const url = path.startsWith("http") ? path : $require(path)
  const link = document.createElement("a")
  link.href = url
  link.download = fileName
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}
</script>
