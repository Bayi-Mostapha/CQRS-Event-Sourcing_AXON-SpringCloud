package mostapha.bayi.demoescqrsaxon.query.dtos;

import mostapha.bayi.demoescqrsaxon.query.entities.Account;
import mostapha.bayi.demoescqrsaxon.query.entities.AccountOperation;

import java.util.List;

public record AccountStatementResponseDTO(
        Account account, List<AccountOperation> operations){}
