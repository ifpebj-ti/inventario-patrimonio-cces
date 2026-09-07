FROM node:20.16

WORKDIR /opt/docker/app

# Copiar package.json e package-lock.json
COPY package*.json .

# Executar npm ci para instalar as dependências
RUN npm ci

# Copiar o restante do código para o contêiner
COPY . .

# O Next inlina as variáveis NEXT_PUBLIC_* no bundle durante o build, então ela
# precisa existir AQUI: definir no runtime do contêiner não tem efeito nenhum.
# Declarada depois do npm ci para que trocar o client id não invalide a camada
# de instalação de dependências.
ARG NEXT_PUBLIC_GOOGLE_CLIENT_ID
ENV NEXT_PUBLIC_GOOGLE_CLIENT_ID=${NEXT_PUBLIC_GOOGLE_CLIENT_ID}

# Compilar o código
RUN npm run build

# Expor a porta 3000
EXPOSE 3000

# Comando para iniciar a aplicação em produção
CMD ["npm", "run", "start"]
