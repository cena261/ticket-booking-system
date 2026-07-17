package com.ticketapp.controller.event;

import com.ticketapp.application.event.EventBrowseService;
import com.ticketapp.application.event.EventDetailView;
import com.ticketapp.controller.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventBrowseService eventBrowseService;

    public EventController(EventBrowseService eventBrowseService) {
        this.eventBrowseService = eventBrowseService;
    }

    @GetMapping("/{id}")
    public ApiResponse<EventDetailView> browse(@PathVariable Long id) {
        return ApiResponse.ok(eventBrowseService.browse(id));
    }
}
