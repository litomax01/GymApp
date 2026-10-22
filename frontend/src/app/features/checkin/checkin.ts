import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, OnInit, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap, tap } from 'rxjs';

import { ApiService } from '../../core/api.service';
import { errorMessage } from '../../core/errors';
import { DENY_TEXT } from '../../core/format';
import { CheckIn, Member } from '../../core/models';
import { ToastService } from '../../core/toast.service';

@Component({
  selector: 'app-checkin',
  imports: [DatePipe, RouterLink],
  styles: `
    .layout { display: grid; gap: 2rem; }
    @media (min-width: 62rem) { .layout { grid-template-columns: 1.6fr 1fr; align-items: start; } }
    .search input { width: 100%; min-height: 3.5rem; font: 500 1.4rem var(--font-body); padding: 0 1rem; background: var(--surface); border: 2px solid var(--ink); border-radius: var(--radius); }
    .results { list-style: none; margin: 0.75rem 0 0; padding: 0; border-top: 2px solid var(--ink); }
    .results li { display: flex; align-items: center; justify-content: space-between; gap: 1rem; padding: 0.7rem 0; border-bottom: 1px solid var(--line); }
    .results strong { font-size: 1.1rem; }
    .verdict { margin-top: 1.5rem; padding: 2rem 1.5rem; border-radius: var(--radius); border: 3px solid var(--ink); display: grid; gap: 0.75rem; }
    .verdict.ok { background: var(--go); color: #fff; border-color: var(--go); }
    .verdict.no { background: var(--stop); color: #fff; border-color: var(--stop); }
    .verdict .word { font: 700 clamp(3rem, 12vw, 6.5rem)/0.9 var(--font-display); text-transform: uppercase; }
    .verdict .who { font: 600 clamp(1.6rem, 4vw, 2.4rem)/1.1 var(--font-display); }
    .verdict p { font-size: 1.15rem; }
    .verdict .btn { justify-self: start; background: #fff; border-color: #fff; color: var(--ink); }
    .recent { list-style: none; margin: 0; padding: 0; }
    .recent li { display: flex; justify-content: space-between; gap: 0.75rem; padding: 0.55rem 0; border-bottom: 1px solid var(--line); }
    .dot { display: inline-block; width: 0.7rem; height: 0.7rem; border-radius: 50%; margin-right: 0.5rem; }
    .dot.ok { background: var(--go); } .dot.no { background: var(--stop); }
  `,
  template: `
    <div class="page">
      <div class="page-head"><div><h1>Check-in</h1><p>Busca al socio por nombre o documento y registra su entrada.</p></div></div>

      <div class="layout">
        <section aria-label="Registrar entrada">
          <div class="search">
            <label class="sr-only" for="q">Nombre o documento del socio</label>
            <input #q id="q" type="search" autocomplete="off" placeholder="Nombre, apellido o documento"
                   (input)="onInput($any($event.target).value)" />
          </div>

          @if (verdict(); as v) {
            <div class="verdict" [class.ok]="v.result === 'ALLOWED'" [class.no]="v.result === 'DENIED'" role="alert">
              <span class="word">{{ v.result === 'ALLOWED' ? 'Adelante' : 'Acceso denegado' }}</span>
              <span class="who">{{ v.memberName }}</span>
              @if (v.result === 'ALLOWED') {
                <p>Cuota vigente hasta el {{ v.membershipEnd | date: 'd MMMM' }}
                  @if (v.daysLeft !== undefined && v.daysLeft <= 7) { · <strong>quedan {{ v.daysLeft }} días</strong> }</p>
              } @else {
                <p>{{ reasonText(v) }}</p>
              }
              <div class="actions">
                <button class="btn" type="button" (click)="reset()">Siguiente socio</button>
                @if (v.result === 'DENIED') {
                  <a class="btn" [routerLink]="['/socios', v.memberId]">Abrir ficha para renovar</a>
                }
              </div>
            </div>
          } @else if (searching()) {
            <div class="state" role="status">Buscando…</div>
          } @else if (searchError()) {
            <div class="alert-box" role="alert" style="margin-top:1rem">{{ searchError() }}</div>
          } @else if (results().length > 0) {
            <ul class="results">
              @for (m of results(); track m.id) {
                <li>
                  <span><strong>{{ m.lastName }}, {{ m.firstName }}</strong><br /><span class="muted">Doc. {{ m.documentNumber }}</span></span>
                  <button class="btn" type="button" [disabled]="busy()" (click)="register(m)">Registrar entrada</button>
                </li>
              }
            </ul>
          } @else if (term().length >= 2) {
            <div class="state"><strong>Sin resultados</strong>No hay socios activos con "{{ term() }}".
              <br /><a class="btn btn-quiet" routerLink="/socios/nuevo">Dar de alta un socio</a></div>
          } @else {
            <div class="state"><strong>Empieza a escribir</strong>Con dos letras ya aparecen coincidencias.</div>
          }
        </section>

        <section aria-labelledby="rec">
          <div class="section-head"><h2 id="rec">Últimos accesos</h2></div>
          @if (recent().length === 0) {
            <div class="state">Todavía no hay accesos registrados.</div>
          } @else {
            <ul class="recent">
              @for (c of recent(); track c.id) {
                <li>
                  <span><span class="dot" [class.ok]="c.result === 'ALLOWED'" [class.no]="c.result === 'DENIED'"></span>
                    <span class="sr-only">{{ c.result === 'ALLOWED' ? 'Permitido' : 'Denegado' }}:</span>{{ c.memberName }}</span>
                  <span class="muted num">{{ c.checkedInAt | date: 'HH:mm' }}</span>
                </li>
              }
            </ul>
          }
        </section>
      </div>
    </div>
  `,
})
export class CheckInPage implements OnInit {
  private readonly api = inject(ApiService);
  private readonly toast = inject(ToastService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('q');
  private readonly typed$ = new Subject<string>();

  protected readonly term = signal('');
  protected readonly results = signal<Member[]>([]);
  protected readonly searching = signal(false);
  protected readonly searchError = signal<string | null>(null);
  protected readonly verdict = signal<CheckIn | null>(null);
  protected readonly busy = signal(false);
  protected readonly recent = signal<CheckIn[]>([]);

  constructor() {
    this.typed$
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        tap((q) => {
          this.term.set(q);
          this.searchError.set(null);
          this.searching.set(q.length >= 2);
        }),
        switchMap((q) =>
          q.length < 2
            ? of(null)
            : this.api.searchMembers(q, 0, true, 8).pipe(
                catchError((e: unknown) => {
                  this.searchError.set(errorMessage(e));
                  return of(null);
                }),
              ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((page) => {
        this.results.set(page?.content ?? []);
        this.searching.set(false);
      });
  }

  ngOnInit(): void {
    this.loadRecent();
  }

  protected onInput(value: string): void {
    this.verdict.set(null);
    this.typed$.next(value.trim());
  }

  protected register(m: Member): void {
    this.busy.set(true);
    this.api.checkIn(m.id).subscribe({
      next: (c) => {
        this.verdict.set(c);
        this.busy.set(false);
        this.loadRecent();
      },
      error: (e: unknown) => {
        this.busy.set(false);
        this.toast.error(errorMessage(e));
      },
    });
  }

  protected reset(): void {
    const el = this.input().nativeElement;
    el.value = '';
    this.verdict.set(null);
    this.results.set([]);
    this.term.set('');
    this.typed$.next(''); // evita que distinctUntilChanged ignore repetir la misma búsqueda
    el.focus();
  }

  protected reasonText(c: CheckIn): string {
    return c.reason ? DENY_TEXT[c.reason] : 'No se puede dar acceso.';
  }

  private loadRecent(): void {
    this.api.recentCheckIns().subscribe({ next: (r) => this.recent.set(r), error: () => undefined });
  }
}
