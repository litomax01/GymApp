import { CurrencyPipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { applyFieldErrors, controlError, errorMessage } from '../../core/errors';
import { Plan } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-plans',
  imports: [CurrencyPipe, ReactiveFormsModule],
  template: `
    <div class="page">
      <div class="page-head">
        <div><h1>Planes</h1><p>Un cambio de precio solo afecta a las próximas membresías.</p></div>
        @if (auth.isAdmin() && !editing()) { <button class="btn" type="button" (click)="startEdit(null)">Nuevo plan</button> }
      </div>

      @if (editing()) {
        <form class="panel form" [formGroup]="form" (ngSubmit)="save()" novalidate style="margin-bottom:1.5rem">
          @if (formError()) { <div class="alert-box field wide" role="alert">{{ formError() }}</div> }
          <div class="field"><label for="name">Nombre</label>
            <input id="name" formControlName="name" [attr.aria-invalid]="!!err('name')" />
            @if (err('name'); as e) { <span class="error">{{ e }}</span> }</div>
          <div class="field"><label for="price">Precio (€)</label>
            <input id="price" type="number" step="0.01" formControlName="price" [attr.aria-invalid]="!!err('price')" />
            @if (err('price'); as e) { <span class="error">{{ e }}</span> }</div>
          <div class="field"><label for="durationDays">Duración (días)</label>
            <input id="durationDays" type="number" step="1" formControlName="durationDays" [attr.aria-invalid]="!!err('durationDays')" />
            @if (err('durationDays'); as e) { <span class="error">{{ e }}</span> }</div>
          <div class="form-actions">
            <button class="btn" type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar plan' }}</button>
            <button class="btn btn-quiet" type="button" (click)="editing.set(false)">Cancelar</button>
          </div>
        </form>
      }

      @if (loading()) {
        <div class="skeleton" style="height:8rem" role="status" aria-label="Cargando planes"></div>
      } @else if (error()) {
        <div class="alert-box" role="alert"><span>{{ error() }}</span><button class="btn btn-quiet btn-small" type="button" (click)="load()">Reintentar</button></div>
      } @else if (plans().length === 0) {
        <div class="state"><strong>No hay planes</strong>
          @if (auth.isAdmin()) { Crea el primero para poder contratar membresías. } @else { Pide a administración que cree los planes. }</div>
      } @else {
        <div class="table-wrap">
          <table>
            <caption class="sr-only">Planes de membresía</caption>
            <thead><tr><th>Plan</th><th>Duración</th><th class="right">Precio</th><th>Estado</th>@if (auth.isAdmin()) { <th class="right">Acciones</th> }</tr></thead>
            <tbody>
              @for (p of plans(); track p.id) {
                <tr>
                  <td><strong>{{ p.name }}</strong></td>
                  <td class="num">{{ p.durationDays }} días</td>
                  <td class="right num">{{ p.price | currency: 'EUR' }}</td>
                  <td><span class="badge" [class.badge-go]="p.active" [class.badge-idle]="!p.active">{{ p.active ? 'Disponible' : 'Retirado' }}</span></td>
                  @if (auth.isAdmin()) {
                    <td class="right actions" style="justify-content:flex-end">
                      <button class="btn btn-quiet btn-small" type="button" (click)="startEdit(p)">Editar</button>
                      <button class="btn btn-quiet btn-small" type="button" (click)="toggle(p)">{{ p.active ? 'Retirar' : 'Reactivar' }}</button>
                    </td>
                  }
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
})
export class Plans implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);

  protected readonly plans = signal<Plan[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  private editingId: number | null = null;

  protected readonly form = inject(FormBuilder).nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    price: [0, [Validators.required, Validators.min(0), Validators.max(99999.99)]],
    durationDays: [30, [Validators.required, Validators.min(1), Validators.max(1830)]],
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.listPlans(!this.auth.isAdmin()).subscribe({
      next: (p) => { this.plans.set(p); this.loading.set(false); },
      error: (e: unknown) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  protected err(name: string): string | null {
    return controlError(this.form, name);
  }

  protected startEdit(p: Plan | null): void {
    this.editingId = p?.id ?? null;
    this.formError.set(null);
    this.form.reset({ name: p?.name ?? '', price: p?.price ?? 0, durationDays: p?.durationDays ?? 30 });
    this.editing.set(true);
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const body = this.form.getRawValue();
    this.saving.set(true);
    this.formError.set(null);
    const req = this.editingId === null ? this.api.createPlan(body) : this.api.updatePlan(this.editingId, body);
    req.subscribe({
      next: () => { this.saving.set(false); this.editing.set(false); this.toast.ok('Plan guardado'); this.load(); },
      error: (e: unknown) => { this.saving.set(false); if (!applyFieldErrors(e, this.form)) this.formError.set(errorMessage(e)); },
    });
  }

  protected toggle(p: Plan): void {
    this.api.setPlanActive(p.id, !p.active).subscribe({
      next: () => { this.toast.ok(p.active ? 'Plan retirado' : 'Plan reactivado'); this.load(); },
      error: (e: unknown) => this.toast.error(errorMessage(e)),
    });
  }
}
