import type { Metadata } from 'next';
import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Política de Privacidade | Aqui Resolve',
  description: 'Política de Privacidade do aplicativo Aqui Resolve, em conformidade com a LGPD.',
};

export default function PrivacyPolicyPage() {
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
            <h1 className="text-2xl font-bold text-foreground">Política de Privacidade</h1>
            <p className="text-sm text-muted-foreground">Última atualização: {new Date().toLocaleDateString('pt-BR')}</p>
          </div>

          <div className="prose prose-sm max-w-none space-y-5 text-sm leading-relaxed text-foreground/90">
            <section className="space-y-2">
              <h2 className="text-base font-semibold">1. Introdução</h2>
              <p>
                Esta Política de Privacidade descreve como a <strong>Aqui Resolve</strong> coleta,
                usa, armazena e protege os dados pessoais dos usuários do aplicativo, em
                conformidade com a Lei Geral de Proteção de Dados (Lei nº 13.709/2018 — LGPD).
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">2. Dados que coletamos</h2>
              <ul className="list-disc pl-5 space-y-1">
                <li>Dados de cadastro: nome, e-mail, telefone, CPF e endereço</li>
                <li>Dados de uso: histórico de pedidos, serviços solicitados e mensagens</li>
                <li>Documentos enviados: fotos, certificados e comprovantes</li>
                <li>Dados financeiros: informações de pagamento e transações</li>
                <li>Dados de localização, quando autorizados pelo usuário</li>
              </ul>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">3. Finalidade do tratamento</h2>
              <p>
                Os dados são utilizados para viabilizar a prestação e contratação de serviços na
                plataforma, comunicação entre clientes e prestadores, processamento de pagamentos,
                suporte ao usuário e cumprimento de obrigações legais e fiscais.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">4. Compartilhamento de dados</h2>
              <p>
                Não vendemos dados pessoais. Dados podem ser compartilhados com prestadores de
                serviço envolvidos na operação (processamento de pagamentos, infraestrutura em
                nuvem) e quando exigido por lei ou ordem judicial.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">5. Seus direitos (Art. 18 da LGPD)</h2>
              <p>Você tem direito a, a qualquer momento:</p>
              <ul className="list-disc pl-5 space-y-1">
                <li>Confirmar a existência de tratamento de dados</li>
                <li>Acessar, corrigir ou atualizar seus dados</li>
                <li>Solicitar a anonimização, bloqueio ou eliminação de dados desnecessários</li>
                <li>
                  Solicitar a <strong>exclusão completa da sua conta</strong> através da{' '}
                  <Link href="/" className="underline hover:text-foreground">
                    página de exclusão de conta
                  </Link>
                </li>
                <li>Revogar o consentimento dado anteriormente</li>
              </ul>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">6. Retenção de dados</h2>
              <p>
                Após a exclusão da conta, os dados pessoais são removidos permanentemente. Pedidos
                já concluídos podem ser mantidos de forma anonimizada, sem vínculo com dados
                pessoais, exclusivamente para cumprimento de obrigações fiscais e contábeis.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">7. Segurança</h2>
              <p>
                Adotamos medidas técnicas e organizacionais para proteger os dados contra acesso
                não autorizado, perda, alteração ou destruição, incluindo autenticação segura e
                criptografia em trânsito.
              </p>
            </section>

            <section className="space-y-2">
              <h2 className="text-base font-semibold">8. Contato</h2>
              <p>
                Dúvidas sobre esta Política ou solicitações relacionadas aos seus dados pessoais
                podem ser enviadas para{' '}
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
