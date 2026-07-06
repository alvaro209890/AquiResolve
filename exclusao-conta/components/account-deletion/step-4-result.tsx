'use client';

import { useEffect, useState } from 'react';
import { Button } from '@/components/ui/button';
import { CheckCircle2, XCircle, RefreshCw, Mail, ShieldCheck } from 'lucide-react';

interface Step4ResultProps {
  success: boolean;
  errorMessage?: string;
  onRetry: () => void;
  onReset: () => void;
}

const REDIRECT_DELAY = 10;
const SUPPORT_EMAIL = 'suporte@seuapp.com.br';
const REDIRECT_URL = 'https://seuapp.com.br';

export function Step4Result({ success, errorMessage, onRetry, onReset }: Step4ResultProps) {
  const [countdown, setCountdown] = useState(REDIRECT_DELAY);

  useEffect(() => {
    if (!success) return;

    const timer = setInterval(() => {
      setCountdown((prev) => {
        if (prev <= 1) {
          clearInterval(timer);
          window.location.href = REDIRECT_URL;
          return 0;
        }
        return prev - 1;
      });
    }, 1000);

    return () => clearInterval(timer);
  }, [success]);

  if (success) {
    return (
      <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500 text-center">
        <div className="w-20 h-20 bg-green-100 rounded-full flex items-center justify-center mx-auto">
          <CheckCircle2 className="w-10 h-10 text-green-600" />
        </div>

        <div className="space-y-2">
          <h2 className="text-2xl font-bold text-green-700">Conta Excluída</h2>
          <p className="text-muted-foreground text-sm leading-relaxed">
            Todos os seus dados foram permanentemente removidos dos nossos servidores,
            conforme solicitado.
          </p>
        </div>

        <div className="rounded-lg border border-green-200 bg-green-50 p-4 space-y-2">
          <div className="flex items-center justify-center gap-2">
            <ShieldCheck className="h-4 w-4 text-green-600" />
            <span className="text-sm font-semibold text-green-800">Conforme LGPD</span>
          </div>
          <p className="text-xs text-green-700">
            A exclusão foi registrada conforme o Art. 18 da Lei nº 13.709/2018.
          </p>
        </div>

        <div className="bg-muted/50 rounded-lg p-4 space-y-3">
          <p className="text-sm text-muted-foreground">
            Redirecionando em{' '}
            <span className="font-bold text-foreground tabular-nums">{countdown}s</span>
          </p>
          <div className="w-full bg-muted rounded-full h-1.5 overflow-hidden">
            <div
              className="bg-green-500 h-full rounded-full transition-all duration-1000 ease-linear"
              style={{ width: `${((REDIRECT_DELAY - countdown) / REDIRECT_DELAY) * 100}%` }}
            />
          </div>
        </div>

        <Button
          variant="outline"
          className="w-full"
          onClick={() => { window.location.href = REDIRECT_URL; }}
        >
          Ir para o site agora
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-6 animate-in fade-in zoom-in-95 duration-500 text-center">
      <div className="w-20 h-20 rounded-full flex items-center justify-center mx-auto" style={{ backgroundColor: 'var(--danger-surface)' }}>
        <XCircle className="w-10 h-10" style={{ color: 'var(--danger-icon)' }} />
      </div>

      <div className="space-y-2">
        <h2 className="text-2xl font-bold text-red-700">Erro ao Excluir</h2>
        <p className="text-muted-foreground text-sm leading-relaxed">
          {errorMessage || 'Ocorreu um erro ao processar sua solicitação.'}
        </p>
      </div>

      <div className="space-y-3">
        <Button
          onClick={onRetry}
          className="w-full bg-danger-icon hover:bg-[#C62828] text-white"
        >
          <RefreshCw className="mr-2 h-4 w-4" />
          Tentar Novamente
        </Button>
        <Button variant="outline" onClick={onReset} className="w-full">
          Voltar ao Início
        </Button>
      </div>

      <div className="border-t pt-4 space-y-1">
        <p className="text-xs text-muted-foreground">Se o problema persistir, contate o suporte:</p>
        <a
          href={`mailto:${SUPPORT_EMAIL}`}
          className="inline-flex items-center gap-1.5 text-blue-600 hover:text-blue-700 text-sm font-medium"
        >
          <Mail className="h-3.5 w-3.5" />
          {SUPPORT_EMAIL}
        </a>
      </div>
    </div>
  );
}
