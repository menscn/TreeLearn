/** Extract the static Android persona policy table without executing arbitrary Java source. */
export function extractPersonaPrompts(personaSource, promptSource) {
  const section = personaSource.match(/public enum Persona\s*\{([\s\S]*?);/);
  if (!section) throw new Error('回答风格标识来源不可用');
  const entries = [...section[1].matchAll(/\b([A-Z_]+)\("([a-z][a-z-]*)"\)/g)];
  const expected = ['default', 'hand-holding', 'strict-mentor', 'study-buddy', 'sarcastic-friend', 'late-night', 'learning-repair'];
  if (entries.length !== expected.length || entries.some((entry, index) => entry[2] !== expected[index])) throw new Error('回答风格标识与桌面契约不一致');
  const cases = new Map();
  for (const match of promptSource.matchAll(/\bcase\s+([A-Z_]+)\s*->\s*([\s\S]*?);/g)) {
    if (cases.has(match[1])) throw new Error('回答风格提示词重复');
    const literals = [...match[2].matchAll(/"(?:\\.|[^"\\])*"/g)];
    const residue = match[2].replace(/"(?:\\.|[^"\\])*"/g, '').replace(/[\s+]/g, '');
    if (!literals.length || residue) throw new Error('回答风格提示词格式不受支持');
    cases.set(match[1], literals.map(value => JSON.parse(value[0])).join(''));
  }
  if (cases.size !== entries.length) throw new Error('回答风格提示词不完整');
  const result = Object.create(null);
  for (const [, name, wireId] of entries) {
    if (!cases.has(name)) throw new Error('回答风格提示词缺失');
    result[wireId] = cases.get(name);
    if (wireId === 'default' ? result[wireId] !== '' : !result[wireId].trim()) throw new Error('回答风格提示词内容无效');
  }
  return result;
}
