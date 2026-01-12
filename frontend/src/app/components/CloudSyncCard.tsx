import React from 'react';
import { cn } from '../components/ui/utils';

interface CloudSyncCardProps {
  children: React.ReactNode;
  className?: string;
}

export function CloudSyncCard({ children, className }: CloudSyncCardProps) {
  return (
    <div className={cn(
      'rounded-2xl bg-surface-0 border border-border-0 overflow-hidden',
      'shadow-[0_1px_2px_rgba(0,0,0,0.3)]',
      className
    )}>
      {children}
    </div>
  );
}
