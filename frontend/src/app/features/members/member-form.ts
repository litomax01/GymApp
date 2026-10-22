import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { applyFieldErrors, controlError, errorMessage } from '../../core/errors';
import { MemberInput } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-member-form',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="page-head"><h1>{{ id() ? 'Editar socio' : 'Nuevo socio' }}</h1></div>

      @if (loading()) {
        <div class="skeleton" style="height:12rem" role="status" aria-label="Cargando socio"></div>
      } @else if (loadError()) {
        <div class="alert-box" role="alert"><span>{{ loadError() }}</span><a class="btn btn-quiet btn-small" routerLink="/socios">Volver a socios</a></div>
      } @else {
        <form class="panel form" [formGroup]="form" (ngSubmit)="save()" novalidate>
          @if (formError()) { <div class="alert-box field wide" role="alert">{{ formError() }}</div> }
          @for (f of fields; track f.name) {
            <div class="field" [class.wide]="f.wide">
              <label [for]="f.name">{{ f.label }}</label>
              @if (f.type === 'textarea') {
                <textarea [id]="f.name" [formControlName]="f.name" [attr.aria-invalid]="!!err(f.name)"></textarea>
              } @else {
                <input [id]="f.name" [type]="f.type" [formControlName]="f.name" [attr.inputmode]="f.inputmode ?? null"
                       [attr.autocomplete]="f.autocomplete ?? 'off'" [attr.aria-invalid]="!!err(f.name)"
                       [attr.aria-describedby]="err(f.name) ? f.name + '-err' : null" />
              }
              @if (err(f.name); as m) { <span class="error" [id]="f.name + '-err'">{{ m }}</span> }
            </div>
          }
          <div class="form-actions">
            <button class="btn" type="submit" [disabled]="saving()">{{ saving() ? 'Guardando…' : 'Guardar socio' }}</button>
            <a class="btn btn-quiet" [routerLink]="id() ? ['/socios', id()] : ['/socios']">Cancelar</a>
          </div>
        </form>
      }
    </div>
  `,
})
export class MemberForm implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  /** Parámetro de ruta :id (withComponentInputBinding); ausente al crear. */
  readonly id = input<string | undefined, string | undefined>(undefined, { transform: (v) => v });

  protected readonly loading = signal(false);
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);

  protected readonly fields = [
    { name: 'firstName', label: 'Nombre', type: 'text', autocomplete: 'off' },
    { name: 'lastName', label: 'Apellidos', type: 'text' },
    { name: 'documentNumber', label: 'Documento de identidad', type: 'text' },
    { name: 'birthDate', label: 'Fecha de nacimiento', type: 'date' },
    { name: 'phone', label: 'Teléfono', type: 'tel', inputmode: 'tel' },
    { name: 'email', label: 'Email', type: 'email', inputmode: 'email' },
    { name: 'notes', label: 'Notas (lesiones, preferencias…)', type: 'textarea', wide: true },
  ];

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: ['', [Validators.required, Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.maxLength(80)]],
    documentNumber: ['', [Validators.required, Validators.maxLength(30), Validators.pattern(/^[A-Za-z0-9.\-]+$/)]],
    birthDate: [''],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[0-9+() \-]*$/)]],
    email: ['', [Validators.email, Validators.maxLength(160)]],
    notes: ['', [Validators.maxLength(500)]],
  });

  ngOnInit(): void {
    const id = this.id();
    if (!id) return;
    this.loading.set(true);
    this.api.getMember(+id).subscribe({
      next: (m) => {
        this.form.patchValue({
          firstName: m.firstName, lastName: m.lastName, documentNumber: m.documentNumber,
          birthDate: m.birthDate ?? '', phone: m.phone ?? '', email: m.email ?? '', notes: m.notes ?? '',
        });
        this.loading.set(false);
      },
      error: (e: unknown) => { this.loadError.set(errorMessage(e)); this.loading.set(false); },
    });
  }

  protected err(name: string): string | null {
    return controlError(this.form, name);
  }

  protected save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.saving()) return;
    const v = this.form.getRawValue();
    const body: MemberInput = {
      ...v, email: v.email || null, phone: v.phone || null, birthDate: v.birthDate || null, notes: v.notes || null,
    };
    const id = this.id();
    this.saving.set(true);
    this.formError.set(null);
    const req = id ? this.api.updateMember(+id, body) : this.api.createMember(body);
    req.subscribe({
      next: (m) => {
        this.toast.ok(id ? 'Socio actualizado' : 'Socio creado');
        void this.router.navigate(['/socios', m.id]);
      },
      error: (e: unknown) => {
        this.saving.set(false);
        if (!applyFieldErrors(e, this.form)) this.formError.set(errorMessage(e));
      },
    });
  }
}
