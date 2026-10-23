/**
 * The browser-facing tsconfig pins `types: ["vite/client"]`, so Node built-ins used by Node-env
 * tests have no declarations. Vitest runs these tests in Node; declare only the members they use.
 */
declare module "node:fs" {
  export function existsSync(path: string): boolean;
  export function readFileSync(path: string, encoding: "utf8"): string;
}

declare module "node:url" {
  export function fileURLToPath(url: string | URL): string;
}
