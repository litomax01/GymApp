import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { authInterceptor } from './auth.http';
import { AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let ctrl: HttpTestingController;
  let auth: AuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [provideRouter([{ path: 'login', children: [] }]), provideHttpClient(withInterceptors([authInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    ctrl = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
  });

  it('añade el token a las llamadas /api tras iniciar sesión', () => {
    auth.login('a@b.c', 'x').subscribe();
    ctrl.expectOne('/api/auth/login').flush({
      accessToken: 'tok', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { id: 1, email: 'a@b.c', fullName: 'A', role: 'ADMIN' },
    });
    http.get('/api/members').subscribe();
    const req = ctrl.expectOne('/api/members');
    expect(req.request.headers.get('Authorization')).toBe('Bearer tok');
    expect(auth.isAdmin()).toBe(true);
  });

  it('no envía token al login ni a otros orígenes', () => {
    http.post('/api/auth/login', {}).subscribe();
    expect(ctrl.expectOne('/api/auth/login').request.headers.has('Authorization')).toBe(false);
  });

  it('cierra la sesión ante un 401', () => {
    auth.login('a@b.c', 'x').subscribe();
    ctrl.expectOne('/api/auth/login').flush({
      accessToken: 'tok', expiresAt: new Date(Date.now() + 60_000).toISOString(),
      user: { id: 1, email: 'a@b.c', fullName: 'A', role: 'RECEPTION' },
    });
    http.get('/api/dashboard').subscribe({ error: () => undefined });
    ctrl.expectOne('/api/dashboard').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(auth.isLoggedIn()).toBe(false);
  });
});
