import { CurrencyPipe, DatePipe } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/errors';
import { whatsappLink } from '../../core/format';
import { AtRiskMember, DashboardSummary } from '../../core/models';

@Component({
  selector: 'app-dashboard',
  imports: [CurrencyPipe, DatePipe, RouterLink],
  styles: `
    .strip { display: grid; grid-template-columns: repeat(2, 1fr); border-top: 2px solid var(--ink); border-bottom: 2px solid var(--ink); }
    @media (min-width: 56rem) { .strip { grid-template-columns: repeat(4, 1fr); } }
    .kpi { padding: 1rem 1rem 1rem 0; }
    .kpi + .kpi { padding-left: 1rem; border-left: 1px solid var(--line); }
    .kpi:nth-child(3) { border-left: 0; padding-left: 0; }
    @media (min-width: 56rem) { .kpi:nth-child(3) { border-left: 1px solid var(--line); padding-left: 1rem; } }
    .kpi dt { color: var(--ink-soft); font-size: 0.95rem; }
    .kpi dd { margin: 0; font: 700 clamp(2.4rem, 5vw, 3.6rem)/1 var(--font-display); }
    .kpi small { display: block; color: var(--ink-soft); font: 400 0.85rem var(--font-body); margin-top: 0.15rem; }
    .section { margin-top: 2rem; }
    .section > p { color: var(--ink-soft); max-width: 60ch; margin-bottom: 0.75rem; }
    .sk { height: 5rem; }
  `,
  template: `
    <div class="page">
      <div class="page-head">
        <div>
          <h1>Panel</h1>
          <p>Cómo va el gimnasio hoy.</p>
        </div>
        <a class="btn" routerLink="/acceso">Registrar entrada</a>
      </div>

      @if (loading()) {
        <div class="skeleton sk" role="status" aria-label="Cargando panel"></div>
      } @else if (error()) {
        <div class="alert-box" role="alert">
          <span>{{ error() }}</span>
          <button class="btn btn-quiet btn-small" type="button" (click)="load()">Reintentar</button>
        </div>
      } @else if (data(); as d) {
        <dl class="strip" style="margin:0">
          <div class="kpi"><dt>Socios con cuota al día</dt><dd class="num">{{ d.activeMembers }}</dd></div>
          <div class="kpi"><dt>Vencen en 7 días</dt><dd class="num">{{ d.expiringSoon }}</dd><small>sin renovación registrada</small></div>
          <div class="kpi"><dt>Entradas de hoy</dt><dd class="num">{{ d.checkInsToday }}</dd></div>
          @if (d.monthRevenue !== undefined && d.monthRevenue !== null) {
            <div class="kpi">
              <dt>Cobrado este mes</dt>
              <dd class="num">{{ d.monthRevenue | currency: 'EUR' : 'symbol' : '1.0-0' }}</dd>
              <small>pendiente de cobro: {{ d.pendingBalance | currency: 'EUR' : 'symbol' : '1.0-0' }}</small>
            </div>
          } @else {
            <div class="kpi"><dt>En riesgo</dt><dd class="num">{{ d.atRisk.length }}</dd></div>
          }
        </dl>

        <section class="section" aria-labelledby="radar">
          <div class="section-head"><h2 id="radar">Socios en riesgo de abandonar</h2></div>
          <p>Con cuota vigente pero sin venir desde hace 14 días o más, o a punto de vencer. Un mensaje a tiempo evita una baja.</p>
          @if (d.atRisk.length === 0) {
            <div class="state"><strong>Nadie en riesgo ahora</strong>Todos los socios con cuota vigente han venido hace poco.</div>
          } @else {
            <div class="table-wrap">
              <table>
                <caption class="sr-only">Socios en riesgo</caption>
                <thead><tr><th>Socio</th><th>Motivo</th><th>Última visita</th><th>Vence</th><th class="right">Contactar</th></tr></thead>
                <tbody>
                  @for (m of d.atRisk; track m.memberId) {
                    <tr>
                      <td><a [routerLink]="['/socios', m.memberId]">{{ m.name }}</a></td>
                      <td>
                        <span class="badge" [class.badge-stop]="m.reason === 'INACTIVE'" [class.badge-warn]="m.reason === 'EXPIRING'">
                          {{ m.reason === 'INACTIVE' ? 'Sin venir' : 'Por vencer' }}
                        </span>
                      </td>
                      <td>{{ m.lastVisit ? (m.lastVisit | date: 'd MMM') : 'Nunca' }}</td>
                      <td>{{ m.membershipEnd | date: 'd MMM' }}</td>
                      <td class="right">
                        @if (link(m); as href) {
                          <a class="btn btn-quiet btn-small" [href]="href" target="_blank" rel="noopener noreferrer">WhatsApp</a>
                        } @else {
                          <span class="muted">Sin teléfono</span>
                        }
                      </td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </section>
      }
    </div>
  `,
})
export class Dashboard implements OnInit {
  private readonly api = inject(ApiService);

  protected readonly data = signal<DashboardSummary | null>(null);
  protected readonly loading = signal(true);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.api.dashboard().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.error.set(errorMessage(e));
        this.loading.set(false);
      },
    });
  }

  protected link(m: AtRiskMember): string | null {
    const first = m.name.split(' ')[0];
    const text =
      m.reason === 'INACTIVE'
        ? `Hola ${first}, hace tiempo que no te vemos por el gimnasio. ¿Todo bien? Aquí tienes tu sitio cuando quieras volver.`
        : `Hola ${first}, tu membresía vence pronto. ¿Quieres que la renovemos?`;
    return whatsappLink(m.phone, text);
  }
}
