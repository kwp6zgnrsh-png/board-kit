<script setup lang="ts">
import BoardCreateForm from '@/features/board/components/BoardCreateForm.vue'
</script>

<template>
  <div class="page">
    <header class="page-header">
      <div>
        <p class="eyebrow">BOARD MANAGEMENT</p>
        <h1>새 게시판 만들기</h1>
        <p class="description">
          게시판 정보와 정책을 설정하면 별도의 배포 없이 바로 사용할 수 있습니다.
        </p>
      </div>
      <span class="status-badge">관리자</span>
    </header>

    <BoardCreateForm />
  </div>
</template>

<style scoped>
.page {
  display: grid;
  gap: 1.5rem;
}

.page-header {
  display: flex;
  gap: 1rem;
  align-items: flex-start;
  justify-content: space-between;
}

.eyebrow,
.page-header h1,
.description {
  margin: 0;
}

.eyebrow {
  margin-bottom: 0.45rem;
  color: #2563eb;
  font-size: 0.75rem;
  font-weight: 800;
  letter-spacing: 0.12em;
}

.page-header h1 {
  color: #0f172a;
  font-size: clamp(1.75rem, 4vw, 2.5rem);
  letter-spacing: -0.04em;
}

.description {
  margin-top: 0.65rem;
  color: #64748b;
}

.status-badge {
  padding: 0.45rem 0.75rem;
  border-radius: 999px;
  color: #1d4ed8;
  background: #dbeafe;
  font-size: 0.8rem;
  font-weight: 800;
}
</style>
