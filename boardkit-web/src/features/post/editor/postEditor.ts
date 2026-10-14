import type {Extensions} from '@tiptap/core'
import StarterKit from '@tiptap/starter-kit'
import {PostImage} from '@/features/post/editor/postImage'

export const POST_DOCUMENT_CLASS =
    'text-sm leading-7 text-slate-800 [overflow-wrap:anywhere] ' +
    '[&_p]:my-2 [&_p]:min-h-7 [&_p]:whitespace-pre-wrap ' +
    '[&_h2]:mt-6 [&_h2]:mb-3 [&_h2]:text-xl [&_h2]:font-bold ' +
    '[&_h3]:mt-5 [&_h3]:mb-2 [&_h3]:text-lg [&_h3]:font-semibold ' +
    '[&_ul]:my-3 [&_ul]:list-disc [&_ul]:pl-6 ' +
    '[&_ol]:my-3 [&_ol]:list-decimal [&_ol]:pl-6 ' +
    '[&_li]:my-1 [&_li>p]:my-0 ' +
    '[&_blockquote]:my-4 [&_blockquote]:border-l-4 ' +
    '[&_blockquote]:border-slate-300 [&_blockquote]:pl-4 ' +
    '[&_blockquote]:text-slate-600 ' +
    '[&_code]:rounded [&_code]:bg-slate-100 [&_code]:px-1 ' +
    '[&_code]:font-mono [&_code]:text-[0.9em] ' +
    '[&_pre]:my-4 [&_pre]:overflow-x-auto [&_pre]:rounded-md ' +
    '[&_pre]:bg-slate-900 [&_pre]:p-4 [&_pre]:text-slate-100 ' +
    '[&_pre_code]:bg-transparent [&_pre_code]:p-0 [&_pre_code]:text-inherit ' +
    '[&_a]:text-blue-600 [&_a]:underline [&_a]:underline-offset-2 ' +
    '[&_hr]:my-6 [&_hr]:border-slate-200'

export function normalizeWebLink(value: string): string | null {
    const href = value.trim()

    if (!/^https?:\/\//i.test(href) || /[\s\\]/u.test(href)) {
        return null
    }

    try {
        const url = new URL(href)

        if (
            !['http:', 'https:'].includes(url.protocol) ||
            !url.hostname
        ) {
            return null
        }

        return url.href
    } catch {
        return null
    }
}

export function createPostEditorExtensions(): Extensions {
    return [
        StarterKit.configure({
            heading: {
                levels: [2, 3],
            },
            link: {
                openOnClick: false,
                defaultProtocol: 'https',
                HTMLAttributes: {
                    target: '_blank',
                    rel: 'noopener noreferrer nofollow',
                },
                isAllowedUri: (href, context) =>
                    context.defaultValidate(href) && normalizeWebLink(href) !== null,
            },
        }),
        PostImage,
    ]
}