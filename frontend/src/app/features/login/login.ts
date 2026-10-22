import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { AuthService } from '../../core/auth.service';
import { controlError, errorMessage } from '../../core/errors';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  styles: `
    :host { display: grid; min-height: 100vh; }
    @media (min-width: 56rem) { :host { grid-template-columns: 1.1fr 1fr; } }
    .brand { background: var(--ink); color: #fff; padding: 2rem; display: flex; flex-direction: column; justify-content: space-between; gap: 2rem; position: relative; }
    .brand::after { content: ''; position: absolute; inset: auto 0 0 0; height: 14px; background: repeating-linear-gradient(-45deg, var(--tape) 0 18px, var(--ink) 18px 36px); }
    .logo { display: flex; align-items: center; gap: 1.25rem; }
    .mark { width: clamp(6rem, 12vw, 9rem); height: auto; flex: none; border-radius: 50%; object-fit: cover; box-shadow: 0 0 0 3px var(--tape); }
    .word { font: 800 clamp(3.6rem, 9vw, 7rem)/0.9 var(--font-display); letter-spacing: 0.02em; text-transform: uppercase; color: #fff; text-shadow: 0 3px 0 rgba(0, 0, 0, 0.5); }
    .word b { color: var(--tape); }
    .word small { display: block; font: 700 1.05rem sans-serif; letter-spacing: 0.3em; color: #fff; background: var(--tape); color: var(--ink); padding: 0.4rem 0.7rem; margin-top: 0.8rem; width: fit-content; text-shadow: none; }
    .tag { font: 800 clamp(2.8rem, 7vw, 5.5rem)/0.95 var(--font-display); max-width: 14ch; color: #fff; text-transform: uppercase; text-shadow: 0 3px 0 rgba(0, 0, 0, 0.5); }
    .brand p.tag { color: #fff; }
    .brand p.lead { color: #fff; font-size: clamp(1.1rem, 2vw, 1.35rem); font-weight: 500; line-height: 1.45; max-width: 36ch; margin-top: 1.25rem; padding-left: 1rem; border-left: 5px solid var(--tape); }
    .side { display: grid; place-items: center; padding: 2rem 1rem; }
    form { width: 100%; max-width: 24rem; display: grid; gap: 1rem; }
    .demo { font-size: 0.9rem; color: var(--ink-soft); border-top: 1px solid var(--line); padding-top: 1rem; }
    code { background: var(--surface); padding: 0.1rem 0.35rem; border-radius: 3px; }
  `,
  template: `
    <section class="brand" aria-label="GymFlow">
      <div class="logo">
        <img class="mark" src="logo.jpg" alt="" width="240" height="240" />
        <span class="word">Gym<b>Flow</b><small>GESTIÓN DE GIMNASIOS</small></span>
      </div>
      <div>
        <p class="tag">La recepción, sin papeles.</p>
        <p class="lead">Socios, cuotas y accesos en una sola pantalla. Entra y comprueba quién tiene la cuota al día.</p>
      </div>
      <span aria-hidden="true"></span>
    </section>
    <main class="side">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h1>Entrar</h1>
        @if (error()) {
          <div class="alert-box" role="alert">{{ error() }}</div>
        }
        <div class="field">
          <label for="email">Email</label>
          <input id="email" type="email" formControlName="email" autocomplete="username" inputmode="email"
                 [attr.aria-invalid]="!!err('email')" [attr.aria-describedby]="err('email') ? 'email-err' : null" />
          @if (err('email'); as m) { <span class="error" id="email-err">{{ m }}</span> }
        </div>
        <div class="field">
          <label for="password">Contraseña</label>
          <input id="password" type="password" formControlName="password" autocomplete="current-password"
                 [attr.aria-invalid]="!!err('password')" [attr.aria-describedby]="err('password') ? 'pw-err' : null" />
          @if (err('password'); as m) { <span class="error" id="pw-err">{{ m }}</span> }
        </div>
        <button class="btn" type="submit" [disabled]="loading()">{{ loading() ? 'Entrando…' : 'Entrar' }}</button>
        <p class="demo">Demo: <code>admin&#64;gymflow.test</code> o <code>recepcion&#64;gymflow.test</code>, contraseña <code>Admin123!</code></p>
      </form>
    </main>
  `,
})
export class Login {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly form = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email, Validators.maxLength(160)]],
    password: ['', [Validators.required, Validators.maxLength(100)]],
  });

  constructor() {
    if (this.auth.token()) void this.router.navigate(['/']);
  }

  protected err(name: string): string | null {
    return controlError(this.form, name);
  }

  protected submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.loading()) return;
    this.loading.set(true);
    this.error.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: () => void this.router.navigate(['/']),
      error: (e: unknown) => {
        this.loading.set(false);
        this.error.set(e instanceof HttpErrorResponse && e.status === 401 ? 'Email o contraseña incorrectos.' : errorMessage(e));
      },
    });
  }
}
