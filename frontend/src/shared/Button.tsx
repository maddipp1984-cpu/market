import './Button.css';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'success' | 'ghost';
  icon?: boolean;
}

export function Button({ variant = 'primary', icon, className, ...props }: ButtonProps) {
  const classes = `btn btn-${variant}${icon ? ' btn-icon' : ''}${className ? ' ' + className : ''}`;
  return <button className={classes} {...props} />;
}
