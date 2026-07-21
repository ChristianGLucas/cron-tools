package nodes;

import axiom.AxiomContext;
import com.cronutils.model.Cron;
import com.cronutils.model.time.ExecutionTime;
import gen.Messages.CronIsDueResult;
import gen.Messages.IsDueInput;

import java.time.ZonedDateTime;

public class IsDue {

    /**
     * Test whether a caller-supplied `check_time` exactly matches an
     * occurrence of a cron expression, e.g. "0 9 * * 1-5" (UNIX) at
     * "2026-07-20T09:00:00Z" (a Monday) -&gt; is_due=true; at
     * "2026-07-20T09:01:00Z" -&gt; is_due=false. `check_time` is always
     * caller-supplied — this node never reads the wall clock, so the same
     * input always produces the same output.
     */
    public static CronIsDueResult isDue(AxiomContext ax, IsDueInput input) {
        Cron cron;
        try {
            cron = CronSupport.parse(input.getCron(), input.getDialect());
        } catch (IllegalArgumentException e) {
            return CronIsDueResult.newBuilder()
                    .setError(CronSupport.error("INVALID_CRON", String.valueOf(e.getMessage())))
                    .build();
        }
        ZonedDateTime checkTime;
        try {
            checkTime = CronSupport.parseInstant(input.getCheckTime());
        } catch (RuntimeException e) {
            return CronIsDueResult.newBuilder()
                    .setError(CronSupport.error("INVALID_TIMESTAMP", String.valueOf(e.getMessage())))
                    .build();
        }
        boolean isDue = ExecutionTime.forCron(cron).isMatch(checkTime);
        return CronIsDueResult.newBuilder().setIsDue(isDue).build();
    }
}
