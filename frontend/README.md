
## Como começar

Para rodar a aplicação utilize o comando:

```bash
npm install
npm run dev
```

Abra [http://localhost:3000](http://localhost:3000) com seu navegador favorito e veja a aplicação funcionando.

## Instale no VSCode

### Tailwind CSS IntelliSense

Usada para o autocomplete dos códigos Tailwind CSS.

### ESlint

Usada para padronizar todo nosso código, vale destacar que não precisa adicionar a extensão do Prettier.

## Boas práticas de versionamento (Gitflow)

### main

Aplicação principal, só terá versões estáveis para build.

### development

Aplicação com todas as funcionalidades adicionadas e que servirão de base para adição de novas features.

### Feature/[nome da feature]

Branch criada a partir da development para criação de nova funcionalidade, após finalizada é feito merge com a development

### Release/[nome da release]

Branch criada para testes de funcionalidades que deverão ser mescladas para main ou development

### Hotfix/[nome do hotfix]

Branch criada para correção de bugs presentes na main.
