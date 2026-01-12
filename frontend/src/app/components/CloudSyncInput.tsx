import React from 'react';
import { cn } from '../components/ui/utils';

interface CloudSyncInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  label?: string;
}

export function CloudSyncInput({ label, className, ...props }: CloudSyncInputProps) {
  return (
    <div className="flex flex-col gap-2">
      {label && <label className="text-text-secondary">{label}</label>}
      <input
        className={cn(
          'h-10 px-4 rounded-xl bg-surface-1 border border-border-0 text-text-secondary',
          'placeholder:text-text-muted focus:outline-none focus:ring-2 focus:ring-accent-primary',
          className
        )}
        {...props}
      />
    </div>
  );
}
