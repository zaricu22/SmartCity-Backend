package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.application.mapper.BuildingDtoMapper;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.readmodel.PublicBuildingSummary;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
import com.example.smartcityback.asset.shared.PagedResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly=true)
public class PublicBuildingQueryService {

    private final PublicBuildingRepository repository;

    public PublicBuildingQueryService(PublicBuildingRepository repository) {
        this.repository = repository;
    }

    public PublicBuildingDto getById(UUID id) {
        PublicBuilding building = repository.findById(id)
                .orElseThrow(BuildingNotFoundException::new);

        return BuildingDtoMapper.toDto(building);
    }

    public PagedResult<PublicBuildingDto> getAll(String name, String location, int page, int size, String sortBy, String sortDir) {
        return toDtoPage(repository.findAll(name, location, page, size, sortBy, sortDir));
    }

    public PagedResult<PublicBuildingDto> getEligibleForSubsidy(int page, int size, String sortBy, String sortDir) {
        PagedResult<PublicBuildingSummary> paged = repository.findEligibleForSubsidy(page, size, sortBy, sortDir);
        List<PublicBuildingDto> dtos = paged.content().stream()
                .map(s -> new PublicBuildingDto(
                        s.id(), s.name(), s.location(), s.consumptionValue(), s.consumptionUnit(), List.of(), null))
                .toList();
        return new PagedResult<>(dtos, paged.totalElements(), paged.totalPages(), paged.pageNumber(), paged.pageSize());
    }

    private PagedResult<PublicBuildingDto> toDtoPage(PagedResult<PublicBuilding> paged) {
        return new PagedResult<>(
                paged.content().stream().map(BuildingDtoMapper::toDto).toList(),
                paged.totalElements(),
                paged.totalPages(),
                paged.pageNumber(),
                paged.pageSize()
        );
    }
}



