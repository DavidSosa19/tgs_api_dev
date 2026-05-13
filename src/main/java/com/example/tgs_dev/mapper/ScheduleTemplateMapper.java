package com.example.tgs_dev.mapper;

import com.example.tgs_dev.controller.response.ScheduleTemplateDTO;
import com.example.tgs_dev.entity.ScheduleTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ScheduleTemplateMapper {

    private final RouteMapper routeMapper;

    public ScheduleTemplateDTO toDTO(ScheduleTemplate template) {
        if (template == null) return null;
        return new ScheduleTemplateDTO(
                template.getId(),
                template.getTemplateNumber(),
                template.getName(),
                template.getActive(),
                template.getStartTime(),
                routeMapper.toDTO(template.getRoute()),
                routeMapper.toDTO(template.getSecondaryRoute())
        );
    }

    public List<ScheduleTemplateDTO> toDTOList(List<ScheduleTemplate> templates) {
        return templates.stream().map(this::toDTO).toList();
    }

    public void updateEntity(ScheduleTemplate template, com.example.tgs_dev.controller.request.ScheduleTemplateRequest request,
                             com.example.tgs_dev.entity.Route route,
                             com.example.tgs_dev.entity.Route secondaryRoute) {
        template.setTemplateNumber(request.templateNumber());
        template.setName(request.name());
        template.setStartTime(request.startTime());
        template.setRoute(route);
        template.setSecondaryRoute(secondaryRoute);
        if (request.active() != null) {
            template.setActive(request.active());
        }
    }
}
