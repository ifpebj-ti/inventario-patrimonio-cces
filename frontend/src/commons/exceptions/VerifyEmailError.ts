// Exceção de verificação de Email
export class VerifyEmailError extends Error {
  constructor(public message: string) {
    super(message)
    this.name = 'VerifyEmailError'
  }
}
