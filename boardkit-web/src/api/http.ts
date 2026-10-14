import { clearAuthSession } from '@/features/auth/services/authSession'

export class ApiError extends Error {
    constructor(
        public readonly status: number,
        message: string,
    ) {
        super(message)
        this.name = 'ApiError'
    }
}

const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS', 'TRACE'])
const CSRF_COOKIE_PREFIX = 'XSRF-TOKEN='

let csrfInitialization: Promise<void> | null = null

function readCsrfToken(): string | undefined {
    const cookie = document.cookie
        .split(';')
        .map((value) => value.trim())
        .find((value) => value.startsWith(CSRF_COOKIE_PREFIX))

    if (!cookie) return undefined

    const token = decodeURIComponent(cookie.slice(CSRF_COOKIE_PREFIX.length))
    return token || undefined
}

async function ensureCsrfToken(): Promise<string> {
    const existingToken = readCsrfToken()
    if (existingToken) return existingToken

    if (!csrfInitialization) {
        csrfInitialization = fetchApiResponse('/api/auth/csrf')
            .then(() => undefined)
            .finally(() => {
                csrfInitialization = null
            })
    }

    await csrfInitialization

    const token = readCsrfToken()
    if (!token) {
        throw new Error(
            '요청 보호 정보를 확인할 수 없습니다. 쿠키 설정을 확인하고 새로고침해 주세요.',
        )
    }

    return token
}

async function fetchApiResponse(
    url: string,
    options: RequestInit = {},
): Promise<Response> {
    const headers = new Headers(options.headers)
    const method = (options.method ?? 'GET').toUpperCase()

    if (!SAFE_METHODS.has(method)) {
        headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
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
        credentials: 'same-origin',
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

    return response
}

export async function apiRequest<T>(
    url: string,
    options: RequestInit = {},
): Promise<T> {
    const response = await fetchApiResponse(url, options)

    if (response.status === 204) {
        return undefined as T
    }

    return response.json() as Promise<T>
}

export async function apiBlobRequest(
    url: string,
    options: RequestInit = {},
): Promise<Blob> {
    const response = await fetchApiResponse(url, options)
    return response.blob()
}