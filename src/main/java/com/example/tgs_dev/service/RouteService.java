package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.RouteRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RouteService {

    private final RouteRepository routeRepository;

    public RouteService(RouteRepository routeRepository) {
        this.routeRepository = routeRepository;
    }

    public Route save(Route route){
        return routeRepository.save(route);
    }

    public Route findById(Integer id){
        return routeRepository.findById(id).
                orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + id));
    }

    public List<Route> findAll() {
        return routeRepository.findAllByOrderByRouteNumberAsc();
    }

    public Optional<Route> findByNumber(String routeNumber) {
        return routeRepository.findOne(CommonSpecifications.fieldEquals("routeNumber", routeNumber));
    }

    public void delete(Route route){
        routeRepository.softDelete(route);
    }

    public Page<Route> filter(FilterRequest request) {
        return routeRepository.filter(request, request.toPageable());
    }
}
