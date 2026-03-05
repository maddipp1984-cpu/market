import './FormField.css';

interface FormFieldProps {
  label: string;
  children: React.ReactNode;
  className?: string;
  compact?: boolean;
}

export function FormField({ label, children, className, compact }: FormFieldProps) {
  return (
    <label className={`form-field${compact ? ' form-field--compact' : ''}${className ? ' ' + className : ''}`}>
      <span className="form-field-label">{label}</span>
      {children}
    </label>
  );
}
