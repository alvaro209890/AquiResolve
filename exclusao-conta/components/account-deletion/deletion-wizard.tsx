'use client';

import { useState, useCallback } from 'react';
import { signOut } from 'firebase/auth';
import { auth } from '@/lib/firebase';
import { deleteAccount } from '@/services/account-deletion';
import { BrandBar } from './brand-bar';
import { StepIndicator } from './step-indicator';
import { Step1Login } from './step-1-login';
import { Step2Summary } from './step-2-summary';
import { Step3Confirmation } from './step-3-confirmation';
import { Step4Result } from './step-4-result';
import type { UserData, WizardStep } from '@/lib/types';

const CONFIRMATION_TEXT = 'EXCLUIR MINHA CONTA';

export function DeletionWizard() {
  const [currentStep, setCurrentStep] = useState<WizardStep>(1);
  const [user, setUser] = useState<UserData | null>(null);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [deleteSuccess, setDeleteSuccess] = useState(false);

  const handleLoginSuccess = useCallback((userData: UserData) => {
    setUser(userData);
    setCurrentStep(2);
    setError(null);
  }, []);

  const handleBack = useCallback(() => {
    if (currentStep > 1) {
      setCurrentStep((prev) => (prev - 1) as WizardStep);
      setError(null);
    }
  }, [currentStep]);

  const handleNext = useCallback(() => {
    if (currentStep < 4) {
      setCurrentStep((prev) => (prev + 1) as WizardStep);
      setError(null);
    }
  }, [currentStep]);

  const handleDeleteAccount = useCallback(async () => {
    if (!user) return;

    setIsLoading(true);
    setError(null);

    try {
      const currentUser = auth.currentUser;
      if (!currentUser) {
        throw new Error('Sessão expirada. Por favor, faça login novamente.');
      }

      const idToken = await currentUser.getIdToken(true);

      await deleteAccount({
        idToken,
        userId: user.uid,
        confirmationText: CONFIRMATION_TEXT,
      });

      await signOut(auth).catch(() => null);

      setDeleteSuccess(true);
      setCurrentStep(4);
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Erro desconhecido. Tente novamente.';
      setError(message);
      setDeleteSuccess(false);
      setCurrentStep(4);
    } finally {
      setIsLoading(false);
    }
  }, [user]);

  const handleRetry = useCallback(() => {
    setCurrentStep(3);
    setError(null);
  }, []);

  const handleReset = useCallback(async () => {
    await signOut(auth).catch(() => null);
    setCurrentStep(1);
    setUser(null);
    setError(null);
    setDeleteSuccess(false);
  }, []);

  return (
    /*
     * Mobile: sem card — o fundo branco é a própria tela (feel nativo).
     * sm+: card flutuante com sombra e borda arredondada.
     *
     * pt-safe: garante que o conteúdo não fica atrás do notch no iOS
     * quando o card é full-screen (mobile).
     */
    <div
      className="
        bg-card w-full
        px-4 py-6
        pt-[calc(1.5rem+env(safe-area-inset-top))]
        sm:pt-6
        sm:rounded-xl sm:border sm:border-border sm:shadow-lg
        sm:px-8 sm:py-8
      "
    >
      <BrandBar />
      <StepIndicator currentStep={currentStep} />

      {currentStep === 1 && <Step1Login onSuccess={handleLoginSuccess} />}

      {currentStep === 2 && user && (
        <Step2Summary user={user} onNext={handleNext} onBack={handleBack} />
      )}

      {currentStep === 3 && user && (
        <Step3Confirmation
          user={user}
          onConfirm={handleDeleteAccount}
          onBack={handleBack}
          isLoading={isLoading}
          error={error}
        />
      )}

      {currentStep === 4 && (
        <Step4Result
          success={deleteSuccess}
          errorMessage={error ?? undefined}
          onRetry={handleRetry}
          onReset={handleReset}
        />
      )}
    </div>
  );
}
