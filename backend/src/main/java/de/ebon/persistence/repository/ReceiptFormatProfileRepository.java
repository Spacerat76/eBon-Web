package de.ebon.persistence.repository;

import de.ebon.persistence.model.FormatProfileState;
import de.ebon.persistence.model.ReceiptFormatProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReceiptFormatProfileRepository extends JpaRepository<ReceiptFormatProfile, Long> {

    Optional<ReceiptFormatProfile>
            findFirstByStateAndStoreNameKeyAndStoreBranchKeyAndFingerprintAndFingerprintVersionOrderByVersionDesc(
            FormatProfileState state,
            String storeNameKey,
            String storeBranchKey,
            String fingerprint,
            int fingerprintVersion);
}
