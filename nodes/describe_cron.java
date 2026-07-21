package nodes;

import axiom.AxiomContext;
import com.cronutils.descriptor.CronDescriptor;
import com.cronutils.model.Cron;
import gen.Messages.CronDescriptionResult;
import gen.Messages.DescribeCronInput;

public class DescribeCron {

    /**
     * Describe a cron expression in plain English, e.g. "0 9 * * 1-5" (UNIX)
     * -&gt; "at 09:00 AM, Monday through Friday". Supports cron-utils'
     * bundled locales (en, de, el, es, fr, id, it, ja, ko, nl, pl, pt, ro,
     * ru, sw, tr, zh); an unrecognized or empty locale tag falls back to
     * "en". Returns a structured INVALID_CRON error, not a crash, when the
     * expression does not parse for the given dialect.
     */
    public static CronDescriptionResult describeCron(AxiomContext ax, DescribeCronInput input) {
        try {
            Cron cron = CronSupport.parse(input.getCron(), input.getDialect());
            CronDescriptor descriptor = CronDescriptor.instance(CronSupport.localeFor(input.getLocale()));
            String description = descriptor.describe(cron);
            return CronDescriptionResult.newBuilder().setDescription(description).build();
        } catch (IllegalArgumentException e) {
            return CronDescriptionResult.newBuilder()
                    .setError(CronSupport.error("INVALID_CRON", String.valueOf(e.getMessage())))
                    .build();
        }
    }
}
