package mostapha.bayi.demoescqrsaxon.query.repository;

import mostapha.bayi.demoescqrsaxon.query.entities.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {
}
