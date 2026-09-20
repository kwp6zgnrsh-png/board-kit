<script setup lang="ts">
import LoginForm from '@/features/auth/components/LoginForm.vue'
</script>

<template>
  <div class="login-page">
    <div>
      <h1>관리자 로그인</h1>
      <p>게시판을 관리하려면 로그인하세요.</p>
    </div>

    <LoginForm />
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  justify-items: center;
  gap: 1.5rem;
}

.login-page h1,
.login-page p {
  margin: 0;
  text-align: center;
}

.login-page p {
  margin-top: 0.5rem;
  color: #64748b;
}
</style>