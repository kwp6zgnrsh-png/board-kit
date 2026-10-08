import { Node, mergeAttributes } from '@tiptap/core'

export const PostImage = Node.create({
    name: 'image',
    group: 'block',
    atom: true,
    selectable: true,
    draggable: false,

    addAttributes() {
        return {
            imageKey: {
                default: null,
                rendered: false,
            },
            src: {
                default: null,
            },
            alt: {
                default: '',
            },
        }
    },

    parseHTML() {
        return []
    },

    renderHTML({ HTMLAttributes }) {
        return [
            'img',
            mergeAttributes(HTMLAttributes, {
                draggable: 'false',
                class:
                    'mx-auto my-3 block h-auto max-w-full rounded ' +
                    '[&.ProseMirror-selectednode]:outline-2 ' +
                    '[&.ProseMirror-selectednode]:outline-blue-500',
            }),
        ]
    },
})