package com.example.smartcityback.asset.infrastructure.persistence.implementation;

import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.shared.PagedResult;
import com.example.smartcityback.asset.infrastructure.persistence.entity.PublicBuildingJpaEntity;
import com.example.smartcityback.asset.infrastructure.persistence.interfaces.PublicBuildingJpaRepository;
import com.example.smartcityback.asset.infrastructure.persistence.mapper.PublicBuildingMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Repository
@Slf4j
public class PublicBuildingRepositoryImpl implements PublicBuildingRepository {

    private final PublicBuildingJpaRepository jpaRepository;

    public PublicBuildingRepositoryImpl(PublicBuildingJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Optional<PublicBuilding> findById(UUID id) {
        return jpaRepository.findById(id).map(PublicBuildingMapper::toDomain);
    }

    @Override
    public PagedResult<PublicBuilding> findAll(int page, int size, String sortBy, String sortDir) {
        Sort.Direction direction = "desc".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Page<PublicBuildingJpaEntity> jpaPage = jpaRepository.findAll(PageRequest.of(page, size, Sort.by(direction, sortBy)));
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
