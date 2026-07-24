package nodes;

import axiom.AxiomContext;
import gen.Messages.CronRunTimesInput;
import gen.Messages.CronRunTimesResult;

public class NextRunTimes {

    /**
     * Compute the next `count` run times of a cron expression strictly after
     * a caller-supplied `from_time`, e.g. "0 9 * * 1-5" (UNIX) from
     * "2026-07-20T00:00:00Z" (a Monday) with count=3 -&gt;
     * ["2026-07-20T09:00:00Z","2026-07-21T09:00:00Z","2026-07-22T09:00:00Z"].
     * `from_time` is always caller-supplied — this node never reads the wall
     * clock. count must not be negative (INVALID_ARGUMENT otherwise); an
     * expression whose fields can never simultaneously match (e.g.
     * day-of-month=31 in February) returns truncated=true with whatever run
     * times were found before cron-utils' own bounded search gave up.
     */
    public static CronRunTimesResult nextRunTimes(AxiomContext ax, CronRunTimesInput input) {
        return CronSupport.computeRunTimes(input, true);
    }
}
