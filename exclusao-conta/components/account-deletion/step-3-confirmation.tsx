'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { AlertTriangle, Loader2, ArrowLeft, Trash2 } from 'lucide-react';
import type { UserData } from '@/lib/types';

interface Step3ConfirmationProps {
  user: UserData;
  onConfirm: () => Promise<void>;
  onBack: () => void;
  isLoading: boolean;
  error: string | null;
}

const CONFIRMATION_TEXT = 'EXCLUIR MINHA CONTA';

export function Step3Confirmation({
  user,
  onConfirm,
  onBack,
  isLoading,
  error,
}: Step3ConfirmationProps) {
  const [confirmationInput, setConfirmationInput] = useState('');

  const isValid = confirmationInput === CONFIRMATION_TEXT;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (isValid && !isLoading) await onConfirm();
  };

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
      <div className="text-center space-y-2">
        <div className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4" style={{ backgroundColor: 'var(--danger-surface)' }}>
          <AlertTriangle className="w-8 h-8" style={{ color: 'var(--danger-icon)' }} />
        </div>
        <h2 className="text-xl font-bold text-foreground">Confirmação Final</h2>
        <p className="text-muted-foreground text-sm">Esta é sua última oportunidade de cancelar</p>
      </div>

      <Alert className="border-[--danger-surface]" style={{ borderColor: '#FFBCBC', backgroundColor: 'var(--danger-surface)' }}>
        <AlertTriangle className="h-4 w-4 shrink-0" style={{ color: 'var(--danger-icon)' }} />
        <AlertDescription className="text-sm" style={{ color: '#B71C1C' }}>
          Você está prestes a excluir permanentemente a conta{' '}
          <strong className="break-all">{user.email}</strong>. Esta ação{' '}
          <strong>não pode ser desfeita</strong>.
        </AlertDescription>
      </Alert>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <Alert variant="destructive" className="animate-in fade-in duration-200">
            <AlertTriangle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="space-y-2">
          <Label htmlFor="confirmation" className="leading-relaxed">
            Digite{' '}
            <strong className="text-red-600 font-mono tracking-wide">{CONFIRMATION_TEXT}</strong>{' '}
            para confirmar:
          </Label>
          <Input
            id="confirmation"
            type="text"
            placeholder={CONFIRMATION_TEXT}
            value={confirmationInput}
            onChange={(e) => setConfirmationInput(e.target.value.toUpperCase())}
            disabled={isLoading}
            className="text-center font-mono tracking-widest text-sm"
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="characters"
            spellCheck={false}
          />
          <p className="text-xs text-muted-foreground text-center">
            {confirmationInput.length > 0 && !isValid ? (
              <span className="text-red-500">
                {CONFIRMATION_TEXT.length - confirmationInput.length > 0
                  ? `Faltam ${CONFIRMATION_TEXT.length - confirmationInput.length} caracteres`
                  : 'Texto não corresponde'}
              </span>
            ) : (
              'Digite exatamente como mostrado acima'
            )}
          </p>
        </div>

        <div className="flex gap-3 pt-2">
          <Button
            type="button"
            variant="outline"
            onClick={onBack}
            disabled={isLoading}
            className="flex-1"
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Cancelar
          </Button>
          <Button
            type="submit"
            disabled={!isValid || isLoading}
            className="flex-1 bg-danger-icon hover:bg-[#C62828] text-white disabled:opacity-50"
          >
            {isLoading ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Excluindo...
              </>
            ) : (
              <>
                <Trash2 className="mr-2 h-4 w-4" />
                Excluir Permanentemente
              </>
            )}
          </Button>
        </div>
      </form>

      {isLoading && (
        <div className="text-center space-y-1 animate-in fade-in duration-300">
          <p className="text-sm font-medium text-muted-foreground">
            Removendo seus dados...
          </p>
          <p className="text-xs text-muted-foreground">
            Isso pode levar alguns segundos. Não feche esta página.
          </p>
        </div>
      )}
    </div>
  );
}
