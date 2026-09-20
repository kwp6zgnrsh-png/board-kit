import type {
    AuthSession,
    LoginResponse,
} from '@/features/auth/types/auth'

const STORAGE_KEY = 'boardkit.auth'

export function saveAuthSession(response: LoginResponse): void {
    const session: AuthSession = {
        accessToken: response.accessToken,
        memberId: response.memberId,
        displayName: response.displayName,
        role: response.role,
    }

    sessionStorage.setItem(STORAGE_KEY, JSON.stringify(session))
}

export function getAuthSession(): AuthSession | null {
    const value = sessionStorage.getItem(STORAGE_KEY)

    if (!value) {
        return null
    }

    try {
        return JSON.parse(value) as AuthSession
    } catch {
        sessionStorage.removeItem(STORAGE_KEY)
        return null
    }
}

export function getAccessToken(): string | null {
    return getAuthSession()?.accessToken ?? null
}

export function clearAuthSession(): void {
    sessionStorage.removeItem(STORAGE_KEY)
}