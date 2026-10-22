import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../../core/auth.service';

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  styles: `
    :host { display: block; min-height: 100vh; }
    .nav { background: var(--ink); color: #fff; display: flex; flex-wrap: wrap; align-items: center; gap: 0.5rem 1.5rem; padding: 0.5rem 1rem; position: relative; }
    .nav::after { content: ''; position: absolute; inset: auto 0 0 0; height: 6px; background: repeating-linear-gradient(-45deg, var(--tape) 0 12px, var(--ink) 12px 24px); }
    .logo { display: inline-flex; align-items: center; gap: 0.5rem; font: 800 2.3rem var(--font-display); text-decoration: none; letter-spacing: 0.02em; text-transform: uppercase; margin-right: 0.5rem; }
    .logo b { color: var(--tape); }
    .logo img { width: 3.2rem; height: 3.2rem; border-radius: 50%; object-fit: cover; }
    ul { list-style: none; margin: 0; padding: 0; display: flex; gap: 0.25rem; flex-wrap: wrap; }
    a.link { display: inline-flex; align-items: center; min-height: 2.75rem; padding: 0 0.9rem; color: #dfe7e2; text-decoration: none; font-weight: 500; border-radius: var(--radius); }
    a.link:hover { background: rgba(255, 255, 255, 0.1); }
    a.link.active { color: var(--ink); background: var(--tape); font-weight: 600; }
    a.link:focus-visible { outline-color: #fff; box-shadow: none; }
    .who { margin-left: auto; display: flex; align-items: center; gap: 0.75rem; font-size: 0.95rem; }
    .who button { background: transparent; color: #fff; border: 1.5px solid #8fa197; border-radius: var(--radius); min-height: 2.25rem; padding: 0 0.8rem; font: inherit; cursor: pointer; }
    .who button:hover { border-color: #fff; }
    .who button:focus-visible { outline-color: #fff; box-shadow: none; }
    @media (max-width: 40rem) { .who span { display: none; } }
  `,
  template: `
    <a class="sr-only" href="#contenido">Saltar al contenido</a>
    <header class="nav">
      <a class="logo" routerLink="/" aria-label="GymFlow, ir al panel">
        <img src="logo.jpg" alt="" width="40" height="40" />
        <span>Gym<b>Flow</b></span>
      </a>
      <nav aria-label="Principal">
        <ul>
          <li><a class="link" routerLink="/panel" routerLinkActive="active">Panel</a></li>
          <li><a class="link" routerLink="/acceso" routerLinkActive="active">Check-in</a></li>
          <li><a class="link" routerLink="/socios" routerLinkActive="active">Socios</a></li>
          <li><a class="link" routerLink="/planes" routerLinkActive="active">Planes</a></li>
        </ul>
      </nav>
      <div class="who">
        <span>{{ auth.user()?.fullName }} · {{ auth.isAdmin() ? 'Administración' : 'Recepción' }}</span>
        <button type="button" (click)="auth.logout()">Salir</button>
      </div>
    </header>
    <main id="contenido" tabindex="-1"><router-outlet /></main>
  `,
})
export class Shell {
  protected readonly auth = inject(AuthService);
}
