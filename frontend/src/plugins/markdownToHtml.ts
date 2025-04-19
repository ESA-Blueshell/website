import {marked} from 'marked'
import DOMPurify from 'dompurify'

export default function $markdownToHtml(text: string): string {
  const md: string = marked.parse(text, {async: false})
  return DOMPurify.sanitize(md)
}
