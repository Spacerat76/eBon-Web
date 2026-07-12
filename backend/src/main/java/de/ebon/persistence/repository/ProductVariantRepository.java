package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductVariant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductFamily_IdOrderByNameAsc(Long productFamilyId);

    long countByProductFamily_Id(Long productFamilyId);

    List<ProductVariant> findByActiveTrueOrderByProductFamily_NameAscNameAsc();
}
