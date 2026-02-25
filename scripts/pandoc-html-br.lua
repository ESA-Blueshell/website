-- Convert HTML <br> tags in markdown to Pandoc line breaks so LaTeX/PDF output preserves them.
function RawInline(el)
  if el.format ~= "html" then
    return nil
  end

  local text = el.text:lower()
  if text:match("^%s*<br%s*/?>%s*$") then
    return pandoc.LineBreak()
  end

  return nil
end
