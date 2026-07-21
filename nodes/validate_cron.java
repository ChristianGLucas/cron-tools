package nodes;

import axiom.AxiomContext;
import com.cronutils.model.Cron;
import gen.Messages.CronValidationResult;
import gen.Messages.ValidateCronInput;

public class ValidateCron {

    /**
     * Validate a cron expression against a dialect (UNIX/QUARTZ/SPRING53/CRON4J),
     * e.g. "0 9 * * 1-5" (UNIX) -&gt; valid=true, normalized="0 9 * * 1-5";
     * "0 9 * * 8" -&gt; valid=false with an INVALID_CRON error naming the
     * out-of-range day-of-week. Never throws on malformed input.
     */
    public static CronValidationResult validateCron(AxiomContext ax, ValidateCronInput input) {
        try {
            Cron cron = CronSupport.parse(input.getCron(), input.getDialect());
            return CronValidationResult.newBuilder()
                    .setValid(true)
                    .setNormalized(cron.asString())
                    .build();
        } catch (IllegalArgumentException e) {
            return CronValidationResult.newBuilder()
                    .setValid(false)
                    .setError(CronSupport.error("INVALID_CRON", String.valueOf(e.getMessage())))
                    .build();
        }
    }
}
