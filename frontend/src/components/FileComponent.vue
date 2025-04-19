<script lang="ts" setup>
import { ref } from 'vue';
import { FileType } from '@/models/enums/FileType';
import type FileModel from '@/models/FileModel.ts';

// References and reactive state
const fileInput = ref<HTMLInputElement | null>(null);
const selectedFile = ref<FileModel | null>(null);
const previewUrl = ref<string | null>(null);
// Default file type (can be changed via the select)
const fileType = ref<FileType>(FileType.DOCUMENT);

// Called when a file is selected from the input element
function onFileSelected(event: Event) {
  const target = event.target as HTMLInputElement;
  if (target.files && target.files[0]) {
    const file = target.files[0];
    const reader = new FileReader();
    reader.onload = (e: ProgressEvent<FileReader>) => {
      if (e.target?.result) {
        // Save the base64 content for preview and upload
        previewUrl.value = e.target.result as string;
        selectedFile.value = {
          name: file.name,
          fileName: file.name,
          fileType: fileType.value,
          base64Content: previewUrl.value
          // Note: size, uploaderId, url, createdAt, id, and mediaType are not set on upload.
        };
      }
    };
    reader.readAsDataURL(file);
  }
}

// Simulate an upload action (replace with an actual API call)
function uploadFile() {
  if (selectedFile.value) {
    console.log("Uploading file:", selectedFile.value);
    // e.g., await api.uploadFile(selectedFile.value);
  } else {
    alert("Please select a file first.");
  }
}

// Create a download link and simulate a download
function downloadFile() {
  if (selectedFile.value && selectedFile.value.base64Content) {
    const link = document.createElement('a');
    link.href = selectedFile.value.base64Content;
    link.download = selectedFile.value.fileName || 'download';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  } else {
    alert("No file available for download.");
  }
}
</script>

<template>
  <div class="file-component">
    <!-- Upload Section -->
    <div class="upload-section">
      <label for="fileInput">Select FileModel:</label>
      <input
        id="fileInput"
        ref="fileInput"
        type="file"
        @change="onFileSelected"
      >

      <label for="fileTypeSelect">Select FileModel Type:</label>
      <select
        id="fileTypeSelect"
        v-model="fileType"
      >
        <option
          v-for="type in Object.values(FileType)"
          :key="type"
          :value="type"
        >
          {{ type }}
        </option>
      </select>

      <button @click="uploadFile">
        Upload FileModel
      </button>
    </div>

    <!-- Download Section -->
    <div
      v-if="selectedFile"
      class="download-section"
    >
      <h3>FileModel: {{ selectedFile.name }}</h3>
      <!-- Preview image if file type is not DOCUMENT -->
      <img
        v-if="selectedFile.fileType !== FileType.DOCUMENT && previewUrl"
        :src="previewUrl"
        alt="Preview"
        style="max-width:200px; max-height:200px;"
      >
      <button @click="downloadFile">
        Download FileModel
      </button>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';

export default defineComponent({
  name: "FileComponent"
});
</script>

<style scoped>
.file-component {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.upload-section,
.download-section {
  border: 1px solid #ccc;
  padding: 1rem;
}
</style>
