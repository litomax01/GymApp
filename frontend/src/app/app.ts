import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ToastService } from './core/toast.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet],
  template: `
    <router-outlet />
    <div class="toasts" aria-live="polite" aria-atomic="false">
      @for (t of toasts.toasts(); track t.id) {
        <div class="toast" [class.toast-error]="t.kind === 'error'" [attr.role]="t.kind === 'error' ? 'alert' : 'status'">
          <span>{{ t.text }}</span>
          <button type="button" class="toast-close" (click)="toasts.dismiss(t.id)" aria-label="Cerrar aviso">×</button>
        </div>
      }
    </div>
  `,
})
export class App {
  protected readonly toasts = inject(ToastService);
}
