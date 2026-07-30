package com.example.smartcityback.asset.infrastructure.persistence.implementation;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.readmodel.PublicBuildingSummary;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.shared.PagedResult;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.interfaces.PublicBuildingJpaRepository;
import com.example.smartcityback.asset.infrastructure.persistence.mapper.PublicBuildingMapper;
import com.example.smartcityback.asset.infrastructure.persistence.specification.SubsidyEligibilityJpaSpecification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Slf4j
public class PublicBuildingRepositoryImpl implements PublicBuildingRepository {

    private final PublicBuildingJpaRepository jpaRepository;

    // @PersistenceContext cannot target a constructor parameter (its @Target is TYPE/METHOD/FIELD only),
    // so the shared EntityManager proxy is field-injected here as the one exception to constructor injection.
    @PersistenceContext
    private EntityManager entityManager;

    public PublicBuildingRepositoryImpl(PublicBuildingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PublicBuilding> findById(UUID id) {
        return jpaRepository.findById(id).map(PublicBuildingMapper::toDomain);
    }

    @Override
    public PagedResult<PublicBuilding> findAll(int page, int size, String sortBy, String sortDir) {
        Page<PublicBuildingJpaEntity> jpaPage = jpaRepository.findAll(pageRequest(page, size, sortBy, sortDir));
        return toPagedResult(jpaPage);
    }

    // Genuine query projection: the SQL SELECT itself only fetches id/name/location/consumption —
    // no full PublicBuildingJpaEntity is hydrated and devices are never joined or loaded.
    // JpaSpecificationExecutor.findAll(Specification, Pageable) always returns Page<PublicBuildingJpaEntity>
    // (fixed to the entity type), so it can't produce a projected result — a manual CriteriaQuery<PublicBuildingSummary>
    // is used instead, reusing SubsidyEligibilityJpaSpecification#toPredicate for the WHERE clause.
    @Override
    public PagedResult<PublicBuildingSummary> findEligibleForSubsidy(int page, int size, String sortBy, String sortDir) {
        Specification<PublicBuildingJpaEntity> spec = new SubsidyEligibilityJpaSpecification();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();

        CriteriaQuery<PublicBuildingSummary> selectQuery = cb.createQuery(PublicBuildingSummary.class);
        Root<PublicBuildingJpaEntity> root = selectQuery.from(PublicBuildingJpaEntity.class);
        selectQuery.select(cb.construct(
                PublicBuildingSummary.class,
                root.get("id"),
                root.get("name"),
                root.get("location"),
                root.get("consumption").get("value"),
                root.get("consumption").get("unit")
        ));
        selectQuery.where(spec.toPredicate(root, selectQuery, cb));
        selectQuery.orderBy("desc".equalsIgnoreCase(sortDir)
                ? cb.desc(root.get(sortBy))
                : cb.asc(root.get(sortBy)));

        List<PublicBuildingSummary> content = entityManager.createQuery(selectQuery)
                .setFirstResult(page * size)
                .setMaxResults(size)
                .getResultList();

        CriteriaQuery<Long> countQuery = cb.createQuery(Long.class);
        Root<PublicBuildingJpaEntity> countRoot = countQuery.from(PublicBuildingJpaEntity.class);
        countQuery.select(cb.count(countRoot));
        countQuery.where(spec.toPredicate(countRoot, countQuery, cb));
        long totalElements = entityManager.createQuery(countQuery).getSingleResult();
        int totalPages = (int) Math.ceil((double) totalElements / size);

        return new PagedResult<>(content, totalElements, totalPages, page, size);
    }

    private PageRequest pageRequest(int page, int size, String sortBy, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        return PageRequest.of(page, size, Sort.by(direction, sortBy));
    }

    private PagedResult<PublicBuilding> toPagedResult(Page<PublicBuildingJpaEntity> jpaPage) {
        return new PagedResult<>(
                jpaPage.getContent().stream().map(PublicBuildingMapper::toDomain).toList(),
                jpaPage.getTotalElements(),
                jpaPage.getTotalPages(),
                jpaPage.getNumber(),
                jpaPage.getSize()
        );
    }

    @Override
    public void save(PublicBuilding building) {
        // If the PublicBuildingJpaEntity already exists in Hibernate session (ex. from getAll),
        // now we create another PublicBuildingJpaEntity object with the same ID and exception will be thrown
        PublicBuildingJpaEntity existing = jpaRepository.findById(building.getId())
                .orElse(null);

        if (!Objects.isNull(existing))
            PublicBuildingMapper.updateJpaEntity(existing, building);
        else
            existing = PublicBuildingMapper.toJpa(building);

        log.debug("Persisting PublicBuilding aggregateId={}", building.getId());

        jpaRepository.save(existing);
    }

    @Override
    public void delete(UUID id) {
        jpaRepository.deleteById(id);
    }
}
