package de.ebon.persistence.repository;

import de.ebon.persistence.model.ProductVariant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    List<ProductVariant> findByProductFamily_IdOrderByNameAsc(Long productFamilyId);

    long countByProductFamily_Id(Long productFamilyId);

    @Query("select new de.ebon.persistence.repository.IdCount(v.productFamily.id, count(v)) from ProductVariant v group by v.productFamily.id")
    List<IdCount> countGroupedByProductFamily();

    List<ProductVariant> findByActiveTrueOrderByProductFamily_NameAscNameAsc();
}
