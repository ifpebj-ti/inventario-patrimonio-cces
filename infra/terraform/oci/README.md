# Terraform OCI

Esta pasta contem a configuracao inicial do Terraform para a Oracle Cloud Infrastructure.

Neste primeiro passo, o Terraform ainda nao cria recursos. Ele apenas configura o provider da OCI e consulta os Availability Domains da tenancy/regiao configurada. Isso serve para validar que as credenciais locais conseguem autenticar na Oracle.

## Arquivos

- `versions.tf`: define a versao minima do Terraform e o provider `oracle/oci`.
- `providers.tf`: configura o provider OCI com os dados vindos das variaveis.
- `variables.tf`: declara os valores esperados no `terraform.tfvars`.
- `auth-check.tf`: faz uma consulta read-only na Oracle para validar autenticacao.
- `network.tf`: declara a VCN, a subnet publica da aplicacao, a subnet privada de observabilidade, gateways, route tables e uma security list base.
- `locals.tf`: monta listas de regras de rede a partir das variaveis.
- `security.tf`: declara os NSGs e as regras de entrada/saida das VMs.
- `state.tf`: cria o bucket privado e versionado que sera usado como backend remoto do Terraform state.
- `compute.tf`: declara as VMs de aplicacao e observabilidade (IPs privados fixos).
- `public-ip.tf`: IP publico RESERVADO da VM de aplicacao (sobrevive a recriacao da VM; use-o no DNS).
- `backup.tf`: bucket privado de backups do Postgres, retencao (lifecycle) e IAM (dynamic group e policies) para a VM enviar dumps com instance principal.
- `cloud-init-app.yaml.tftpl`: grava os arquivos versionados de `infra/nginx/app-vm` e `infra/scripts/app-vm` na VM e dispara o `bootstrap-vm.sh` (Nginx, Docker, Portainer, timers de backup e de certificado).
- `cloud-init-observability.yaml.tftpl`: prepara a VM de observabilidade com Nginx e Docker.
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

Para permitir atualizacoes e download de imagens sem expor a observabilidade para entrada publica, a subnet privada usa uma route table propria com `0.0.0.0/0` apontando para o NAT Gateway.

As duas subnets usam uma security list base sem regras de entrada. Ela libera apenas saida para internet. As regras de entrada, como SSH, HTTP e HTTPS, devem ser criadas depois com NSGs especificos por tipo de VM.

## NSGs iniciais

O NSG da aplicacao libera:

- SSH `22` somente para `allowed_ssh_cidrs`.
- HTTP `80` e HTTPS `443` para `allowed_web_cidrs`. O site e publico e o Let's Encrypt (HTTP-01) valida pela porta 80, entao use `0.0.0.0/0`.
- Saida para internet.

A porta `9443` (Portainer) **nao** e mais exposta: o Portainer escuta so em `127.0.0.1` e a UI e publicada pelo Nginx em `portainer.<dominio>`, restrita aos IPs de `allowed_admin_cidrs` (o Terraform recusa `0.0.0.0/0` nessa variavel).

O NSG de observabilidade nao recebe trafego direto da internet. Ele libera SSH `22` somente a partir da subnet publica da aplicacao, permitindo um acesso futuro via VM da aplicacao como ponto de entrada interno.

## VMs iniciais

A VM de aplicacao nasce na subnet publica, sem IP publico efemero, e recebe o IP publico reservado (`public-ip.tf`). Usa o NSG da aplicacao e o IP privado fixo `cidrhost(subnet, 10)`. O cloud-init instala Nginx, Docker e Portainer CE e aplica a configuracao versionada; o passo a passo esta em `docs/wiki/Deploy-em-Producao-OCI.md`.

O `user_data` vai comprimido (`base64gzip`) porque o Nginx e os scripts passam do limite de 32 KB em texto puro. Ele tem `ignore_changes` na VM de aplicacao: a VM guarda o volume do Postgres e nao pode ser recriada por mudanca de Nginx ou de script. Para atualizar o Nginx use `infra/scripts/deploy-nginx.sh`; para reprovisionar de proposito use `terraform apply -replace=oci_core_instance.app` (apos garantir um backup).

A VM de observabilidade nasce na subnet privada, sem IP publico, e usa o NSG de observabilidade. O cloud-init instala Nginx e Docker para receber a stack de observabilidade e SonarQube depois.

## State remoto

O bucket `inventarium-terraform-state` e criado como privado (`NoPublicAccess`) e com versionamento habilitado (`versioning = "Enabled"`). Ele nao deve ser acessado pela aplicacao.

O acesso ao state deve ser controlado por IAM da OCI e pelas credenciais usadas pelo Terraform. Para GitHub Actions, o ideal e usar credenciais/secrets especificos da pipeline, nao liberar o bucket por IP publico.

O backend `oci` esta declarado em `versions.tf` e exige Terraform 1.12 ou superior. Como o bucket precisa existir antes da migracao do state, o fluxo da primeira vez e:

```bash
terraform init -backend=false   # cria o bucket do state com state local
terraform apply -target=oci_objectstorage_bucket.terraform_state
terraform init -migrate-state -backend-config=backend.oci.hcl
```

Nas demais execucoes basta `terraform init -backend-config=backend.oci.hcl`.

O arquivo real `backend.oci.hcl` deve ser criado localmente a partir de `backend.oci.hcl.example` e nao deve ser commitado se tiver OCIDs ou caminhos reais de chave.
