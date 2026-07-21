package nodes;

import axiom.AxiomContext;
import com.cronutils.model.Cron;
import gen.Messages.CronMigrateResult;
import gen.Messages.MigrateCronInput;

public class MigrateCron {

    /**
     * Translate a cron expression from one dialect's field layout into
     * another's, e.g. "0 9 * * 1-5" (UNIX) -&gt; "0 0 9 ? * 2-6" (QUARTZ,
     * seconds prepended, day-of-week renumbered 1=Sunday, "?" inserted in
     * day-of-month since day-of-week is explicit). Handles day-of-week
     * renumbering between dialects and the day-of-month/day-of-week "?"
     * disambiguation QUARTZ and SPRING53 require. Returns INVALID_CRON if
     * `cron` does not parse in `from_dialect`, or if the migrated result is
     * not valid in `to_dialect`.
     */
    public static CronMigrateResult migrateCron(AxiomContext ax, MigrateCronInput input) {
        try {
            Cron source = CronSupport.parse(input.getCron(), input.getFromDialect());
            Cron migrated = CronSupport.migrate(source, input.getToDialect());
            return CronMigrateResult.newBuilder().setMigratedCron(migrated.asString()).build();
        } catch (IllegalArgumentException e) {
            return CronMigrateResult.newBuilder()
                    .setError(CronSupport.error("INVALID_CRON", String.valueOf(e.getMessage())))
                    .build();
        }
    }
}
