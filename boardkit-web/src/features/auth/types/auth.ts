export type MemberRole = 'MEMBER' | 'ADMIN'

export interface LoginRequest {
    loginId: string
    password: string
}

export interface AuthSession {
    memberId: number
    displayName: string
    role: MemberRole
}

export type LoginResponse = AuthSession
export type CurrentMemberResponse = AuthSession