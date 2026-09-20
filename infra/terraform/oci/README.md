# Terraform OCI

Esta pasta contem a configuracao inicial do Terraform para a Oracle Cloud Infrastructure.

Neste primeiro passo, o Terraform ainda nao cria recursos. Ele apenas configura o provider da OCI e consulta os Availability Domains da tenancy/regiao configurada. Isso serve para validar que as credenciais locais conseguem autenticar na Oracle.

## Arquivos

- `versions.tf`: define a versao minima do Terraform e o provider `oracle/oci`.
- `providers.tf`: configura o provider OCI com os dados vindos das variaveis.
- `variables.tf`: declara os valores esperados no `terraform.tfvars`.
- `auth-check.tf`: faz uma consulta read-only na Oracle para validar autenticacao.
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
