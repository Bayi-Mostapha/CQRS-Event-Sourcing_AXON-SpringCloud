package mostapha.bayi.demoescqrsaxon.events;

import mostapha.bayi.demoescqrsaxon.enums.AccountStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter @AllArgsConstructor @NoArgsConstructor
public class AccountStatusUpdatedEvent {
    private String accountId;
    private AccountStatus status;
}
