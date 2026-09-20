# Terraform OCI

Esta pasta contem a configuracao inicial do Terraform para a Oracle Cloud Infrastructure.

Neste primeiro passo, o Terraform ainda nao cria recursos. Ele apenas configura o provider da OCI e consulta os Availability Domains da tenancy/regiao configurada. Isso serve para validar que as credenciais locais conseguem autenticar na Oracle.

## Arquivos

- `versions.tf`: define a versao minima do Terraform e o provider `oracle/oci`.
- `providers.tf`: configura o provider OCI com os dados vindos das variaveis.
- `variables.tf`: declara os valores esperados no `terraform.tfvars`.
- `auth-check.tf`: faz uma consulta read-only na Oracle para validar autenticacao.
- `network.tf`: declara a VCN, a subnet publica da aplicacao, a subnet privada de observabilidade, o Internet Gateway, a tabela de rotas publica e uma security list base.
- `locals.tf`: monta listas de regras de rede a partir das variaveis.
- `security.tf`: declara os NSGs e as regras de entrada/saida das VMs.
- `state.tf`: cria o bucket privado e versionado que sera usado como backend remoto do Terraform state.
- `backend.oci.hcl.example`: modelo de configuracao do backend remoto, usado depois que o bucket existir.
- `outputs.tf`: mostra os Availability Domains retornados pela Oracle.
- `terraform.tfvars.example`: modelo versionado dos valores locais.
- `terraform.tfvars`: arquivo local com dados reais, ignorado pelo Git.

## Teste local

```bash
terraform init
terraform fmt
terraform validate
terraform plan
```

Se o `plan` exibir o output `availability_domains`, a autenticacao funcionou.

## Rede inicial

A VCN usa `10.42.0.0/24`, dividida inicialmente em subnets `/26`:

- `10.42.0.0/26`: subnet publica da aplicacao.
- `10.42.0.64/26`: subnet privada de observabilidade.

A subnet publica tem uma route table com `0.0.0.0/0` apontando para o Internet Gateway. Isso permite que VMs nessa subnet recebam IP publico e acessem a internet, desde que as regras de seguranca permitam.

A subnet privada usa `prohibit_public_ip_on_vnic = true`. Isso impede IP publico nas VMs dessa subnet. O acesso a ela deve acontecer por caminho interno, como SSH via VM da aplicacao, Bastion/VPN ou proxy controlado no futuro.

As duas subnets usam uma security list base sem regras de entrada. Ela libera apenas saida para internet. As regras de entrada, como SSH, HTTP e HTTPS, devem ser criadas depois com NSGs especificos por tipo de VM.

## NSGs iniciais

O NSG da aplicacao libera:

- SSH `22` somente para `allowed_ssh_cidrs`.
- HTTP `80` e HTTPS `443` somente para `allowed_web_cidrs`.
- Saida para internet.

O NSG de observabilidade nao recebe trafego direto da internet. Ele libera SSH `22` somente a partir da subnet publica da aplicacao, permitindo um acesso futuro via VM da aplicacao como ponto de entrada interno.

## State remoto

O bucket `inventarium-terraform-state` e criado como privado (`NoPublicAccess`) e com versionamento habilitado (`versioning = "Enabled"`). Ele nao deve ser acessado pela aplicacao.

O acesso ao state deve ser controlado por IAM da OCI e pelas credenciais usadas pelo Terraform. Para GitHub Actions, o ideal e usar credenciais/secrets especificos da pipeline, nao liberar o bucket por IP publico.

O backend remoto ainda nao esta ativado em `versions.tf`, porque o bucket precisa existir antes da migracao. O fluxo esperado e:

```bash
terraform apply
terraform init -migrate-state -backend-config=backend.oci.hcl
```

O arquivo real `backend.oci.hcl` deve ser criado localmente a partir de `backend.oci.hcl.example` e nao deve ser commitado se tiver OCIDs ou caminhos reais de chave.
