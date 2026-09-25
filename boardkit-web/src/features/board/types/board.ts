export type BoardDisplayType = 'LIST' | 'CARD'
export type ReadPermission = 'PUBLIC' | 'MEMBER' | 'ADMIN'
export type WritePermission = 'MEMBER' | 'ADMIN'
export type SecretMode = 'OFF' | 'OPTIONAL' | 'REQUIRED'
export type UploadProfile = 'IMAGE' | 'IMAGE_AND_ZIP'
export type RepresentativeImageMode = 'FIRST_IMAGE' | 'MANUAL'
export type BoardPolicyResponse = BoardPolicyRequest

export interface BoardPolicyRequest {
  readPermission: ReadPermission
  writePermission: WritePermission
  commentPermission: WritePermission
  secretMode: SecretMode
  categoriesEnabled: boolean
  categoryRequired: boolean
  attachmentsEnabled: boolean
  uploadProfile: UploadProfile
  maxFileCount: number
  maxFileSizeBytes: number
  imageRequired: boolean
  thumbnailEnabled: boolean
  representativeImageMode: RepresentativeImageMode
  commentsEnabled: boolean
  commentRepliesEnabled: boolean
  postLikesEnabled: boolean
  commentLikesEnabled: boolean
  officialReplyEnabled: boolean
}

export interface BoardCategoryCreateRequest {
  name: string
  sortOrder: number
}

export interface BoardCreateRequest {
  name: string
  slug: string
  description: string
  displayType: BoardDisplayType
  sortOrder: number
  policy: BoardPolicyRequest
  categories: BoardCategoryCreateRequest[]
}

export interface BoardCreateResponse {
  id: number
  name: string
  slug: string
}

export interface BoardCategoryResponse
    extends BoardCategoryCreateRequest {
  id: number
}

export interface BoardSummaryResponse {
  id: number
  name: string
  slug: string
  description: string | null
  displayType: BoardDisplayType
  sortOrder: number
}

export interface BoardDetailResponse {
  id: number
  name: string
  slug: string
  description: string | null
  displayType: BoardDisplayType
  sortOrder: number
  policy: BoardPolicyResponse
  categories: BoardCategoryResponse[]
}

export interface BoardCategoryUpdateRequest {
  id: number | null
  name: string
  sortOrder: number
}

export interface BoardUpdateRequest {
  name: string
  description: string
  displayType: BoardDisplayType
  sortOrder: number
  policy: BoardPolicyRequest
  categories: BoardCategoryUpdateRequest[]
  version: number
}

export interface BoardAdminDetailResponse
    extends BoardDetailResponse {
  version: number
}