import { shallowRef } from 'vue'
import type { AuthSession } from '@/features/auth/types/auth'

sessionStorage.removeItem('boardkit.auth')

const currentSession = shallowRef<AuthSession | null>(null)

export function saveAuthSession(session: AuthSession): void {
    currentSession.value = {
        memberId: session.memberId,
        displayName: session.displayName,
        role: session.role,
    }
}

export function getAuthSession(): AuthSession | null {
    return currentSession.value
}

export function clearAuthSession(): void {
    currentSession.value = null
}