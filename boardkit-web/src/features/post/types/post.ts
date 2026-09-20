export interface PostCreateRequest {
    title: string
    content: string
    categoryId: number | null
    isSecret: boolean
    secretPassword: string | null
    commentsLocked: boolean
}

export interface PostCreateResponse {
    id: number
}