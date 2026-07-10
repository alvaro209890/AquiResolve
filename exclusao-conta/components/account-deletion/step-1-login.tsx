'use client';

import { useState } from 'react';
import { signInWithEmailAndPassword } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '@/lib/firebase';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { AlertCircle, Mail, Lock, Loader2, Trash2, ShieldAlert } from 'lucide-react';
import type { UserData, UserType } from '@/lib/types';

interface Step1LoginProps {
  onSuccess: (user: UserData) => void;
}

const firebaseErrorMessages: Record<string, string> = {
  'auth/invalid-email': 'Email inválido. Verifique o formato do email.',
  'auth/user-disabled': 'Esta conta foi desativada.',
  'auth/user-not-found': 'Conta não encontrada. Verifique seu email.',
  'auth/wrong-password': 'Senha incorreta. Tente novamente.',
  'auth/invalid-credential': 'Email ou senha incorretos.',
  'auth/too-many-requests': 'Muitas tentativas. Aguarde alguns minutos.',
  'auth/network-request-failed': 'Erro de conexão. Verifique sua internet.',
};

export function Step1Login({ onSuccess }: Step1LoginProps) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsLoading(true);

    try {
      const credential = await signInWithEmailAndPassword(auth, email, password);
      const { user } = credential;

      // Fetch the user document from Firestore to get real role/displayName
      let userType: UserType = 'cliente';
      let displayName = user.displayName;

      try {
        const userDoc = await getDoc(doc(db, 'users', user.uid));
        if (userDoc.exists()) {
          const data = userDoc.data();
          const role: string = data?.role ?? '';

          if (role === 'admin' || role === 'operador') {
            setError('Administradores e operadores não podem excluir a conta por este portal.');
            await auth.signOut();
            return;
          }

          userType = role === 'prestador' ? 'prestador' : 'cliente';
          displayName = data?.nome ?? data?.displayName ?? user.displayName;
        }
      } catch {
        // If Firestore fetch fails, proceed with Firebase Auth data
      }

      onSuccess({
        uid: user.uid,
        email: user.email ?? email,
        displayName,
        userType,
      });
    } catch (err) {
      const code = (err as { code?: string }).code ?? '';
      setError(firebaseErrorMessages[code] ?? 'Erro ao fazer login. Tente novamente.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="space-y-6 animate-in fade-in slide-in-from-right-4 duration-300">
      <div className="text-center space-y-2">
        <div className="w-16 h-16 rounded-full flex items-center justify-center mx-auto mb-4" style={{ backgroundColor: 'var(--danger-surface)' }}>
          <Trash2 className="w-8 h-8" style={{ color: 'var(--danger-icon)' }} />
        </div>
        <h1 className="text-2xl font-bold text-foreground">Excluir Minha Conta</h1>
        <p className="text-muted-foreground text-sm leading-relaxed">
          Conforme o <strong>Art. 18 da LGPD</strong>, você tem o direito à eliminação
          dos seus dados pessoais. Faça login para continuar.
        </p>
      </div>

      <Alert style={{ borderColor: 'var(--warning-border)', backgroundColor: 'var(--warning-surface)' }}>
        <ShieldAlert className="h-4 w-4" style={{ color: 'var(--warning-icon)' }} />
        <AlertDescription className="text-xs leading-relaxed" style={{ color: 'var(--warning-foreground)' }}>
          Todos os seus dados serão{' '}
          <strong>permanentemente excluídos</strong>{' '}
          e não poderão ser recuperados.
        </AlertDescription>
      </Alert>

      <form onSubmit={handleSubmit} className="space-y-4">
        {error && (
          <Alert variant="destructive" className="animate-in fade-in duration-200">
            <AlertCircle className="h-4 w-4" />
            <AlertDescription>{error}</AlertDescription>
          </Alert>
        )}

        <div className="space-y-2">
          <Label htmlFor="email">Email</Label>
          <div className="relative">
            <Mail className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              id="email"
              type="email"
              placeholder="seu@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={isLoading}
              className="pl-10"
            />
          </div>
        </div>

        <div className="space-y-2">
          <Label htmlFor="password">Senha</Label>
          <div className="relative">
            <Lock className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
            <Input
              id="password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={isLoading}
              className="pl-10"
            />
          </div>
        </div>

        <Button
          type="submit"
          className="w-full bg-lgpd-primary hover:bg-lgpd-primary-hover text-lgpd-primary-foreground"
          disabled={isLoading || !email || !password}
        >
          {isLoading ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              Autenticando...
            </>
          ) : (
            'Continuar'
          )}
        </Button>
      </form>

      <p className="text-center text-xs text-muted-foreground">
        Ao continuar, você concorda com nossa{' '}
        <a href="/privacy-policy" className="underline hover:text-foreground">
          Política de Privacidade
        </a>{' '}
        e{' '}
        <a href="/terms" className="underline hover:text-foreground">
          Termos de Uso
        </a>
        .
      </p>
    </div>
  );
}
