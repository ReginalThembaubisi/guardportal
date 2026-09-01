package com.propertysecurity.platform.shift;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

/**
 * Closes shifts that are still open past their rostered end plus the grace window.
 *
 * Design invariants (per product spec):
 *   - Rule 1: never modifies a shift with clock_out_at already set.
 *   - Rule 2: sets clock_out_at to the rostered end time, not the job's run time.
 *   - Rule 3: every auto-closed shift carries ROSTER_AUTO_CLOSED — no unlabelled closes.
 *   - Rule 4: no roster row with a non-null end_time → no auto-close.
 *   - Rule 5: audit row records null as the actor (system, not the guard).
 *   - Night-shift rule: when end_time <= start_time, the effective end date is shift_date + 1.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShiftAutoCloseJob {

    private final ShiftService shiftService;

    @Value("${app.shift.auto-close-grace-minutes:90}")
    private int graceMinutes;

    @Value("${app.shift.auto-close-lookback-days:7}")
    private int lookbackDays;

    /**
     * Runs every 15 minutes. Worst-case lag from rostered end to supervisor
     * seeing the row as auto-closed: grace window + 15 min = 105 min.
     * @Scheduled requires @EnableScheduling — already on the main application class.
     */
    @Scheduled(fixedRate = 900_000)
    public void run() {
        // Default 7-day lookback: shifts open longer than that are a data-quality
        // issue beyond the auto-close window (override via auto-close-lookback-days).
        LocalDateTime lookback = LocalDateTime.now().minusDays(lookbackDays);
        List<Shift> openShifts = shiftService.findOpenShiftsSince(lookback);

        int closed = 0;
        for (Shift shift : openShifts) {
            try {
                if (shiftService.tryAutoClose(shift, graceMinutes)) {
                    closed++;
                }
            } catch (Exception e) {
                log.error("Auto-close failed for shift {} (guard {}): {}", shift.getId(), shift.getGuard().getId(), e.getMessage(), e);
            }
        }
        if (closed > 0) {
            log.info("Auto-close job closed {} shift(s)", closed);
        }
    }
}
