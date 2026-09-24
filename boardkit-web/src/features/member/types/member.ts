export interface MemberCreateRequest {
    loginId: string
    password: string
    passwordConfirm: string
    displayName: string
}

export interface MemberCreateResponse {
    id: number
    loginId: string
    displayName: string
}