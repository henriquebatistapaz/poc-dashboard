package com.henrique.dashboard.api;

/** Corpo de DELETE /v1/items/{id}: o motivo da remoção é obrigatório. */
public record ItemRemovalRequest(String reason) {
}
