// Perfis de acesso do Inventarium e o que cada um pode fazer na interface.
// O back-end é quem de fato autoriza; aqui só escondemos o que o perfil não pode usar.
//
// - admin    (ADMIN_ORGANIZATION): vê e gerencia tudo.
// - manager  (GESTOR_SETOR):       gerencia os inventários do seu setor e cadastra Operadores
//                                  e Consulta.
// - operator (OPERADOR_CAMPO):     vê o setor, escaneia, valida itens e adiciona observações.
// - consult  (CONSULTA):           somente leitura no seu setor.
export type AccessRole = 'admin' | 'manager' | 'operator' | 'consult'

export const roleOf = (permissions?: string[] | null): AccessRole => {
  if (permissions?.includes('ADMIN')) return 'admin'
  if (permissions?.includes('MANAGE_SECTOR')) return 'manager'
  if (permissions?.includes('MANAGE_ITEM')) return 'operator'
  return 'consult'
}

export const capabilitiesOf = (role: AccessRole) => {
  const admin = role === 'admin'
  const manager = role === 'manager'
  return {
    isAdmin: admin,
    // Criar, editar, finalizar e excluir inventários; importar e remover itens; editar dados.
    canManageInventories: admin || manager,
    // Escanear, validar itens e registrar observações.
    canOperateItems: admin || manager || role === 'operator',
    // Cadastrar usuários e gerenciar acessos (o gestor, só Operador e Consulta no seu setor).
    canManageUsers: admin || manager,
    // Tela de setores: admin gerencia, gestor consulta os seus.
    canReadSectors: admin || manager,
  }
}

export const roleLabel: Record<AccessRole, string> = {
  admin: 'Administrador da Organização',
  manager: 'Gestor de Setor',
  operator: 'Operador de Campo',
  consult: 'Consulta',
}
