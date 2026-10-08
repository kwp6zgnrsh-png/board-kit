import type { JSONContent } from '@tiptap/core'
import type { PostEditorImage, SelectedPostAttachment } from '@/features/post/types/post'

export function getBodyImageKeys(document: JSONContent): string[] {
    return (document.content ?? []).flatMap(block => {
        const key = block.attrs?.imageKey
        return block.type === 'image' && typeof key === 'string' ? [key] : []
    })
}

export function buildPostUpload(
    document: JSONContent,
    images: PostEditorImage[],
    attachments: SelectedPostAttachment[],
    retainedAttachmentIds: number[] = [],
) {
    const imageByKey = new Map(images.map(image => [image.key, image]))
    const usedKeys = new Set<string>()
    const retainedFileIds = new Set(retainedAttachmentIds)
    const files: File[] = []
    const blocks: JSONContent[] = []
    let hasText = false
    let imageCount = 0

    for (const block of document.content ?? []) {
        if (block.type === 'paragraph') {
            const content: JSONContent[] = []

            for (const inline of block.content ?? []) {
                if (inline.type === 'text' && typeof inline.text === 'string') {
                    content.push({ type: 'text', text: inline.text })
                    hasText ||= inline.text.trim().length > 0
                } else if (inline.type === 'hardBreak') {
                    content.push({ type: 'hardBreak' })
                } else {
                    throw new Error('본문에 지원하지 않는 내용이 있습니다.')
                }
            }

            blocks.push({ type: 'paragraph', content })
            continue
        }

        if (block.type !== 'image') {
            throw new Error('본문에 지원하지 않는 내용이 있습니다.')
        }

        const key = block.attrs?.imageKey
        const image = typeof key === 'string' ? imageByKey.get(key) : undefined

        if (!image) {
            throw new Error('본문 이미지를 확인할 수 없습니다. 이미지를 다시 선택해 주세요.')
        }
        if (usedKeys.has(image.key)) {
            throw new Error('같은 첨부 이미지가 반복되었습니다. 본문을 확인해 주세요.')
        }

        usedKeys.add(image.key)

        if (image.kind === 'existing') {
            if (retainedFileIds.has(image.fileId)) {
                throw new Error('첨부 이미지 정보가 중복되었습니다. 본문을 확인해 주세요.')
            }

            retainedFileIds.add(image.fileId)
            blocks.push({
                type: 'image',
                attrs: { fileId: image.fileId },
            })
        } else {
            blocks.push({
                type: 'image',
                attrs: { fileIndex: files.length },
            })
            files.push(image.file)
        }

        imageCount++
    }

    files.push(...attachments.map(attachment => attachment.file))

    return {
        contentDocument: { type: 'doc', content: blocks } satisfies JSONContent,
        files,
        retainedFileIds: [...retainedFileIds],
        hasText,
        imageCount,
    }
}