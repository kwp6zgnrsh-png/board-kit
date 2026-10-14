import type {JSONContent} from '@tiptap/core'
import type {
    PostEditorImage,
    SelectedPostAttachment,
} from '@/features/post/types/post'

const SUPPORTED_NODE_TYPES = new Set([
    'doc',
    'paragraph',
    'text',
    'hardBreak',
    'heading',
    'bulletList',
    'orderedList',
    'listItem',
    'blockquote',
    'codeBlock',
    'horizontalRule',
])

export function getBodyImageNodes(document: JSONContent): JSONContent[] {
    const images: JSONContent[] = []

    function visit(node: JSONContent) {
        if (node.type === 'image') {
            images.push(node)
            return
        }

        for (const child of node.content ?? []) {
            visit(child)
        }
    }

    visit(document)
    return images
}

export function getBodyImageKeys(document: JSONContent): string[] {
    return getBodyImageNodes(document).flatMap(node => {
        const key = node.attrs?.imageKey
        return typeof key === 'string' ? [key] : []
    })
}

export function buildPostUpload(
    document: JSONContent,
    images: PostEditorImage[],
    attachments: SelectedPostAttachment[],
    retainedAttachmentIds: number[] = [],
) {
    if (document.type !== 'doc') {
        throw new Error('본문 형식이 올바르지 않습니다.')
    }

    const imageByKey = new Map(images.map(image => [image.key, image]))
    const usedKeys = new Set<string>()
    const retainedFileIds = new Set(retainedAttachmentIds)
    const files: File[] = []
    let hasText = false
    let imageCount = 0

    function normalizeNode(node: JSONContent): JSONContent {
        if (node.type === 'image') {
            const key = node.attrs?.imageKey
            const image = typeof key === 'string' ? imageByKey.get(key) : undefined

            if (!image) {
                throw new Error(
                    '본문 이미지를 확인할 수 없습니다. 이미지를 다시 선택해 주세요.',
                )
            }
            if (usedKeys.has(image.key)) {
                throw new Error('같은 첨부 이미지가 반복되었습니다. 본문을 확인해 주세요.')
            }

            usedKeys.add(image.key)
            imageCount++

            if (image.kind === 'existing') {
                if (retainedFileIds.has(image.fileId)) {
                    throw new Error('첨부 이미지 정보가 중복되었습니다. 본문을 확인해 주세요.')
                }

                retainedFileIds.add(image.fileId)
                return {
                    type: 'image',
                    attrs: {fileId: image.fileId},
                }
            }

            const fileIndex = files.length
            files.push(image.file)

            return {
                type: 'image',
                attrs: {fileIndex},
            }
        }

        if (!node.type || !SUPPORTED_NODE_TYPES.has(node.type)) {
            throw new Error('본문에 지원하지 않는 내용이 있습니다.')
        }

        const normalized: JSONContent = {type: node.type}

        if (node.type === 'text') {
            if (typeof node.text !== 'string' || node.text.length === 0) {
                throw new Error('본문 텍스트가 올바르지 않습니다.')
            }

            normalized.text = node.text
            hasText ||= node.text.trim().length > 0
        }

        if (node.attrs) {
            normalized.attrs = {...node.attrs}
        }
        if (node.marks) {
            normalized.marks = node.marks.map(mark => {
                const copied = {...mark}

                if (mark.attrs) {
                    copied.attrs = {...mark.attrs}
                }

                return copied
            })
        }
        if (node.content) {
            normalized.content = node.content.map(normalizeNode)
        }

        return normalized
    }

    const contentDocument = normalizeNode(document)
    files.push(...attachments.map(attachment => attachment.file))

    return {
        contentDocument,
        files,
        retainedFileIds: [...retainedFileIds],
        hasText,
        imageCount,
    }
}