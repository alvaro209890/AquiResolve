import type { Metadata } from 'next';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Termos de Uso | Aqui Resolve',
  description: 'Termos de Uso do aplicativo Aqui Resolve.',
};

export default function TermsPage() {
  return (
    <div className="min-h-dvh bg-surface-page flex flex-col">
      <main className="flex-1 flex flex-col items-center py-10 px-4">
        <div className="w-full max-w-2xl space-y-6">
          <Link
            href="/"
            className="inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground transition-colors"
          >
            <ArrowLeft className="h-4 w-4" />
            Voltar
          </Link>

          <div className="space-y-1">
            <h1 className="text-2xl font-bold text-foreground">Termos de Uso</h1>
            <p className="text-sm text-muted-foreground">Última atualização: {new Date().toLocaleDateString('pt-BR')}</p>
          </div>

          <div className="prose prose-sm max-w-none space-y-5 text-sm leading-relaxed text-foreground/90">
            <section className="space-y-2">
              <h2 className="text-base font-semibold">1. Aceitação dos termos</h2>
              <p>
                Ao criar uma conta e utilizar o aplicativo <strong>Aqui Resolve</strong>, você
                concorda integralmente com estes Termos de Uso. Caso não concorde, não utilize a
                plataforma.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">2. Descrição do serviço</h2>
              <p>
                A Aqui Resolve é uma plataforma que conecta clientes a prestadores de serviços,
                permitindo a solicitação, contratação, acompanhamento e pagamento de serviços
                através do aplicativo.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">3. Cadastro e conta</h2>
              <p>
                O usuário é responsável por fornecer informações verdadeiras, completas e
                atualizadas no momento do cadastro, e por manter a confidencialidade de suas
                credenciais de acesso.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">4. Responsabilidades</h2>
              <p>
                Prestadores de serviço são responsáveis pela qualidade e execução dos serviços
                oferecidos. A Aqui Resolve atua como intermediadora e não se responsabiliza por
                danos decorrentes da execução dos serviços contratados entre as partes.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">5. Pagamentos</h2>
              <p>
                Pagamentos realizados na plataforma são processados por parceiros especializados.
                Taxas e condições de pagamento são informadas ao usuário antes da confirmação de
                cada transação.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">6. Exclusão de conta</h2>
              <p>
                O usuário pode solicitar a exclusão permanente de sua conta a qualquer momento
                através da{' '}
                <Link href="/" className="underline hover:text-foreground">
                  página de exclusão de conta
                </Link>
                . A exclusão é irreversível e segue o disposto em nossa{' '}
                <Link href="/privacy-policy" className="underline hover:text-foreground">
                  Política de Privacidade
                </Link>
                .
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">7. Alterações nos termos</h2>
              <p>
                Estes Termos podem ser atualizados periodicamente. O uso continuado do aplicativo
                após alterações constitui aceitação dos novos termos.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">8. Contato</h2>
              <p>
                Dúvidas sobre estes Termos podem ser enviadas para{' '}
                <a href="mailto:aquiresolveservico123@gmail.com" className="underline hover:text-foreground">
                  aquiresolveservico123@gmail.com
                </a>
                .
              </p>
            </section>
          </div>
        </div>
      </main>
    </div>
  );
}
