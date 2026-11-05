package com.repairshop.saas.ticket.repository;

import com.repairshop.saas.ticket.entity.TechnicianKycDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TechnicianKycDocumentRepository extends JpaRepository<TechnicianKycDocument, UUID> {

    List<TechnicianKycDocument> findByTechnicianIdOrderByCreatedAtAsc(UUID technicianId);

    Optional<TechnicianKycDocument> findByTechnicianIdAndDocType(UUID technicianId, String docType);

    void deleteByTechnicianIdAndDocType(UUID technicianId, String docType);
}
