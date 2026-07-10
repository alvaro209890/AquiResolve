export type UserType = 'cliente' | 'prestador';

export interface UserData {
  uid: string;
  email: string;
  displayName: string | null;
  userType: UserType;
}

export interface DeleteAccountResponse {
  success: boolean;
  message?: string;
  error?: string;
}

export type WizardStep = 1 | 2 | 3 | 4;

export interface WizardState {
  currentStep: WizardStep;
  user: UserData | null;
  isLoading: boolean;
  error: string | null;
  deleteSuccess: boolean;
}
