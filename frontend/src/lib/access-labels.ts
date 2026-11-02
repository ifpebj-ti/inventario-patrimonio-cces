// Nomes de exibição das permissões. Os códigos (ex.: MANAGE_SECTOR) continuam sendo os valores
// usados pelo servidor; aqui só mudam como aparecem na tela. Cargos têm o nome que o administrador
// deu a eles, sem tradução.
const permissionLabels: Record<string, string> = {
  VIEW_SECTOR: 'Consultar',
  MANAGE_SECTOR: 'Gerenciar Setor',
  MANAGE_ITEM: 'Gerenciar Item',
}

export const permissionLabel = (name: string) => permissionLabels[name] ?? name

// Ordem de exibição: da mais simples para a mais ampla. Permissão desconhecida vai para o fim.
export const permissionOrder = ['VIEW_SECTOR', 'MANAGE_ITEM', 'MANAGE_SECTOR']
export const comparePermissions = (a: string, b: string) => {
  const rank = (name: string) => {
    const index = permissionOrder.indexOf(name)
    return index === -1 ? permissionOrder.length : index
  }
  return rank(a) - rank(b)
}
