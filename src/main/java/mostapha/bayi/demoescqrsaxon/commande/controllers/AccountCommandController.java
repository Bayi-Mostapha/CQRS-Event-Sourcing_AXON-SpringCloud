package mostapha.bayi.demoescqrsaxon.commande.controllers;


import mostapha.bayi.demoescqrsaxon.commande.commands.AddAccountCommand;
import mostapha.bayi.demoescqrsaxon.commande.commands.CreditAccountCommand;
import mostapha.bayi.demoescqrsaxon.commande.commands.DebitAccountCommand;
import mostapha.bayi.demoescqrsaxon.commande.commands.UpdateAccountStatusCommand;
import mostapha.bayi.demoescqrsaxon.commande.dtos.AddNewAccountRequestDTO;
import mostapha.bayi.demoescqrsaxon.commande.dtos.CreditAccountRequestDTO;
import mostapha.bayi.demoescqrsaxon.commande.dtos.DebitAccountRequestDTO;
import mostapha.bayi.demoescqrsaxon.commande.dtos.UpdateAccountStatusDTO;
import org.axonframework.commandhandling.gateway.CommandGateway;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@RestController
@RequestMapping("/commands/accounts")
public class AccountCommandController {

    private CommandGateway commandGateway;
    private EventStore eventStore;

    public AccountCommandController(CommandGateway commandGateway, EventStore eventStore) {
        this.commandGateway = commandGateway;
        this.eventStore = eventStore;
    }

    @PostMapping("/add")
    public CompletableFuture<String> addNewAccount(@RequestBody AddNewAccountRequestDTO request){
            CompletableFuture<String> response = commandGateway.send(new AddAccountCommand(
                    UUID.randomUUID().toString(),
                    request.initialBalance(),
                    request.currency()
            ));
        return response;
    }
    @ExceptionHandler(Exception.class)
    public String exceptionHandler(Exception exception) {
        return exception.getMessage();
    }

    @PostMapping("/credit")
    public CompletableFuture<String> creditAccount(@RequestBody CreditAccountRequestDTO request){
        CompletableFuture<String> response = commandGateway.send(new CreditAccountCommand(
                request.accountId(),
                request.amount(),
                request.currency()
        ));
        return response;
    }

    @PostMapping("/debit")
    public CompletableFuture<String> debitAccount(@RequestBody DebitAccountRequestDTO request){
        CompletableFuture<String> response = commandGateway.send(new DebitAccountCommand(
                request.accountId(),
                request.amount(),
                request.currency()
        ));
        return response;
    }

    @PutMapping("/updateStatus")
    public CompletableFuture<String> updateStatus(@RequestBody UpdateAccountStatusDTO request){
        CompletableFuture<String> result = this.commandGateway.send(new UpdateAccountStatusCommand(
                request.accountId(),
                request.status()
        ));
        return result;
    }

    @GetMapping("/events/{accountId}")
    public Stream eventStore(@PathVariable String accountId){
        return eventStore.readEvents(accountId).asStream();
    }
}
