import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { tap } from 'rxjs';

import { LoginResponse, User } from './models';

const STORAGE_KEY = 'gymflow.session';

interface Session {
  token: string;
  expiresAt: number;
  user: User;
}

/**
 * Sesión en localStorage para que recepción no tenga que volver a entrar al recargar.
 * Riesgo asumido: un XSS podría leer el token; se mitiga con el escapado de Angular, sin innerHTML
 * y con una caducidad corta del JWT (8 h por defecto).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly session = signal<Session | null>(this.restore());

  readonly user = computed(() => this.session()?.user ?? null);
  readonly isLoggedIn = computed(() => this.session() !== null);
  readonly isAdmin = computed(() => this.session()?.user.role === 'ADMIN');

  login(email: string, password: string) {
    return this.http.post<LoginResponse>('/api/auth/login', { email, password }).pipe(
      tap((res) => {
        const s: Session = { token: res.accessToken, expiresAt: Date.parse(res.expiresAt), user: res.user };
        this.session.set(s);
        try {
          localStorage.setItem(STORAGE_KEY, JSON.stringify(s));
        } catch {
          /* almacenamiento bloqueado: la sesión vive sólo en memoria */
        }
      }),
    );
  }

  logout(redirect = true): void {
    this.session.set(null);
    try {
      localStorage.removeItem(STORAGE_KEY);
    } catch {
      /* nada que limpiar */
    }
    if (redirect) void this.router.navigate(['/login']);
  }

  token(): string | null {
    const s = this.session();
    if (!s) return null;
    if (s.expiresAt <= Date.now()) {
      this.logout();
      return null;
    }
    return s.token;
  }

  private restore(): Session | null {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (!raw) return null;
      const s = JSON.parse(raw) as Session;
      return s.expiresAt > Date.now() ? s : null;
    } catch {
      return null;
    }
  }
}
