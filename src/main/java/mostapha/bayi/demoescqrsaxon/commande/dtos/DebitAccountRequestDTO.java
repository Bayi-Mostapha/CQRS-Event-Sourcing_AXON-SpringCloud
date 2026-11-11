package mostapha.bayi.demoescqrsaxon.commande.dtos;

public record DebitAccountRequestDTO(String accountId, double amount, String currency) {
}
