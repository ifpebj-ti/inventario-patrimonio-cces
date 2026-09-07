// O backend responde 401 com o mesmo corpo para token inválido e para domínio
// não permitido, de propósito, para não revelar qual verificação falhou.
// Por isso classificamos pelo status HTTP, nunca pelo texto do corpo.
export type AuthErrorKind =
  | 'unauthorized' // 401/400: token recusado ou conta fora do domínio permitido
  | 'unavailable' // 5xx ou falha de rede: backend fora do ar ou mal configurado
  | 'unexpected'

export class AuthError extends Error {
  constructor(
    public readonly kind: AuthErrorKind,
    message: string,
  ) {
    super(message)
    this.name = 'AuthError'
  }
}
