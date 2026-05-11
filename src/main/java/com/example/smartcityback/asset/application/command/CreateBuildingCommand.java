package com.example.smartcityback.asset.application.command;

public record CreateBuildingCommand(
        String name,
        String location
) {}
