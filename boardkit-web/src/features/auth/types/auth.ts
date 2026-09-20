export type MemberRole = 'MEMBER' | 'ADMIN'

export interface LoginRequest {
    loginId: string
    password: string
}

export interface LoginResponse {
    accessToken: string
    tokenType: 'Bearer'
    memberId: number
    displayName: string
    role: MemberRole
}

export interface AuthSession {
    accessToken: string
    memberId: number
    displayName: string
    role: MemberRole
}