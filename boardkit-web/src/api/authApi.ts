import { apiRequest } from '@/api/http'
import type {
    LoginRequest,
    LoginResponse,
} from '@/features/auth/types/auth'

export function login(request: LoginRequest): Promise<LoginResponse> {
    return apiRequest<LoginResponse>('/api/auth/login', {
        method: 'POST',
        body: JSON.stringify(request),
    })
}