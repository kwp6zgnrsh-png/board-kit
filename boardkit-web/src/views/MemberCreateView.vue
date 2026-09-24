<script setup lang="ts">
import MemberCreateForm from '@/features/member/components/MemberCreateForm.vue'
</script>

<template>
  <main class="member-create-page">
    <header class="member-create-page__header">
      <p>JOIN BOARDKIT</p>
      <h1>회원가입</h1>
      <span>새로운 계정을 만들고 커뮤니티에 참여하세요.</span>
    </header>

    <MemberCreateForm />

    <p class="member-create-page__login">
      이미 계정이 있나요?
      <RouterLink :to="{ name: 'login' }">
        로그인
      </RouterLink>
    </p>
  </main>
</template>

<style scoped>
.member-create-page {
  display: grid;
  justify-items: center;
  gap: 1rem;
  width: 100%;
  padding: 1rem 0 2rem;
}

.member-create-page__header {
  width: min(520px, 100%);
  text-align: center;
}

.member-create-page__header p {
  display: inline-block;
  margin: 0 0 0.65rem;
  padding: 0.3rem 0.65rem;
  border: 2px solid #0f172a;
  background: #fde047;
  font-size: 0.65rem;
  font-weight: 900;
  letter-spacing: 0.2em;
  transform: rotate(-1deg);
}

.member-create-page__header h1 {
  margin: 0;
  color: #0f172a;
  font-family: Impact, Haettenschweiler, 'Arial Narrow Bold', sans-serif;
  font-size: clamp(2.4rem, 7vw, 3.6rem);
  line-height: 1;
  letter-spacing: 0.03em;
}

.member-create-page__header span {
  display: block;
  margin-top: 0.65rem;
  color: #475569;
  font-size: 0.9rem;
  font-weight: 700;
}

.member-create-page__login {
  margin: 0;
  color: #475569;
  font-size: 0.85rem;
  font-weight: 700;
}

.member-create-page__login a {
  margin-left: 0.25rem;
  color: #1d4ed8;
  font-weight: 900;
  text-underline-offset: 3px;
}
</style>
