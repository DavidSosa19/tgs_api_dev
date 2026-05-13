package com.example.tgs_dev.service;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.entity.RouteOperation;
import com.example.tgs_dev.entity.VehicleAssignment;
import com.example.tgs_dev.repository.RouteOperationRepository;
import com.example.tgs_dev.repository.specification.CommonSpecifications;
import jakarta.transaction.Transactional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import org.springframework.data.jpa.domain.Specification;

@Service
public class RouteOperationService {

    private final RouteOperationRepository routeOperationRepository;
    private final VehicleAssignmentService vehicleAssignmentService;

    public RouteOperationService(RouteOperationRepository routeOperationRepository,
                                 @Lazy VehicleAssignmentService vehicleAssignmentService) {
        this.routeOperationRepository = routeOperationRepository;
        this.vehicleAssignmentService = vehicleAssignmentService;
    }

    public RouteOperation save(RouteOperation routeOperation){
        return routeOperationRepository.save(routeOperation);
    }

    public RouteOperation findById(Integer id){
        return routeOperationRepository.findById(id)
                .orElseThrow(()-> new NoSuchElementException("notFound.routeOperation|" + id));
    }

    public List<RouteOperation> findAll(){
        return routeOperationRepository.findAll();
    }

    public List<RouteOperation> findAllByDate(LocalDate date){
        return routeOperationRepository.findAll(CommonSpecifications.fieldEquals("serviceDate",date));
    }

    public Optional<RouteOperation> findByRouteAndDate(Route route, LocalDate date) {
        Specification<RouteOperation> byRoute = CommonSpecifications.fieldEquals("route", route);
        Specification<RouteOperation> byDate = CommonSpecifications.fieldEquals("serviceDate", date);
        return routeOperationRepository.findOne(byRoute.and(byDate));
    }

    @Transactional
    public void softDelete(RouteOperation operation) {
        List<VehicleAssignment> assignments = vehicleAssignmentService.findByRouteOperation(operation);
        vehicleAssignmentService.softDeleteAll(assignments);
        routeOperationRepository.softDelete(operation);
    }

    @Transactional
    public void softDeleteAllByDate(LocalDate date) {
        List<RouteOperation> operations = findAllByDate(date);
        if (operations.isEmpty()) return;
        for (RouteOperation op : operations) {
            List<VehicleAssignment> assignments = vehicleAssignmentService.findByRouteOperation(op);
            vehicleAssignmentService.softDeleteAll(assignments);
        }
        routeOperationRepository.softDeleteAll(operations);
    }

    @Transactional
    public RouteOperation initRoutOperation(Route route, LocalDate date){
        RouteOperation newRouteOperation = new RouteOperation(route, date);
        return save(newRouteOperation);
    }
}
