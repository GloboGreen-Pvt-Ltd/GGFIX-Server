package com.repairshop.saas.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Staff task wire shapes. Field names match the Partner App's task model, so
 * the app's store maps one-to-one. Dates are "yyyy-MM-dd", times "HH:mm".
 */
public final class StaffTaskDtos {

    private StaffTaskDtos() {}

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Assignee {
        private String id;          // technician id
        private String name;
        private String roleLabel;
        private String phone;
        private String photoUrl;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class ChecklistItem {
        private String id;
        private String text;
        private boolean done;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class HistoryItem {
        private String at;
        private String action;
        private String note;
        private String by;          // OWNER | EMPLOYEE
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class Coords {
        private Double latitude;
        private Double longitude;
    }

    /** Create (all required fields) and edit (only the fields sent change). */
    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskRequest {
        private String type;
        private String title;
        private String description;
        private String date;
        private String startTime;
        private String endTime;
        private List<Assignee> assignees;
        private String priority;
        private Integer reminder;
        private String repeat;
        private String location;
        private Coords coords;
        private List<ChecklistItem> checklist;
        private String clientRef;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class StatusRequest {
        private String status;
        private String note;
    }

    @Data @NoArgsConstructor @AllArgsConstructor @Builder
    public static class TaskResponse {
        private String id;
        private String type;
        private String title;
        private String description;
        private String date;
        private String startTime;
        private String endTime;
        private List<Assignee> assignees;
        private String priority;
        private Integer reminder;
        private String repeat;
        private String location;
        private Coords coords;
        private List<ChecklistItem> checklist;
        private String status;
        private String incompleteReason;
        private List<HistoryItem> history;
        private Instant createdAt;
        private Instant updatedAt;
    }
}
