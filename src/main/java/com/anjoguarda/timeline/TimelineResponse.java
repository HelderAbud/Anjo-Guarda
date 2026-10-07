package com.anjoguarda.timeline;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TimelineResponse(UUID childId, LocalDate from, LocalDate to, List<TimelineEventResponse> events) {}
