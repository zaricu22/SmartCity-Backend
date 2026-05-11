package com.example.smartcityback.asset.application.service;

import com.example.smartcityback.asset.application.dto.PublicBuildingDto;
import com.example.smartcityback.asset.application.exception.BuildingNotFoundException;
import com.example.smartcityback.asset.application.mapper.BuildingDtoMapper;
import com.example.smartcityback.asset.domain.aggregate.PublicBuilding;
import com.example.smartcityback.asset.domain.repository.PublicBuildingRepository;
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

    public List<PublicBuildingDto> getAll() {
        List<PublicBuilding> buildings = repository.findAll();

        return buildings.stream()
                .map(BuildingDtoMapper::toDto)
                .toList();
    }
}



