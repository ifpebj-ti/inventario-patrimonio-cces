// Nomes de exibição das permissões. Os códigos (ex.: MANAGE_SECTOR) continuam sendo os valores
// usados pelo servidor; aqui só mudam como aparecem na tela. Cargos têm o nome que o administrador
// deu a eles, sem tradução.
const permissionLabels: Record<string, string> = {
  MANAGE_SECTOR: 'Gerenciar Setor',
  MANAGE_ITEM: 'Gerenciar Item',
}

export const permissionLabel = (name: string) => permissionLabels[name] ?? name
