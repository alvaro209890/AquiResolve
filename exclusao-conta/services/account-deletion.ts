export interface DeleteAccountPayload {
  idToken: string;
  userId: string;
  confirmationText: string;
}

export interface DeleteAccountResponse {
  success: boolean;
  message?: string;
  error?: string;
  phases?: Record<string, { success: boolean; error?: string; count?: number }>;
  deletedResources?: Record<string, number>;
}

export async function deleteAccount(
  payload: DeleteAccountPayload,
): Promise<DeleteAccountResponse> {
  const response = await fetch('/api/account/delete', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${payload.idToken}`,
    },
    body: JSON.stringify({
      userId: payload.userId,
      confirmationText: payload.confirmationText,
    }),
  });

  let data: DeleteAccountResponse;
  try {
    data = await response.json();
  } catch {
    throw new Error('Resposta inválida do servidor. Tente novamente.');
  }

  if (!response.ok || !data.success) {
    throw new Error(data.error || 'Erro ao processar a exclusão. Tente novamente.');
  }

  return data;
}
