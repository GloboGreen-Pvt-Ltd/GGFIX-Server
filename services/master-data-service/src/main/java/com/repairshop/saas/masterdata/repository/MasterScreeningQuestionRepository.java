package com.repairshop.saas.masterdata.repository;

import com.repairshop.saas.masterdata.entity.MasterScreeningQuestion;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MasterScreeningQuestionRepository extends JpaRepository<MasterScreeningQuestion, UUID> {

    List<MasterScreeningQuestion> findByFlowAndIsActiveTrueOrderBySortOrderAsc(String flow);

    List<MasterScreeningQuestion> findAllByOrderBySortOrderAsc();
}
