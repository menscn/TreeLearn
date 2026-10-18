import { copyText, type CopyKey } from "../copy";

/** 一屏读不到基线时的大声说明：文案一律走文案表，页面不得自己拼句子。 */
export function Notice({ copyKey }: { copyKey: CopyKey }) {
  return (
    <p className="notice" data-layer="components" data-copy={copyKey}>
      {copyText(copyKey)}
    </p>
  );
}
