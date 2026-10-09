package com.mentor.app.service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

// subconjunto de RRULE: FREQ=DAILY o FREQ=WEEKLY[;BYDAY=MO,TU,...]
public final class Recurrence {

    private static final Map<String, DayOfWeek> CODES = Map.of(
            "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY,
            "SU", DayOfWeek.SUNDAY);

    private final boolean daily;
    private final Set<DayOfWeek> days;

    private Recurrence(boolean daily, Set<DayOfWeek> days) {
        this.daily = daily;
        this.days = days;
    }

    // lanza IllegalArgumentException si la regla no es válida o no está soportada
    public static Recurrence parse(String rule, LocalDate start) {
        Map<String, String> parts = new HashMap<>();
        for (String piece : rule.trim().toUpperCase().split(";")) {
            String[] kv = piece.split("=", 2);
            if (kv.length != 2 || kv[0].isBlank() || kv[1].isBlank()) {
                throw new IllegalArgumentException("La repetición no tiene un formato válido");
            }
            parts.put(kv[0], kv[1]);
        }
        if (!Set.of("FREQ", "BYDAY").containsAll(parts.keySet())) {
            throw new IllegalArgumentException("Solo se admiten FREQ y BYDAY en la repetición");
        }

        String freq = parts.get("FREQ");
        if ("DAILY".equals(freq) && !parts.containsKey("BYDAY")) {
            return new Recurrence(true, EnumSet.noneOf(DayOfWeek.class));
        }
        if ("WEEKLY".equals(freq)) {
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            if (parts.containsKey("BYDAY")) {
                for (String code : parts.get("BYDAY").split(",")) {
                    DayOfWeek day = CODES.get(code.trim());
                    if (day == null) {
                        throw new IllegalArgumentException("Día no válido en la repetición: " + code);
                    }
                    days.add(day);
                }
            } else {
                days.add(start.getDayOfWeek());
            }
            return new Recurrence(false, days);
        }
        throw new IllegalArgumentException("Solo se admite FREQ=DAILY o FREQ=WEEKLY");
    }

    public boolean occursOn(LocalDate date) {
        return daily || days.contains(date.getDayOfWeek());
    }
}