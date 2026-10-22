import { HttpErrorResponse } from '@angular/common/http';
import { FormGroup } from '@angular/forms';

import { ApiErrorBody } from './models';

/** Traduce cualquier error HTTP a un mensaje accionable para la persona usuaria. */
export function errorMessage(err: unknown): string {
  if (!(err instanceof HttpErrorResponse)) return 'Ocurrió un error inesperado. Inténtalo de nuevo.';
  if (err.status === 0) return 'No hay conexión con el servidor. Comprueba la red e inténtalo de nuevo.';
  const body = err.error as Partial<ApiErrorBody> | null;
  if (err.status === 403) return 'No tienes permisos para esta acción.';
  if (err.status >= 500) return 'El servidor tuvo un problema. Inténtalo de nuevo en unos minutos.';
  return body?.message ?? 'No se pudo completar la acción.';
}

/** Copia los errores de validación del servidor a los campos del formulario. Devuelve true si mapeó alguno. */
export function applyFieldErrors(err: unknown, form: FormGroup): boolean {
  if (!(err instanceof HttpErrorResponse)) return false;
  const fields = (err.error as Partial<ApiErrorBody> | null)?.fields ?? [];
  let mapped = false;
  for (const f of fields) {
    const control = form.get(f.field);
    if (control) {
      control.setErrors({ server: f.message });
      control.markAsTouched();
      mapped = true;
    }
  }
  return mapped;
}

export function controlError(form: FormGroup, name: string): string | null {
  const c = form.get(name);
  if (!c || !c.touched || !c.errors) return null;
  const e = c.errors;
  if (e['server']) return e['server'] as string;
  if (e['required']) return 'Este campo es obligatorio.';
  if (e['email']) return 'Introduce un email válido.';
  if (e['maxlength']) return `Máximo ${(e['maxlength'] as { requiredLength: number }).requiredLength} caracteres.`;
  if (e['min']) return `El valor mínimo es ${(e['min'] as { min: number }).min}.`;
  if (e['max']) return `El valor máximo es ${(e['max'] as { max: number }).max}.`;
  if (e['pattern']) return 'Formato no válido.';
  return 'Valor no válido.';
}
