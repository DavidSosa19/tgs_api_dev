package com.example.tgs_dev.service;

import com.example.tgs_dev.controller.exception.ResourceNotFoundException;
import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.RouteRepository;
import com.example.tgs_dev.repository.filter.FilterRequest;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import com.example.tgs_dev.repository.specification.TenantSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class RouteService {

    private final RouteRepository routeRepository;
    private final TenantService   tenantService;

    public RouteService(RouteRepository routeRepository, TenantService tenantService) {
        this.routeRepository = routeRepository;
        this.tenantService   = tenantService;
    }

    /**
     * Saves a route, stamping the current tenant's company.
     */
    public Route save(Route route) {
        route.setCompany(tenantService.currentCompany());
        return routeRepository.save(route);
    }

    public Route findById(Integer id) {
        Integer companyId = tenantService.currentCompanyId();
        return routeRepository.findOne(
                Specification.<Route>where(CommonSpecifications.fieldEquals("id", id))
                        .and(TenantSpecifications.belongsToCompany(companyId))
        ).orElseThrow(() -> new ResourceNotFoundException("notFound.route|" + id));
    }

    public List<Route> findAll() {
        return routeRepository.findAll(
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }

    public Optional<Route> findByNumber(String routeNumber) {
        return routeRepository.findOne(
                CommonSpecifications.<Route>fieldEquals("routeNumber", routeNumber)
                        .and(TenantSpecifications.belongsToCompany(tenantService.currentCompanyId())));
    }

    public void delete(Route route) {
        routeRepository.softDelete(route);
    }

    public Page<Route> filter(FilterRequest request) {
        return routeRepository.filter(
                request,
                request.toPageable(),
                TenantSpecifications.belongsToCompany(tenantService.currentCompanyId()));
    }
}
