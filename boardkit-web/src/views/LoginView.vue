<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

import LoginForm from '@/features/auth/components/LoginForm.vue'

const route = useRoute()

const registrationCompleted = computed(
    () => route.query.registered === 'true',
)
</script>

<template>
  <div class="login-page">
    <div>
      <h1>로그인</h1>
      <p>로그인하고 커뮤니티에 참여하세요.</p>
    </div>

    <p
        v-if="registrationCompleted"
        class="login-page__success"
        role="status"
    >
      회원가입이 완료되었습니다. 로그인해주세요.
    </p>

    <LoginForm />

    <p class="login-page__signup">
      아직 계정이 없나요?
      <RouterLink :to="{ name: 'member-create' }">
        회원가입
      </RouterLink>
    </p>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  justify-items: center;
  gap: 1rem;
  width: 100%;
  padding: 2rem 0;
}

.login-page > div {
  text-align: center;
}

.login-page h1 {
  margin: 0;
  color: #0f172a;
  font-family: Impact, Haettenschweiler, 'Arial Narrow Bold', sans-serif;
  font-size: clamp(2.5rem, 7vw, 3.7rem);
  line-height: 1;
  letter-spacing: 0.03em;
}

.login-page > div p {
  margin: 0.65rem 0 0;
  color: #475569;
  font-size: 0.9rem;
  font-weight: 700;
}

.login-page__success {
  width: min(440px, 100%);
  margin: 0;
  padding: 0.75rem 1rem;
  border: 2px solid #14532d;
  border-radius: 0.35rem;
  color: #14532d;
  background: #dcfce7;
  font-size: 0.8rem;
  font-weight: 900;
  text-align: center;
}

.login-page__signup {
  margin: 0;
  color: #475569;
  font-size: 0.85rem;
  font-weight: 700;
}

.login-page__signup a {
  margin-left: 0.25rem;
  color: #1d4ed8;
  font-weight: 900;
  text-underline-offset: 3px;
}
</style>
