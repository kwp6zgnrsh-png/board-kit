import { apiRequest } from '@/api/http'
import type {
    MemberCreateRequest,
    MemberCreateResponse,
} from '@/features/member/types/member'

export function createMember(
    request: MemberCreateRequest,
): Promise<MemberCreateResponse> {
    return apiRequest<MemberCreateResponse>(
        '/api/members',
        {
            method: 'POST',
            body: JSON.stringify(request),
        },
    )
}