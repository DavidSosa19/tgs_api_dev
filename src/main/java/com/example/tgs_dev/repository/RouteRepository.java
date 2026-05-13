package com.example.tgs_dev.repository;

import com.example.tgs_dev.entity.Route;
import com.example.tgs_dev.repository.base.BaseRepository;
import java.util.List;

public interface RouteRepository extends BaseRepository<Route,Integer> {

    List<Route> findAllByOrderByRouteNumberAsc();

}
