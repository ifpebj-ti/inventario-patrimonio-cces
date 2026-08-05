-- =========================== USER ==================================

-- Definição da tabela de usuário
CREATE TABLE im_user
(
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(100) NOT NULL,
    email      VARCHAR(255) NOT NULL,
    password   VARCHAR(255) NOT NULL,
    telephone  VARCHAR(20)  NOT NULL,
    verified   BOOLEAN      NOT NULL,
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL,

    CONSTRAINT uk_user_email UNIQUE (email)
);

-- Função para atualizar o timestamp de updated_at
CREATE OR REPLACE FUNCTION update_updated_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language plpgsql;

-- Função para definir o timestamp de created_at na inserção
CREATE OR REPLACE FUNCTION set_created_at_column()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.created_at = CURRENT_TIMESTAMP;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language plpgsql;

-- Trigger para atualizar updated_at na tabela user
CREATE TRIGGER update_user_updated_at
    BEFORE UPDATE
    ON im_user
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Trigger para definir created_at e updated_at na inserção em user
CREATE TRIGGER set_user_created_at
    BEFORE INSERT
    ON im_user
    FOR EACH ROW
EXECUTE FUNCTION set_created_at_column();

-- =========================== USER TOKENS ================================

-- Definição da tabela de Tokens do usuário para verificação de email e senha
CREATE TABLE im_user_tokens
(
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255),
    token_type VARCHAR(55), -- pode ser um token de verificacao de email ou verificacao de senha
    expiration TIMESTAMP,
    id_user BIGINT NOT NULL,
    CONSTRAINT fk_user_tokens FOREIGN KEY (id_user) REFERENCES im_user (id)
);

CREATE OR REPLACE FUNCTION set_token_expiration()
RETURNS TRIGGER AS
$$
BEGIN
    IF NEW.token IS NOT NULL THEN
        NEW.expiration = NOW() + INTERVAL '30 minutes';
    ELSE
        NEW.expiration = NULL;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_set_token_expiration
    BEFORE INSERT OR UPDATE
    ON im_user_tokens
    FOR EACH ROW
EXECUTE FUNCTION set_token_expiration();

-- =========================== INVENTORY ==================================

-- Definição da tabela de inventário
CREATE TABLE im_inventory
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description VARCHAR(255),
    created_at  TIMESTAMP    NOT NULL,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_user     BIGINT       NOT NULL,
    CONSTRAINT fk_inventory_user FOREIGN KEY (id_user) REFERENCES im_user (id)
);

-- Trigger para atualizar updated_at na tabela inventory
CREATE TRIGGER update_inventory_updated_at
    BEFORE UPDATE
    ON im_inventory
    FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();

-- Trigger para definir created_at e updated_at na inserção em inventory
CREATE TRIGGER set_inventory_created_at
    BEFORE INSERT
    ON im_inventory
    FOR EACH ROW
EXECUTE FUNCTION set_created_at_column();

-- =========================== ITEM ==================================

CREATE TABLE im_item
(
    id           BIGSERIAL PRIMARY KEY,
    code         VARCHAR(255) NOT NULL,
    name         VARCHAR(255),
    description  VARCHAR(1000),
    price        BIGINT,
    locale       VARCHAR(255),
    responsible  VARCHAR(255),
    qr_code      VARCHAR(255) NOT NULL,
    is_valid     BOOLEAN      NOT NULL DEFAULT FALSE,
    validated_at TIMESTAMP,
    id_inventory BIGINT       NOT NULL,

    CONSTRAINT fk_inventory FOREIGN KEY (id_inventory) REFERENCES im_inventory (id)
);

-- =========================== OBSERVATION ==================================

CREATE TABLE im_observation
(
    id      BIGSERIAL PRIMARY KEY,
    content VARCHAR(1000) NOT NULL,
    id_item BIGINT       NOT NULL,
    CONSTRAINT fk_observation_item FOREIGN KEY (id_item) REFERENCES im_item (id)
);

-- Índice para otimizar consultas de observações por item
CREATE INDEX idx_observation_item ON im_observation (id_item);

