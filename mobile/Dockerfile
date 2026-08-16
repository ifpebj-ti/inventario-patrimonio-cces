FROM node:20.16

WORKDIR /opt/docker/app

# Copiar package.json e package-lock.json
COPY package*.json .

# Executar npm ci para instalar as dependências
RUN npm ci

# Copiar o restante do código para o contêiner
COPY . .

# Compilar o código
RUN npm run build

# Expor a porta 3000
EXPOSE 3000

# Comando para iniciar a aplicação em produção
CMD ["npm", "run", "start"]
