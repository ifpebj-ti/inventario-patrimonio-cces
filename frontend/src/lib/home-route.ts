// Tela inicial de cada usuário: o dashboard é exclusivo de administradores.
// Quem não é administrador começa em Inventários, a primeira tela a que tem acesso.
export const homeRoute = (administrator?: boolean | null) =>
  administrator ? '/dashboard' : '/inventory'
