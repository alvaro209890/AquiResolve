'use client';

import { useState } from 'react';
import { Button } from '@/components/ui/button';
import { Checkbox } from '@/components/ui/checkbox';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import {
  User,
  FileText,
  MessageSquare,
  CreditCard,
  MapPin,
  Shield,
  Key,
  AlertTriangle,
  ArrowLeft,
  ArrowRight,
  Image,
  Navigation,
  Scale,
} from 'lucide-react';
import type { UserData } from '@/lib/types';

interface Step2SummaryProps {
  user: UserData;
  onNext: () => void;
  onBack: () => void;
}

const dataToDelete = [
  { icon: User, label: 'Dados pessoais — nome, email, telefone, CPF, endereço' },
  { icon: FileText, label: 'Histórico de pedidos e serviços' },
  { icon: MessageSquare, label: 'Mensagens, conversas e suporte' },
  { icon: Image, label: 'Documentos enviados — fotos, certificados, registros' },
  { icon: CreditCard, label: 'Dados financeiros e transações' },
  { icon: MapPin, label: 'Endereços e localização salvos' },
  { icon: Navigation, label: 'Dados de atividade e localização' },
  { icon: Shield, label: 'Consentimentos e registros LGPD' },
  { icon: Key, label: 'Conta de autenticação (login)' },
];

const dataToAnonymize = [
  'Pedidos já concluídos (mantidos por obrigação fiscal, sem dados pessoais)',
  'Histórico financeiro e pagamentos realizados (anonimizado)',
];

export function Step2Summary({ user, onNext, onBack }: Step2SummaryProps) {
  const [understandPermanent, setUnderstandPermanent] = useState(false);
  const [understandAnonymize, setUnderstandAnonymize] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  const canProceed = understandPermanent && understandAnonymize && confirmDelete;

  return (
    <div className="space-y-5 animate-in fade-in slide-in-from-right-4 duration-300">
      <div className="text-center space-y-1">
        <h2 className="text-xl font-bold text-foreground">Resumo da Exclusão</h2>
        <p className="text-muted-foreground text-sm">
          Revise o que será excluído ou anonimizado permanentemente
        </p>
      </div>

      {/* User info */}
      <div className="bg-muted/50 rounded-lg p-4 space-y-1.5">
        <div className="flex items-center gap-2">
          <User className="h-4 w-4 text-muted-foreground shrink-0" />
          <span className="text-sm font-medium truncate">{user.displayName || 'Usuário'}</span>
        </div>
        <p className="text-sm text-muted-foreground pl-6 truncate">{user.email}</p>
        <div className="pl-6">
          <span className="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-700">
            {user.userType === 'prestador' ? 'Prestador de Serviços' : 'Cliente'}
          </span>
        </div>
      </div>

      {/* Data to be deleted */}
      <div className="space-y-2">
        <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-red-500 inline-block shrink-0" />
          Dados que serão excluídos permanentemente:
        </h3>
        {/* Grid 2 colunas no mobile para caber mais itens na tela */}
        <div className="grid grid-cols-1 xs:grid-cols-2 sm:grid-cols-1 gap-1.5">
          {dataToDelete.map((item, index) => (
            <div
              key={index}
              className="flex items-center gap-2 px-2.5 py-1.5 rounded-lg bg-red-50 border border-red-100"
            >
              <item.icon className="h-3.5 w-3.5 text-red-500 shrink-0" />
              <span className="text-xs text-red-700 leading-tight">{item.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Data to be anonymized */}
      <div className="space-y-2">
        <h3 className="text-sm font-semibold text-foreground flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-amber-500 inline-block shrink-0" />
          Mantidos anonimizados:
        </h3>
        <div className="grid gap-1.5">
          {dataToAnonymize.map((item, index) => (
            <div
              key={index}
              className="flex items-start gap-2 px-2.5 py-1.5 rounded-lg bg-amber-50 border border-amber-100"
            >
              <span className="text-amber-400 text-xs mt-0.5 shrink-0">•</span>
              <span className="text-xs text-amber-700 leading-tight">{item}</span>
            </div>
          ))}
        </div>
      </div>

      {/* LGPD Notice */}
      <div className="rounded-lg border border-blue-200 bg-blue-50 p-4 space-y-2">
        <div className="flex items-center gap-2">
          <Scale className="h-4 w-4 text-blue-600 shrink-0" />
          <span className="text-sm font-semibold text-blue-800">Seus direitos — LGPD</span>
        </div>
        <p className="text-xs text-blue-700 leading-relaxed">
          Esta exclusão é realizada conforme o <strong>Art. 18 da Lei nº 13.709/2018</strong>{' '}
          (LGPD), que garante ao titular o direito à eliminação dos dados pessoais tratados
          com seu consentimento.
        </p>
        <div className="flex gap-3 pt-1">
          <a
            href="/privacy-policy"
            className="text-xs text-blue-600 underline hover:text-blue-800 font-medium"
          >
            Política de Privacidade
          </a>
          <a
            href="/terms"
            className="text-xs text-blue-600 underline hover:text-blue-800 font-medium"
          >
            Termos de Uso
          </a>
        </div>
      </div>

      {/* Warning */}
      <Alert
        className="border-warning-border bg-warning-surface"
        style={{ borderColor: 'var(--warning-border)', backgroundColor: 'var(--warning-surface)' }}
      >
        <AlertTriangle className="h-4 w-4" style={{ color: 'var(--warning-icon)' }} />
        <AlertDescription className="text-xs font-medium" style={{ color: 'var(--warning-foreground)' }}>
          Esta ação é <strong>IRREVERSÍVEL</strong>. Os dados excluídos não poderão ser
          recuperados sob nenhuma circunstância.
        </AlertDescription>
      </Alert>

      {/* Confirmations */}
      <div className="space-y-3 pt-1">
        <div className="flex items-start space-x-3">
          <Checkbox
            id="understand"
            checked={understandPermanent}
            onCheckedChange={(checked) => setUnderstandPermanent(checked === true)}
            className="mt-0.5"
          />
          <Label htmlFor="understand" className="text-sm leading-snug cursor-pointer">
            Entendo que esta ação é <strong>permanente e irreversível</strong>
          </Label>
        </div>

        <div className="flex items-start space-x-3">
          <Checkbox
            id="understand-anonymize"
            checked={understandAnonymize}
            onCheckedChange={(checked) => setUnderstandAnonymize(checked === true)}
            className="mt-0.5"
          />
          <Label htmlFor="understand-anonymize" className="text-sm leading-snug cursor-pointer">
            Entendo que pedidos concluídos serão mantidos anonimizados por exigência fiscal
          </Label>
        </div>

        <div className="flex items-start space-x-3">
          <Checkbox
            id="confirm"
            checked={confirmDelete}
            onCheckedChange={(checked) => setConfirmDelete(checked === true)}
            className="mt-0.5"
          />
          <Label htmlFor="confirm" className="text-sm leading-snug cursor-pointer">
            Confirmo que desejo excluir minha conta e todos os dados associados
          </Label>
        </div>
      </div>

      {/* Actions */}
      <div className="flex gap-3 pt-1">
        <Button variant="outline" onClick={onBack} className="flex-1">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Voltar
        </Button>
        <Button
          onClick={onNext}
          disabled={!canProceed}
          className="flex-1 bg-lgpd-primary hover:bg-lgpd-primary-hover text-lgpd-primary-foreground disabled:bg-lgpd-primary-disabled disabled:opacity-100"
        >
          Continuar
          <ArrowRight className="ml-2 h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
