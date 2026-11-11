import type { ReactNode } from "react";

/** Render common model output as React elements; raw HTML stays text. */
function inline(text: string): ReactNode[] {
  const tokens = text.split(/(`[^`\n]+`|\*\*[^*\n]+\*\*|\[[^\]\n]+\]\([^\s)]+\))/g);
  return tokens.map((token, index) => {
    if (token.startsWith("`") && token.endsWith("`") && token.length > 1) return <code key={index}>{token.slice(1, -1)}</code>;
    if (token.startsWith("**") && token.endsWith("**") && token.length > 4) return <strong key={index}>{token.slice(2, -2)}</strong>;
    const link = /^\[([^\]]+)\]\(([^)]+)\)$/.exec(token);
    if (link) {
      try {
        const url = new URL(link[2] ?? "");
        if (url.protocol === "https:" || url.protocol === "http:") return <span key={index}>{link[1]} ({link[2]})</span>;
      } catch { /* Invalid links remain plain, selectable text. */ }
    }
    return token;
  });
}

function tableCells(line: string): string[] {
  const trimmed = line.trim().replace(/^\|/, "").replace(/\|$/, "");
  return trimmed.split(/(?<!\\)\|/).map((cell) => cell.replace(/\\\|/g, "|").trim());
}

export function DesktopMarkdown({ text }: { text: string }) {
  const lines = text.replace(/\r\n/g, "\n").split("\n");
  const blocks: ReactNode[] = [];
  let index = 0;
  while (index < lines.length) {
    const line = lines[index] ?? "";
    if (/^\s*```/.test(line)) {
      const code: string[] = [];
      index += 1;
      while (index < lines.length && !/^\s*```/.test(lines[index] ?? "")) { code.push(lines[index] ?? ""); index += 1; }
      if (index < lines.length) index += 1;
      blocks.push(<pre key={`code-${index}`}><code>{code.join("\n")}</code></pre>);
      continue;
    }
    const separator = lines[index + 1];
    if (line.includes("|") && separator?.includes("|")) {
      const headings = tableCells(line);
      const rule = tableCells(separator);
      if (headings.length > 1 && headings.length === rule.length && rule.every((cell) => /^:?-{3,}:?$/.test(cell))) {
        index += 2;
        const rows: string[][] = [];
        while (index < lines.length && (lines[index] ?? "").includes("|")) {
          const cells = tableCells(lines[index] ?? "");
          if (cells.length !== headings.length) break;
          rows.push(cells); index += 1;
        }
        blocks.push(<div className="desktop-table-scroll" key={`table-${index}`}><table><thead><tr>{headings.map((heading, cellIndex) => <th key={cellIndex}>{inline(heading)}</th>)}</tr></thead><tbody>{rows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell, cellIndex) => <td key={cellIndex}>{inline(cell)}</td>)}</tr>)}</tbody></table></div>);
        continue;
      }
    }
    const heading = /^(#{1,3})\s+(.+)$/.exec(line);
    if (heading) {
      const content = inline(heading[2] ?? "");
      if (heading[1]?.length === 1) blocks.push(<h2 key={`heading-${index}`}>{content}</h2>);
      else blocks.push(<h3 key={`heading-${index}`}>{content}</h3>);
      index += 1;
      continue;
    }
    if (/^\s*[-*+]\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*[-*+]\s+/.test(lines[index] ?? "")) {
        items.push((lines[index] ?? "").replace(/^\s*[-*+]\s+/, "")); index += 1;
      }
      blocks.push(<ul key={`list-${index}`}>{items.map((item, itemIndex) => <li key={itemIndex}>{inline(item)}</li>)}</ul>);
      continue;
    }
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (index < lines.length && /^\s*\d+\.\s+/.test(lines[index] ?? "")) {
        items.push((lines[index] ?? "").replace(/^\s*\d+\.\s+/, "")); index += 1;
      }
      blocks.push(<ol key={`ordered-${index}`}>{items.map((item, itemIndex) => <li key={itemIndex}>{inline(item)}</li>)}</ol>);
      continue;
    }
    if (line.trim()) blocks.push(<p key={`paragraph-${index}`}>{inline(line)}</p>);
    index += 1;
  }
  return <div className="desktop-markdown">{blocks}</div>;
}
