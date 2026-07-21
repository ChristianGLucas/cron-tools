package nodes;

import axiom.AxiomContext;
import com.cronutils.model.Cron;
import gen.Messages.CronFieldBreakdownResult;
import gen.Messages.FieldBreakdownInput;

public class FieldBreakdown {

    /**
     * Decompose a cron expression into its individual fields, e.g.
     * "*&#47;15 9-17 * * 1-5" (UNIX) -&gt; minute{raw="*&#47;15", kind="every"},
     * hour{raw="9-17", kind="between"}, day_of_month{raw="*", kind="always"},
     * month{raw="*", kind="always"}, day_of_week{raw="1-5", kind="between"}.
     * Only the fields the dialect actually has are returned (e.g.
     * QUARTZ/SPRING53 include "second").
     */
    public static CronFieldBreakdownResult fieldBreakdown(AxiomContext ax, FieldBreakdownInput input) {
        try {
            Cron cron = CronSupport.parse(input.getCron(), input.getDialect());
            return CronFieldBreakdownResult.newBuilder()
                    .addAllFields(CronSupport.fieldBreakdown(cron))
                    .build();
        } catch (IllegalArgumentException e) {
            return CronFieldBreakdownResult.newBuilder()
                    .setError(CronSupport.error("INVALID_CRON", String.valueOf(e.getMessage())))
                    .build();
        }
    }
}
