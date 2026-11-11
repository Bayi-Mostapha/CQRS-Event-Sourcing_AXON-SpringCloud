package mostapha.bayi.demoescqrsaxon.commande.aggregate;

import mostapha.bayi.demoescqrsaxon.commande.commands.AddAccountCommand;
import mostapha.bayi.demoescqrsaxon.enums.AccountStatus;
import mostapha.bayi.demoescqrsaxon.events.AccountCreatedEvent;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.modelling.command.AggregateLifecycle;
import org.axonframework.spring.stereotype.Aggregate;

@Aggregate
public class AccountAggregate {
    private String accountId;
    private double balance;
    private AccountStatus status;
    //Pour axon
    public AccountAggregate() {}

    @CommandHandler
    public AccountAggregate(AddAccountCommand command) {
        //fonction de decision
        if (command.getInitialBalance()<=0) throw new IllegalArgumentException("Initial balance must be greater than zero");
        AggregateLifecycle.apply(new AccountCreatedEvent(
                command.getId(),
                command.getInitialBalance(),
                AccountStatus.CREATED,
                command.getCurrency()
        ));
    }
}
