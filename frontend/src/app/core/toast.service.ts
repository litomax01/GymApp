import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'ok' | 'error';
  text: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);

  ok(text: string): void {
    this.push('ok', text, 3500);
  }

  error(text: string): void {
    this.push('error', text, 7000);
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(kind: Toast['kind'], text: string, ms: number): void {
    const id = ++this.seq;
    this.toasts.update((list) => [...list, { id, kind, text }]);
    setTimeout(() => this.dismiss(id), ms);
  }
}
