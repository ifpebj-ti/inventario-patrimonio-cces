
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

## Boas praticas de versionamento

### main

Branch única de integração e entrega. Todo pull request aprovado entra em `main`.

### Feature/[nome da feature]

Branch criada a partir da `main` para uma nova funcionalidade. Depois de revisada, e mesclada de volta na `main` por pull request.

### Hotfix/[nome do hotfix]

Branch criada a partir da `main` para correcao urgente. O pull request de retorno para `main` deve ser marcado como release `patch`.
