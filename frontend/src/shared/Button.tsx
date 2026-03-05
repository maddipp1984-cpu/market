import './Button.css';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'success' | 'ghost';
}

export function Button({ variant = 'primary', className, ...props }: ButtonProps) {
  return <button className={`btn btn-${variant}${className ? ' ' + className : ''}`} {...props} />;
}
