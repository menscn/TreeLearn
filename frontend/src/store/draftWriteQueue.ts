interface DraftWrite {
  scope: string;
  owner: string | null;
  text: string;
  revision: number;
}

/** Serializes writes and replaces only snapshots that have not started for the same scope. */
export class DraftWriteQueue {
  private readonly pending = new Map<string, DraftWrite>();
  private running: Promise<void> | null = null;

  constructor(
    private readonly write: (item: DraftWrite) => Promise<void>,
    private readonly completed: (item: DraftWrite, succeeded: boolean) => void
  ) {}

  enqueue(item: DraftWrite): void {
    this.pending.set(item.scope, item);
    this.start();
  }

  async drain(): Promise<void> {
    while (this.running !== null) await this.running;
  }

  private start(): void {
    if (this.running !== null) return;
    this.running = Promise.resolve().then(() => this.run()).finally(() => {
      this.running = null;
      if (this.pending.size > 0) this.start();
    });
  }

  private async run(): Promise<void> {
    while (this.pending.size > 0) {
      const item = this.pending.values().next().value as DraftWrite;
      this.pending.delete(item.scope);
      let succeeded = false;
      try { await this.write(item); succeeded = true; } catch { /* The caller retains the failed draft. */ }
      this.completed(item, succeeded);
    }
  }
}
