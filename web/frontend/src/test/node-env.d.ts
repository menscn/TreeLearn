// 测试运行环境（Vitest on Node）类型补充。测试需要读取 web/contracts/fixtures/**，
// 但项目 tsconfig 未加载 @types/node；这里只声明本任务用到的极小成员，避免为测试引入
// 新的类型依赖（仅测试可见，不进入生产运行时）。
declare module 'node:fs' {
  export function readFileSync(path: string, encoding: 'utf8'): string;
  export function readdirSync(path: string): string[];
}

declare module 'node:path' {
  export function join(...parts: string[]): string;
}

declare const process: { cwd: () => string };