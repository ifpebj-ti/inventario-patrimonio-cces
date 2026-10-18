// Nomes de exibição para perfis e permissões. Os códigos (ex.: GESTOR_SETOR)
// continuam sendo os valores usados pelo servidor; aqui só mudam como aparecem na tela.
const profileLabels: Record<string, string> = {
  ADMIN_ORGANIZATION: 'Administrador da Organização',
  GESTOR_SETOR: 'Gestor de Setor',
  OPERADOR_CAMPO: 'Operador de Campo',
  CONSULTA: 'Consulta',
}

const permissionLabels: Record<string, string> = {
  MANAGE_SECTOR: 'Gerenciar Setor',
  ADMIN: 'Administrador',
  MANAGE_ITEM: 'Gerenciar Item',
}

export const profileLabel = (name: string) => profileLabels[name] ?? name
export const permissionLabel = (name: string) => permissionLabels[name] ?? name
