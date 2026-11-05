package com.repairshop.saas.shop.dto;

import com.fasterxml.jackson.annotation.JsonSetter;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShopPickupSlotRequest {

    /**
     * 1=Monday … 7=Sunday (ISO-8601). Null = any day.
     *
     * Tolerant of any of these JSON shapes (admin web sends strings, mobile
     * may send numbers):
     *   { "dayOfWeek": 1 }          { "dayOfWeek": "1" }
     *   { "dayOfWeek": "MONDAY" }   { "dayOfWeek": "Mon" }
     *   { "dayOfWeek": null }       { "dayOfWeek": "" }
     */
    private Short dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer capacity;

    private static final Map<String, Short> NAME_TO_DOW = Map.ofEntries(
            Map.entry("MON",       (short) 1), Map.entry("MONDAY",    (short) 1),
            Map.entry("TUE",       (short) 2), Map.entry("TUES",      (short) 2), Map.entry("TUESDAY",   (short) 2),
            Map.entry("WED",       (short) 3), Map.entry("WEDS",      (short) 3), Map.entry("WEDNESDAY", (short) 3),
            Map.entry("THU",       (short) 4), Map.entry("THUR",      (short) 4), Map.entry("THURS",     (short) 4),
            Map.entry("THURSDAY",  (short) 4),
            Map.entry("FRI",       (short) 5), Map.entry("FRIDAY",    (short) 5),
            Map.entry("SAT",       (short) 6), Map.entry("SATURDAY",  (short) 6),
            Map.entry("SUN",       (short) 7), Map.entry("SUNDAY",    (short) 7)
    );

    @JsonSetter("dayOfWeek")
    public void setDayOfWeekAny(Object value) {
        if (value == null) { this.dayOfWeek = null; return; }
        if (value instanceof Number) { this.dayOfWeek = ((Number) value).shortValue(); return; }
        if (value instanceof String) {
            String s = ((String) value).trim();
            if (s.isEmpty()) { this.dayOfWeek = null; return; }
            // Try numeric string first ("1".."7").
            try { this.dayOfWeek = Short.parseShort(s); return; } catch (NumberFormatException ignored) { /* fall */ }
            // Then named lookup.
            Short mapped = NAME_TO_DOW.get(s.toUpperCase());
            if (mapped != null) { this.dayOfWeek = mapped; return; }
            // Finally try java.time.DayOfWeek (handles full case-insensitive names).
            try {
                DayOfWeek d = DayOfWeek.valueOf(s.toUpperCase());
                this.dayOfWeek = (short) d.getValue();
                return;
            } catch (IllegalArgumentException ignored) { /* fall through */ }
            throw new IllegalArgumentException("Unknown dayOfWeek: \"" + s + "\". Use 1-7 or a day name like MONDAY.");
        }
        throw new IllegalArgumentException("Unsupported dayOfWeek type: " + value.getClass().getSimpleName());
    }
}
