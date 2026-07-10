import { DeletionWizard } from '@/components/account-deletion/deletion-wizard';
import { Scale } from 'lucide-react';

export default function HomePage() {
  return (
    /*
     * min-h-dvh: usa 100dvh que desconta a barra de endereço no iOS Safari.
     * Sem isso, `min-h-screen` causa overflow na primeira carga no iPhone.
     */
    <div className="min-h-dvh bg-surface-page flex flex-col">
      {/*
       * Mobile: sem padding → card preenche toda a largura (feel nativo).
       * Desktop: padding + itens centralizados → card flutuante.
       */}
      <main className="flex-1 flex flex-col sm:items-center sm:justify-center sm:p-6 sm:py-10">
        <div className="w-full sm:max-w-md">
          <DeletionWizard />
        </div>
      </main>

      <footer
        className="
          border-t bg-muted/30 py-3 px-4
          /* Padding extra para o home indicator no iPhone */
          pb-[calc(0.75rem+env(safe-area-inset-bottom))]
        "
      >
        <div className="max-w-md mx-auto flex flex-col sm:flex-row items-center justify-between gap-2 text-center sm:text-left">
          <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
            <Scale className="h-3.5 w-3.5 shrink-0" />
            <span>Em conformidade com a LGPD (Lei nº 13.709/2018)</span>
          </div>
          <div className="flex items-center gap-3 text-xs">
            <a
              href="/privacy-policy"
              className="text-muted-foreground hover:text-foreground underline underline-offset-2 transition-colors"
            >
              Privacidade
            </a>
            <a
              href="/terms"
              className="text-muted-foreground hover:text-foreground underline underline-offset-2 transition-colors"
            >
              Termos
            </a>
          </div>
        </div>
      </footer>
    </div>
  );
}
