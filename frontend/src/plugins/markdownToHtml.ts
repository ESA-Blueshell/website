import { marked } from 'marked'
import * as emoji from 'node-emoji'
import DOMPurify from 'dompurify'

export default function $markdownToHtml(text: string): string {
  const withEmoji = emoji.emojify(text)


  const html = marked.parse(withEmoji, {
    gfm: true,
    breaks: true,
    async: false
  })

  return DOMPurify.sanitize(html)
}
