export type BoardDisplayType = 'LIST' | 'CARD'
export type ReadPermission = 'PUBLIC' | 'MEMBER' | 'ADMIN'
export type WritePermission = 'MEMBER' | 'ADMIN'
export type SecretMode = 'OFF' | 'OPTIONAL' | 'REQUIRED'
export type UploadProfile = 'IMAGE' | 'IMAGE_AND_ZIP'
export type RepresentativeImageMode = 'FIRST_IMAGE' | 'MANUAL'

export interface BoardPolicyCreateRequest {
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
  policy: BoardPolicyCreateRequest
  categories: BoardCategoryCreateRequest[]
}

export interface BoardCreateResponse {
  id: number
  name: string
  slug: string
}
