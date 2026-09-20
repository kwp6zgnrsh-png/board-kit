import {
    clearAuthSession,
    getAccessToken,
} from '@/features/auth/services/authSession'

export class ApiError extends Error {
    constructor(
        public readonly status: number,
        message: string,
    ) {
        super(message)
    }
}

export async function apiRequest<T>(
    url: string,
    options: RequestInit = {},
): Promise<T> {
    const headers = new Headers(options.headers)
    const accessToken = getAccessToken()

    if (accessToken) {
        headers.set('Authorization', `Bearer ${accessToken}`)
    }

    if (
        options.body != null &&
        !(options.body instanceof FormData) &&
        !headers.has('Content-Type')
    ) {
        headers.set('Content-Type', 'application/json')
    }

    const response = await fetch(url, {
        ...options,
        headers,
    })

    if (!response.ok) {
        const body = (await response.json().catch(() => null)) as {
            message?: string
        } | null

        if (response.status === 401 && url !== '/api/auth/login') {
            clearAuthSession()
        }

        throw new ApiError(
            response.status,
            body?.message ?? `요청에 실패했습니다. (${response.status})`,
        )
    }

    if (response.status === 204) {
        return undefined as T
    }

    return response.json() as Promise<T>
}