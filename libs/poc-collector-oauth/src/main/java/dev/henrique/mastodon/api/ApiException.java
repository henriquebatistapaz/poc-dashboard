package dev.henrique.mastodon.api;

/** Erro de API com status HTTP explícito. A mensagem é segura para o cliente. */
public final class ApiException extends RuntimeException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
