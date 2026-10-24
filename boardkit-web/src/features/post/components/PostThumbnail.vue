<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { getPostFileBlob } from '@/api/postApi'
import CommunityIcon from '@/components/ui/CommunityIcon.vue'

const props = defineProps<{
  boardSlug: string
  postId: number
  fileId: number
}>()

const imageUrl = ref<string | null>(null)
const loading = ref(false)
const failed = ref(false)
let requestSequence = 0

function releaseImage() {
  if (imageUrl.value) {
    URL.revokeObjectURL(imageUrl.value)
    imageUrl.value = null
  }
}

watch(
    () => [props.boardSlug, props.postId, props.fileId] as const,
    async ([slug, postId, fileId]) => {
      const currentRequest = ++requestSequence
      releaseImage()
      loading.value = true
      failed.value = false

      try {
        const blob = await getPostFileBlob(slug, postId, fileId, {
          thumbnail: true,
        })

        if (currentRequest !== requestSequence) return
        imageUrl.value = URL.createObjectURL(blob)
      } catch {
        if (currentRequest === requestSequence) {
          failed.value = true
        }
      } finally {
        if (currentRequest === requestSequence) {
          loading.value = false
        }
      }
    },
    { immediate: true },
)

onBeforeUnmount(() => {
  ++requestSequence
  releaseImage()
})
</script>

<template>
  <div class="grid shrink-0 place-items-center overflow-hidden bg-zinc-50">
    <img
        v-if="imageUrl"
        :src="imageUrl"
        alt=""
        class="size-full object-cover"
        @error="failed = true; releaseImage()"
    />
    <span
        v-else-if="loading"
        class="size-full animate-pulse bg-zinc-100"
        role="status"
        aria-label="썸네일을 불러오는 중"
    />
    <CommunityIcon
        v-else
        name="image"
        class="size-5 text-zinc-300"
        role="img"
        :aria-hidden="false"
        :aria-label="failed ? '썸네일을 불러오지 못했습니다' : '이미지 없음'"
    />
  </div>
</template>