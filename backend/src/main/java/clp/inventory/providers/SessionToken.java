package clp.inventory.providers;

import java.time.Instant;

/** O que o servidor lê de um JWT já verificado (assinatura, issuer e validade). */
public record SessionToken(long userId, String jti, int version, Instant expiresAt) {}
