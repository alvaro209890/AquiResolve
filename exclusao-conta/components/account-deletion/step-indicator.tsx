'use client';

import { cn } from '@/lib/utils';
import { Check } from 'lucide-react';
import type { WizardStep } from '@/lib/types';

interface StepIndicatorProps {
  currentStep: WizardStep;
}

const steps = [
  { number: 1, label: 'Login' },
  { number: 2, label: 'Resumo' },
  { number: 3, label: 'Confirmação' },
  { number: 4, label: 'Resultado' },
];

export function StepIndicator({ currentStep }: StepIndicatorProps) {
  return (
    <div className="w-full mb-6 sm:mb-8">
      <div className="flex items-center justify-between">
        {steps.map((step, index) => (
          <div key={step.number} className="flex items-center flex-1 min-w-0">
            <div className="flex flex-col items-center shrink-0">
              {/* Círculo menor no mobile (36px) por falta de espaço, 40px no desktop */}
              <div
                className={cn(
                  'w-9 h-9 sm:w-10 sm:h-10 rounded-full flex items-center justify-center text-sm font-semibold transition-all duration-300',
                  currentStep > step.number
                    ? 'bg-success text-success-foreground'
                    : currentStep === step.number
                    ? 'bg-lgpd-primary text-lgpd-primary-foreground ring-4 ring-lgpd-primary-surface'
                    : 'bg-muted text-muted-foreground',
                )}
              >
                {currentStep > step.number ? (
                  <Check className="w-4 h-4 sm:w-5 sm:h-5" />
                ) : (
                  <span className="text-xs sm:text-sm">{step.number}</span>
                )}
              </div>

              {/* Labels — visíveis só em sm+ */}
              <span
                className={cn(
                  'text-xs mt-1.5 text-center hidden sm:block transition-colors duration-300 whitespace-nowrap',
                  currentStep >= step.number
                    ? 'text-foreground font-medium'
                    : 'text-muted-foreground',
                )}
              >
                {step.label}
              </span>
            </div>

            {/* Linha conectora */}
            {index < steps.length - 1 && (
              <div
                className={cn(
                  'flex-1 h-0.5 mx-1.5 sm:mx-2 rounded-full transition-all duration-500',
                  currentStep > step.number ? 'bg-success' : 'bg-muted',
                )}
              />
            )}
          </div>
        ))}
      </div>

      {/* Label de progresso textual — só no mobile, abaixo dos círculos */}
      <p className="text-center text-xs text-muted-foreground mt-3 sm:hidden">
        <span className="font-medium text-foreground">
          {steps[currentStep - 1]?.label}
        </span>
        {' — '}etapa {currentStep} de {steps.length}
      </p>
    </div>
  );
}
