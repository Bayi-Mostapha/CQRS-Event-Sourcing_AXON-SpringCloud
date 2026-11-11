package mostapha.bayi.demoescqrsaxon.commande.dtos;

import mostapha.bayi.demoescqrsaxon.enums.AccountStatus;

public record UpdateAccountStatusDTO(String accountId, AccountStatus status) {
}
