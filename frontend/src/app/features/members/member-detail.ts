import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { AuthService } from '../../core/auth.service';
import { applyFieldErrors, controlError, errorMessage } from '../../core/errors';
import { METHOD_LABEL, STATE_BADGE, STATE_LABEL } from '../../core/format';
import { Member, Membership, PaymentMethod, Plan } from '../../core/models';
import { ToastService } from '../../core/toast.service';

type Panel = 'none' | 'contract' | { pay: number };

@Component({
  selector: 'app-member-detail',
  imports: [CurrencyPipe, DatePipe, ReactiveFormsModule, RouterLink],
  styles: `
    .grid { display: grid; gap: 0.25rem 2rem; grid-template-columns: repeat(auto-fit, minmax(13rem, 1fr)); margin: 0; }
    .grid dt { color: var(--ink-soft); font-size: 0.9rem; }
    .grid dd { margin: 0 0 0.75rem; font-weight: 500; }
    .ms { border-top: 2px solid var(--ink); }
    .ms-row { display: grid; gap: 0.5rem 1rem; padding: 1rem 0; border-bottom: 1px solid var(--line); }
    @media (min-width: 48rem) { .ms-row { grid-template-columns: 1.2fr 1.4fr 1fr auto; align-items: center; } }
    .ms-row h3 { margin-bottom: 0.15rem; }
    .pays { margin: 0.25rem 0 0; padding: 0; list-style: none; color: var(--ink-soft); font-size: 0.9rem; grid-column: 1 / -1; }
    .inline { background: var(--paper); border: 2px dashed var(--ink-soft); border-radius: var(--radius); padding: 1rem; grid-column: 1 / -1; }
    .debt { color: var(--stop); font-weight: 600; }
  `,
  template: `
    <div class="page">
      @if (loading()) {
        <div class="skeleton" style="height:14rem" role="status" aria-label="Cargando ficha"></div>
      } @else if (error()) {
        <div class="alert-box" role="alert"><span>{{ error() }}</span>
          <span class="actions"><button class="btn btn-quiet btn-small" type="button" (click)="load()">Reintentar</button>
          <a class="btn btn-quiet btn-small" routerLink="/socios">Volver a socios</a></span></div>
      } @else if (member(); as m) {
        <div class="page-head">
          <div>
            <h1>{{ m.firstName }} {{ m.lastName }}</h1>
            <p><span class="badge" [class.badge-go]="m.active" [class.badge-idle]="!m.active">{{ m.active ? 'Activo' : 'De baja' }}</span></p>
          </div>
          <div class="actions">
            <a class="btn btn-quiet" [routerLink]="['/socios', m.id, 'editar']">Editar datos</a>
            @if (auth.isAdmin()) {
              @if (confirmToggle()) {
                <span role="group" aria-label="Confirmar">
                  <button class="btn btn-danger" type="button" (click)="toggleActive()">{{ m.active ? 'Sí, dar de baja' : 'Sí, reactivar' }}</button>
                  <button class="btn btn-quiet" type="button" (click)="confirmToggle.set(false)">No</button>
                </span>
              } @else {
                <button class="btn btn-quiet" type="button" (click)="confirmToggle.set(true)">{{ m.active ? 'Dar de baja' : 'Reactivar' }}</button>
              }
            }
          </div>
        </div>

        <section class="panel" aria-label="Datos del socio">
          <dl class="grid">
            <div><dt>Documento</dt><dd class="num">{{ m.documentNumber }}</dd></div>
            <div><dt>Teléfono</dt><dd>{{ m.phone || '—' }}</dd></div>
            <div><dt>Email</dt><dd>{{ m.email || '—' }}</dd></div>
            <div><dt>Nacimiento</dt><dd>{{ m.birthDate ? (m.birthDate | date: 'd MMM y') : '—' }}</dd></div>
            <div><dt>Socio desde</dt><dd>{{ m.createdAt | date: 'd MMM y' }}</dd></div>
            @if (m.notes) { <div style="grid-column:1/-1"><dt>Notas</dt><dd>{{ m.notes }}</dd></div> }
          </dl>
        </section>

        <section class="section" aria-labelledby="ms-title">
          <div class="section-head">
            <h2 id="ms-title">Membresías y pagos</h2>
            @if (m.active) {
              <button class="btn" type="button" (click)="openContract()">{{ hasCurrent() ? 'Renovar membresía' : 'Contratar membresía' }}</button>
            }
          </div>

          @if (panel() === 'contract') {
            <form class="panel form" [formGroup]="contractForm" (ngSubmit)="contract()" novalidate style="margin-bottom:1rem">
              @if (formError()) { <div class="alert-box field wide" role="alert">{{ formError() }}</div> }
              <div class="field">
                <label for="planId">Plan</label>
                <select id="planId" formControlName="planId" (change)="onPlanChange()" [attr.aria-invalid]="!!cerr('planId')">
                  <option [ngValue]="null" disabled>Elige un plan</option>
                  @for (p of plans(); track p.id) {
                    <option [ngValue]="p.id">{{ p.name }} · {{ p.durationDays }} días · {{ p.price | currency: 'EUR' }}</option>
                  }
                </select>
                @if (cerr('planId'); as e) { <span class="error">{{ e }}</span> }
              </div>
              <div class="field">
                <label for="startDate">Empieza el</label>
                <input id="startDate" type="date" formControlName="startDate" />
                <span class="hint">En blanco: hoy, o al terminar la membresía vigente.</span>
              </div>
              <div class="field wide">
                <label class="check"><input type="checkbox" formControlName="payNow" /> Registrar un cobro ahora</label>
              </div>
              @if (contractForm.controls.payNow.value) {
                <div class="field">
                  <label for="c-amount">Importe cobrado (€)</label>
                  <input id="c-amount" type="number" step="0.01" min="0.01" formControlName="amount" [attr.aria-invalid]="!!cerr('amount')" />
                  @if (cerr('amount'); as e) { <span class="error">{{ e }}</span> }
                </div>
                <div class="field">
                  <label for="c-method">Forma de pago</label>
                  <select id="c-method" formControlName="method">
                    @for (k of methods; track k) { <option [value]="k">{{ label[k] }}</option> }
                  </select>
                </div>
              }
              <div class="form-actions">
                <button class="btn" type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Confirmar membresía' }}</button>
                <button class="btn btn-quiet" type="button" (click)="closePanel()">Cancelar</button>
              </div>
            </form>
          }

          @if (memberships().length === 0) {
            <div class="state"><strong>Sin membresías</strong>Contrata un plan para que el socio pueda entrar.</div>
          } @else {
            <div class="ms">
              @for (ms of memberships(); track ms.id) {
                <div class="ms-row">
                  <div>
                    <h3>{{ ms.planName }}</h3>
                    <span class="badge" [class]="'badge ' + badge[ms.state]">{{ stateLabel[ms.state] }}</span>
                  </div>
                  <div>{{ ms.startDate | date: 'd MMM y' }} → {{ ms.endDate | date: 'd MMM y' }}</div>
                  <div class="num">
                    {{ ms.paid | currency: 'EUR' }} de {{ ms.price | currency: 'EUR' }}
                    @if (ms.balance > 0 && ms.state !== 'CANCELED') { <br /><span class="debt">Debe {{ ms.balance | currency: 'EUR' }}</span> }
                  </div>
                  <div class="actions">
                    @if (ms.balance > 0 && ms.state !== 'CANCELED') {
                      <button class="btn btn-quiet btn-small" type="button" (click)="openPay(ms)">Registrar pago</button>
                    }
                    @if (auth.isAdmin() && ms.state !== 'CANCELED' && ms.state !== 'EXPIRED') {
                      @if (confirmCancel() === ms.id) {
                        <button class="btn btn-danger btn-small" type="button" (click)="cancel(ms)">Sí, cancelar</button>
                        <button class="btn btn-quiet btn-small" type="button" (click)="confirmCancel.set(null)">No</button>
                      } @else {
                        <button class="btn btn-danger btn-small" type="button" (click)="confirmCancel.set(ms.id)">Cancelar</button>
                      }
                    }
                  </div>
                  @if (ms.payments.length > 0) {
                    <ul class="pays">
                      @for (p of ms.payments; track p.id) {
                        <li>{{ p.paidAt | date: 'd MMM y' }} · {{ p.amount | currency: 'EUR' }} · {{ label[p.method] }}@if (p.reference) { · ref. {{ p.reference }} }</li>
                      }
                    </ul>
                  }
                  @if (isPaying(ms.id)) {
                    <form class="inline form" [formGroup]="payForm" (ngSubmit)="pay(ms)" novalidate>
                      @if (formError()) { <div class="alert-box field wide" role="alert">{{ formError() }}</div> }
                      <div class="field">
                        <label [for]="'amount-' + ms.id">Importe (€) · pendiente {{ ms.balance | currency: 'EUR' }}</label>
                        <input [id]="'amount-' + ms.id" type="number" step="0.01" min="0.01" [max]="ms.balance" formControlName="amount" [attr.aria-invalid]="!!perr('amount')" />
                        @if (perr('amount'); as e) { <span class="error">{{ e }}</span> }
                      </div>
                      <div class="field">
                        <label [for]="'method-' + ms.id">Forma de pago</label>
                        <select [id]="'method-' + ms.id" formControlName="method">
                          @for (k of methods; track k) { <option [value]="k">{{ label[k] }}</option> }
                        </select>
                      </div>
                      <div class="field">
                        <label [for]="'ref-' + ms.id">Referencia (opcional)</label>
                        <input [id]="'ref-' + ms.id" type="text" formControlName="reference" />
                      </div>
                      <div class="form-actions">
                        <button class="btn" type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar pago' }}</button>
                        <button class="btn btn-quiet" type="button" (click)="closePanel()">Cancelar</button>
                      </div>
                    </form>
                  }
                </div>
              }
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class MemberDetail implements OnInit {
  protected readonly auth = inject(AuthService);
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(FormBuilder);

  readonly id = input.required<string>();

  protected readonly member = signal<Member | null>(null);
  protected readonly memberships = signal<Membership[]>([]);
  protected readonly plans = signal<Plan[]>([]);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly panel = signal<Panel>('none');
  protected readonly confirmToggle = signal(false);
  protected readonly confirmCancel = signal<number | null>(null);
  protected readonly hasCurrent = computed(() => this.memberships().some((m) => m.state === 'CURRENT'));

  protected readonly methods: PaymentMethod[] = ['CASH', 'CARD', 'TRANSFER'];
  protected readonly label = METHOD_LABEL;
  protected readonly stateLabel = STATE_LABEL;
  protected readonly badge = STATE_BADGE;

  protected readonly contractForm = this.fb.group({
    planId: this.fb.control<number | null>(null, Validators.required),
    startDate: this.fb.nonNullable.control(''),
    payNow: this.fb.nonNullable.control(true),
    amount: this.fb.control<number | null>(null),
    method: this.fb.nonNullable.control<PaymentMethod>('CASH'),
  });

  protected readonly payForm = this.fb.group({
    amount: this.fb.control<number | null>(null, [Validators.required, Validators.min(0.01), Validators.max(99999.99)]),
    method: this.fb.nonNullable.control<PaymentMethod>('CASH'),
    reference: this.fb.nonNullable.control('', Validators.maxLength(80)),
  });

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = +this.id();
    this.loading.set(true);
    this.error.set(null);
    forkJoin({ m: this.api.getMember(id), ms: this.api.listMemberships(id), plans: this.api.listPlans(true) }).subscribe({
      next: ({ m, ms, plans }) => {
        this.member.set(m);
        this.memberships.set(ms);
        this.plans.set(plans);
        this.loading.set(false);
      },
      error: (e: unknown) => { this.error.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  protected cerr(n: string) { return controlError(this.contractForm as never, n); }
  protected perr(n: string) { return controlError(this.payForm as never, n); }
  protected isPaying(id: number): boolean {
    const p = this.panel();
    return typeof p === 'object' && p.pay === id;
  }

  protected openContract(): void {
    this.formError.set(null);
    this.contractForm.reset({ planId: null, startDate: '', payNow: true, amount: null, method: 'CASH' });
    this.panel.set('contract');
  }

  protected onPlanChange(): void {
    const plan = this.plans().find((p) => p.id === this.contractForm.controls.planId.value);
    if (plan) this.contractForm.controls.amount.setValue(plan.price);
  }

  protected openPay(ms: Membership): void {
    this.formError.set(null);
    this.payForm.reset({ amount: ms.balance, method: 'CASH', reference: '' });
    this.panel.set({ pay: ms.id });
  }

  protected closePanel(): void {
    this.panel.set('none');
    this.formError.set(null);
  }

  protected contract(): void {
    const f = this.contractForm;
    const v = f.getRawValue();
    f.markAllAsTouched();
    if (v.payNow) {
      f.controls.amount.setValidators([Validators.required, Validators.min(0.01), Validators.max(99999.99)]);
    } else {
      f.controls.amount.clearValidators();
    }
    f.controls.amount.updateValueAndValidity();
    if (f.invalid || this.saving() || v.planId === null) return;
    this.saving.set(true);
    this.formError.set(null);
    this.api.contract(+this.id(), {
      planId: v.planId,
      startDate: v.startDate || null,
      payment: v.payNow && v.amount !== null ? { amount: v.amount, method: v.method } : null,
    }).subscribe({
      next: () => { this.saving.set(false); this.panel.set('none'); this.toast.ok('Membresía contratada'); this.reloadMemberships(); },
      error: (e: unknown) => { this.saving.set(false); if (!applyFieldErrors(e, f)) this.formError.set(errorMessage(e)); },
    });
  }

  protected pay(ms: Membership): void {
    this.payForm.markAllAsTouched();
    const v = this.payForm.getRawValue();
    if (this.payForm.invalid || this.saving() || v.amount === null) return;
    this.saving.set(true);
    this.formError.set(null);
    this.api.pay(ms.id, { amount: v.amount, method: v.method, reference: v.reference || null }).subscribe({
      next: () => { this.saving.set(false); this.panel.set('none'); this.toast.ok('Pago registrado'); this.reloadMemberships(); },
      error: (e: unknown) => { this.saving.set(false); this.formError.set(errorMessage(e)); },
    });
  }

  protected cancel(ms: Membership): void {
    this.confirmCancel.set(null);
    this.api.cancelMembership(ms.id).subscribe({
      next: () => { this.toast.ok('Membresía cancelada'); this.reloadMemberships(); },
      error: (e: unknown) => this.toast.error(errorMessage(e)),
    });
  }

  protected toggleActive(): void {
    const m = this.member();
    if (!m) return;
    this.confirmToggle.set(false);
    this.api.setMemberActive(m.id, !m.active).subscribe({
      next: (u) => { this.member.set(u); this.toast.ok(u.active ? 'Socio reactivado' : 'Socio dado de baja'); },
      error: (e: unknown) => this.toast.error(errorMessage(e)),
    });
  }

  private reloadMemberships(): void {
    this.api.listMemberships(+this.id()).subscribe({
      next: (ms) => this.memberships.set(ms),
      error: (e: unknown) => this.toast.error(errorMessage(e)),
    });
  }
}
