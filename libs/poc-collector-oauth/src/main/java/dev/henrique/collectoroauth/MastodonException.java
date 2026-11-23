package dev.henrique.collectoroauth;

/** Falha ao consultar a instância. A mensagem é segura para log: não inclui token nem cabeçalhos. */
public final class MastodonException extends RuntimeException {
    public MastodonException(String message) {
        super(message);
    }
}
