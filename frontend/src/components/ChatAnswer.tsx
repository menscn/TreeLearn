import type { AnswerBlock } from "../store/workspaceData";

/** 只按夹具正文里的段落、编号与圆点渲染结构，不解析行内 Markdown、不补字。 */
export function ChatAnswer({ blocks }: { blocks: AnswerBlock[] }) {
  return (
    <div className="chat-answer">
      {blocks.map((block, index) => {
        if (block.kind === "h2") return <h2 key={`b-${index}`}>{block.text}</h2>;
        if (block.kind === "h3") return <h3 key={`b-${index}`}>{block.text}</h3>;
        if (block.kind === "list") {
          return (
            <ul key={`b-${index}`}>
              {(block.items ?? []).map((item, itemIndex) => (
                <li key={`b-${index}-${itemIndex}`}>{item}</li>
              ))}
            </ul>
          );
        }
        return <p key={`b-${index}`}>{block.text}</p>;
      })}
    </div>
  );
}
