package mostapha.bayi.demoescqrsaxon.query.entities;

import mostapha.bayi.demoescqrsaxon.enums.OperationType;
import jakarta.persistence.*;

import java.util.Date;
@Entity
public class AccountOperation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Date date;
    private double amount;
    @Enumerated(EnumType.STRING)
    private OperationType type;
    private String currency;
    @ManyToOne
    private Account account;
}
