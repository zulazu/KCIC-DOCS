import { forwardRef, useId } from 'react';
import type {
  InputHTMLAttributes,
  ReactNode,
  SelectHTMLAttributes,
  TextareaHTMLAttributes,
} from 'react';
import { cn } from '../utils/cn';

const controlClass =
  'w-full rounded-lg border border-line-strong bg-surface px-3 text-sm text-ink placeholder:text-ink-disabled focus:border-primary focus:ring-2 focus:ring-primary/15 focus:outline-none disabled:cursor-not-allowed disabled:bg-surface-muted aria-[invalid=true]:border-danger';

interface FieldShellProps {
  id: string;
  label: string;
  required?: boolean;
  helper?: string;
  error?: string;
  children: ReactNode;
}

function FieldShell({ id, label, required, helper, error, children }: FieldShellProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={id} className="text-xs font-semibold text-ink">
        {label}
        {required ? (
          <span className="ml-0.5 text-danger" aria-hidden="true">
            *
          </span>
        ) : null}
      </label>
      {children}
      {helper && !error ? (
        <p id={`${id}-helper`} className="text-xs text-ink-secondary">
          {helper}
        </p>
      ) : null}
      {error ? (
        <p id={`${id}-error`} className="text-xs font-medium text-danger" role="alert">
          {error}
        </p>
      ) : null}
    </div>
  );
}

interface InputFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string;
  helper?: string;
  error?: string;
}

export const InputField = forwardRef<HTMLInputElement, InputFieldProps>(function InputField(
  { label, helper, error, required, className, id, ...rest },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  return (
    <FieldShell id={fieldId} label={label} required={required} helper={helper} error={error}>
      <input
        ref={ref}
        id={fieldId}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          error ? `${fieldId}-error` : helper ? `${fieldId}-helper` : undefined
        }
        className={cn(controlClass, 'h-10', className)}
        {...rest}
      />
    </FieldShell>
  );
});

interface SelectFieldProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label: string;
  helper?: string;
  error?: string;
  children: ReactNode;
}

export const SelectField = forwardRef<HTMLSelectElement, SelectFieldProps>(function SelectField(
  { label, helper, error, required, className, id, children, ...rest },
  ref,
) {
  const generatedId = useId();
  const fieldId = id ?? generatedId;
  return (
    <FieldShell id={fieldId} label={label} required={required} helper={helper} error={error}>
      <select
        ref={ref}
        id={fieldId}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          error ? `${fieldId}-error` : helper ? `${fieldId}-helper` : undefined
        }
        className={cn(controlClass, 'h-10', className)}
        {...rest}
      >
        {children}
      </select>
    </FieldShell>
  );
});

interface TextareaFieldProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  helper?: string;
  error?: string;
}

export const TextareaField = forwardRef<HTMLTextAreaElement, TextareaFieldProps>(
  function TextareaField(
    { label, helper, error, required, className, id, rows = 3, ...rest },
    ref,
  ) {
    const generatedId = useId();
    const fieldId = id ?? generatedId;
    return (
      <FieldShell id={fieldId} label={label} required={required} helper={helper} error={error}>
        <textarea
          ref={ref}
          id={fieldId}
          rows={rows}
          required={required}
          aria-invalid={error ? true : undefined}
          aria-describedby={
            error ? `${fieldId}-error` : helper ? `${fieldId}-helper` : undefined
          }
          className={cn(controlClass, 'min-h-10 py-2', className)}
          {...rest}
        />
      </FieldShell>
    );
  },
);
