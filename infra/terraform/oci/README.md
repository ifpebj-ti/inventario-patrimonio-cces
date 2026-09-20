# Terraform OCI

Esta pasta contem a configuracao inicial do Terraform para a Oracle Cloud Infrastructure.

Neste primeiro passo, o Terraform ainda nao cria recursos. Ele apenas configura o provider da OCI e consulta os Availability Domains da tenancy/regiao configurada. Isso serve para validar que as credenciais locais conseguem autenticar na Oracle.

## Arquivos

- `versions.tf`: define a versao minima do Terraform e o provider `oracle/oci`.
- `providers.tf`: configura o provider OCI com os dados vindos das variaveis.
- `variables.tf`: declara os valores esperados no `terraform.tfvars`.
- `auth-check.tf`: faz uma consulta read-only na Oracle para validar autenticacao.
- `network.tf`: declara a VCN, a subnet publica da aplicacao, a subnet privada de observabilidade, o Internet Gateway, a tabela de rotas publica e uma security list base.
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
