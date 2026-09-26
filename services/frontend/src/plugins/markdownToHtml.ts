import DOMPurify from "dompurify"
import {discordMarked} from "@/plugins/discordMarkdown"

export default function $markdownToHtml(text: string): string {
  return DOMPurify.sanitize(discordMarked.parse(text) as string)
}
