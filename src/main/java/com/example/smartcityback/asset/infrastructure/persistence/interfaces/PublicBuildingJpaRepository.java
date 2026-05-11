package com.example.smartcityback.asset.infrastructure.persistence.interfaces;

import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PublicBuildingJpaRepository
        extends JpaRepository<PublicBuildingJpaEntity, UUID>
        /*, JpaSpecificationExecutor<PublicBuildingJpaEntity> */ {
    // Only save, findById, delete, findAll, query methods

    // NOTE: Spring Data JPA's Specification pattern can be used here
    //
    //   List<PublicBuildingJpaEntity> eligible =
    //       jpaRepository.findAll(new SubsidyEligibilityJpaSpecification());
    //
    // See: com.example.smartcityback.asset.infrastructure.persistence.specification.SubsidyEligibilityJpaSpecification

}
