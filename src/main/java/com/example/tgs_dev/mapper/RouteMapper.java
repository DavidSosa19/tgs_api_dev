package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.request.RouteRequest;
import com.example.tgs_dev.controller.response.RouteDTO;
import com.example.tgs_dev.entity.Route;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RouteMapper {

    public RouteDTO toDTO(Route route) {
        if (route == null) return null;
        return new RouteDTO(
                route.getId(),
                route.getRouteNumber(),
                route.getBaseDuration(),
                route.getCycleCount(),
                route.getActive()
        );
    }

    public List<RouteDTO> toDTOList(List<Route> routes) {
        return routes.stream().map(this::toDTO).toList();
    }

    public Route toEntity(RouteRequest request) {
        return new Route(
                request.routeNumber(),
                request.baseDuration(),
                request.cycleCount()
        );
    }

    public void updateEntity(Route route, RouteRequest request) {
        route.setRouteNumber(request.routeNumber());
        route.setBaseDuration(request.baseDuration());
        route.setCycleCount(request.cycleCount());
    }
}
