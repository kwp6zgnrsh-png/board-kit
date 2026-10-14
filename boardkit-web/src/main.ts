import './assets/main.css'

import {createApp} from 'vue'
import App from './App.vue'
import router from './router'
import {getCurrentMember} from '@/api/authApi'
import {ApiError} from '@/api/http'
import {
    clearAuthSession,
    saveAuthSession,
} from '@/features/auth/services/authSession'

async function startApp(): Promise<void> {
    try {
        const member = await getCurrentMember()
        saveAuthSession(member)
    } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
            clearAuthSession()
        } else {
            const root = document.getElementById('app')

            if (root) {
                root.textContent =
                    '로그인 상태를 확인하지 못했습니다. 서버 연결을 확인한 뒤 새로고침해 주세요.'
                root.className = 'p-6 text-center text-sm text-slate-700'
            }

            console.error('로그인 상태 복원 실패:', error)
            return
        }
    }

    createApp(App).use(router).mount('#app')
}

void startApp()