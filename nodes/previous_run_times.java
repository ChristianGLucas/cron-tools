package nodes;

import axiom.AxiomContext;
import gen.Messages.CronRunTimesInput;
import gen.Messages.CronRunTimesResult;

public class PreviousRunTimes {

    /**
     * Compute the previous `count` run times of a cron expression strictly
     * before a caller-supplied `from_time`, most-recent-first, e.g.
     * "0 9 * * 1-5" (UNIX) from "2026-07-22T00:00:00Z" (a Wednesday) with
     * count=2 -&gt; ["2026-07-21T09:00:00Z","2026-07-20T09:00:00Z"]. Same
     * input contract, bounds, and truncation behaviour as NextRunTimes,
     * walking backward instead of forward.
     */
    public static CronRunTimesResult previousRunTimes(AxiomContext ax, CronRunTimesInput input) {
        return CronSupport.computeRunTimes(input, false);
    }
}
