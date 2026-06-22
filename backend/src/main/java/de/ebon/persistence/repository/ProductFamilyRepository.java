package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductFamily;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductFamilyRepository extends JpaRepository<ProductFamily, Long> {

    Optional<ProductFamily> findByNameIgnoreCase(String name);

    List<ProductFamily> findByActiveTrueOrderByNameAsc();
}
