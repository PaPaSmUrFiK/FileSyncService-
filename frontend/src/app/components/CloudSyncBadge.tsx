import React from 'react';
import { cn } from '../components/ui/utils';

interface CloudSyncBadgeProps {
  variant: 'synced' | 'uploading' | 'pending' | 'conflict' | 'error' | 'completed';
  children: React.ReactNode;
  className?: string;
}

export function CloudSyncBadge({ variant, children, className }: CloudSyncBadgeProps) {
  const baseStyles = 'inline-flex items-center px-3 py-1.5 rounded-full text-xs';
  
  const variantStyles = {
    synced: 'bg-success-base/35 text-success-bright',
    uploading: 'bg-accent-dark/35 text-text-secondary',
    pending: 'bg-warning-base/35 text-text-secondary',
    conflict: 'bg-danger-base/35 text-danger-bright',
    error: 'bg-danger-base/35 text-danger-bright',
    completed: 'bg-success-base/35 text-success-bright',
  };

  return (
    <span className={cn(baseStyles, variantStyles[variant], className)}>
      {children}
    </span>
  );
}
