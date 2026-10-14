import {apiRequest} from '@/api/http'
import type {
    CurrentMemberResponse,
    LoginRequest,
    LoginResponse,
} from '@/features/auth/types/auth'

export function login(request: LoginRequest): Promise<LoginResponse> {
    return apiRequest<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(request),
    })
}

export function getCurrentMember(): Promise<CurrentMemberResponse> {
    return apiRequest<CurrentMemberResponse>('/api/auth/me')
}

export function logout(): Promise<void> {
    return apiRequest<void>('/api/auth/logout', {
        method: 'POST',
    })
}