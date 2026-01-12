import React from 'react';
import { cn } from '../components/ui/utils';

interface CloudSyncButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger';
  children: React.ReactNode;
}

export function CloudSyncButton({ 
  variant = 'primary', 
  children, 
  className,
  disabled,
  ...props 
}: CloudSyncButtonProps) {
  const baseStyles = 'h-10 px-6 rounded-xl transition-colors disabled:opacity-50 disabled:cursor-not-allowed';
  
  const variantStyles = {
    primary: 'bg-accent-primary text-text-primary hover:bg-accent-hover',
    secondary: 'bg-surface-1 text-text-secondary border border-border-0 hover:bg-surface-2',
    danger: 'bg-danger-base text-text-primary hover:bg-danger-bright',
  };

  return (
    <button
      className={cn(baseStyles, variantStyles[variant], className)}
      disabled={disabled}
      {...props}
    >
      {children}
    </button>
  );
}
