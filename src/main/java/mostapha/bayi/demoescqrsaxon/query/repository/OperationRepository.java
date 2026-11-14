package mostapha.bayi.demoescqrsaxon.query.repository;

import mostapha.bayi.demoescqrsaxon.query.entities.AccountOperation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OperationRepository extends JpaRepository<AccountOperation, Long> {
    List<AccountOperation> findByAccountId(String id);
}
