package nodes;

import com.cronutils.Function;
import com.cronutils.mapper.CronMapper;
import com.cronutils.model.Cron;
import com.cronutils.model.CronType;
import com.cronutils.model.SingleCron;
import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.model.field.CronField;
import com.cronutils.model.field.CronFieldName;
import com.cronutils.model.field.expression.Always;
import com.cronutils.model.field.expression.And;
import com.cronutils.model.field.expression.Between;
import com.cronutils.model.field.expression.Every;
import com.cronutils.model.field.expression.FieldExpression;
import com.cronutils.model.field.expression.On;
import com.cronutils.model.field.expression.QuestionMark;
import com.cronutils.model.field.value.SpecialChar;
import com.cronutils.model.time.ExecutionTime;
import com.cronutils.parser.CronParser;
import gen.Messages.CronDialect;
import gen.Messages.CronError;
import gen.Messages.CronFieldInfo;
import gen.Messages.CronRunTimesInput;
import gen.Messages.CronRunTimesResult;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Shared, non-node helper logic for the cron-tools package: dialect
 * resolution, parsing with a consistent error contract, RFC 3339 instant
 * conversion, field-breakdown classification, and generic cross-dialect
 * migration. Every node in this package calls into here rather than
 * duplicating cron-utils setup.
 */
final class CronSupport {

    private CronSupport() {
    }

    // cron-utils' own docs note CronDefinition/CronParser construction walks
    // every field definition; build each dialect's pair once and reuse it
    // across invocations rather than per-request (CronParser is documented
    // thread-safe, so sharing across concurrent static-handler calls is safe).
    private static final Map<CronDialect, CronDefinition> DEFINITIONS = new EnumMap<>(CronDialect.class);
    private static final Map<CronDialect, CronParser> PARSERS = new EnumMap<>(CronDialect.class);

    static {
        for (CronDialect d : new CronDialect[] { CronDialect.UNIX, CronDialect.QUARTZ, CronDialect.SPRING53, CronDialect.CRON4J }) {
            CronDefinition def = CronDefinitionBuilder.instanceDefinitionFor(toCronType(d));
            DEFINITIONS.put(d, def);
            PARSERS.put(d, new CronParser(def));
        }
    }

    static CronType toCronType(CronDialect dialect) {
        switch (dialect) {
            case QUARTZ:
                return CronType.QUARTZ;
            case SPRING53:
                return CronType.SPRING53;
            case CRON4J:
                return CronType.CRON4J;
            case UNIX:
            default:
                return CronType.UNIX;
        }
    }

    static CronDefinition definitionFor(CronDialect dialect) {
        return DEFINITIONS.get(dialect);
    }

    /**
     * Parse and validate a cron string for a dialect. Throws
     * IllegalArgumentException (cron-utils' own contract) with a descriptive
     * message on any syntax/range problem — callers convert that into a
     * structured CronError rather than letting it propagate.
     */
    static Cron parse(String cronExpression, CronDialect dialect) {
        if (cronExpression == null || cronExpression.trim().isEmpty()) {
            throw new IllegalArgumentException("cron expression must not be empty");
        }
        return PARSERS.get(dialect).parse(cronExpression);
    }

    /** Parse an RFC 3339 instant. Throws DateTimeParseException on malformed input. */
    static ZonedDateTime parseInstant(String rfc3339) {
        if (rfc3339 == null || rfc3339.trim().isEmpty()) {
            throw new IllegalArgumentException("timestamp must not be empty");
        }
        return ZonedDateTime.parse(rfc3339);
    }

    /** Re-print an instant as RFC 3339, preserving the offset it was computed in. */
    static String formatInstant(ZonedDateTime z) {
        return z.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    static CronError error(String code, String message) {
        return CronError.newBuilder().setCode(code).setMessage(message).build();
    }

    static Locale localeFor(String tag) {
        if (tag == null || tag.trim().isEmpty()) {
            return Locale.ENGLISH;
        }
        return Locale.forLanguageTag(tag.trim());
    }

    /** Snake_case field name for the registry-facing CronFieldInfo.name. */
    private static String fieldName(CronFieldName name) {
        switch (name) {
            case SECOND:
                return "second";
            case MINUTE:
                return "minute";
            case HOUR:
                return "hour";
            case DAY_OF_MONTH:
                return "day_of_month";
            case MONTH:
                return "month";
            case DAY_OF_WEEK:
                return "day_of_week";
            case YEAR:
                return "year";
            case DAY_OF_YEAR:
                return "day_of_year";
            default:
                return name.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Classify a field's expression shape for CronFieldInfo.kind. */
    private static String classify(FieldExpression expr) {
        if (expr instanceof Always) {
            return "always";
        }
        if (expr instanceof Every) {
            return "every";
        }
        if (expr instanceof Between) {
            return "between";
        }
        if (expr instanceof On) {
            return "on";
        }
        if (expr instanceof And) {
            return "and";
        }
        if (expr instanceof QuestionMark) {
            return "question_mark";
        }
        return expr.getClass().getSimpleName().toLowerCase(Locale.ROOT);
    }

    /** Break a parsed Cron down into its fields, in cron-utils' own field order. */
    static List<CronFieldInfo> fieldBreakdown(Cron cron) {
        List<CronFieldInfo> out = new ArrayList<>();
        Map<CronFieldName, CronField> fields = cron.retrieveFieldsAsMap();
        for (CronFieldName name : CronFieldName.values()) {
            CronField field = fields.get(name);
            if (field == null) {
                continue;
            }
            out.add(CronFieldInfo.newBuilder()
                    .setName(fieldName(name))
                    .setRawExpression(field.getExpression().asString())
                    .setKind(classify(field.getExpression()))
                    .build());
        }
        return out;
    }

    /**
     * Translate a cron from one dialect to another. Generalizes cron-utils'
     * own CronMapper.fromXToY() factories (which only cover a handful of
     * named pairs) to every pair among our four supported dialects, using
     * the same day-of-month/day-of-week "?" disambiguation rule those
     * factories apply — driven off the TARGET field's own constraints
     * (supportsQuestionMark()) rather than a hardcoded dialect list, so it
     * is correct for SPRING53 (which the built-in factories don't cover) too.
     */
    static Cron migrate(Cron cron, CronDialect toDialect) {
        CronDefinition toDef = definitionFor(toDialect);
        CronMapper mapper = new CronMapper(cron.getCronDefinition(), toDef, disambiguationRule());
        return mapper.map(cron);
    }

    // Default count when the caller leaves `count` at its proto3 zero value,
    // and the ceiling above which we reject rather than silently truncate —
    // bounds the cost of the loop below regardless of how cron-utils' own
    // internal search behaves.
    static final int DEFAULT_COUNT = 1;
    static final int MAX_COUNT = 500;

    /**
     * Shared implementation for NextRunTimes and PreviousRunTimes: parse,
     * bound `count`, then walk ExecutionTime.nextExecution/lastExecution one
     * step at a time from `from_time`. Each step is strictly forward/backward
     * of the previous (cron-utils' own contract — see nextExecution's
     * "if nextMatch.equals(date), advance and retry" handling), so this loop
     * cannot stall on repeats; it can only end early (truncated=true) when
     * cron-utils itself gives up because no further match exists within its
     * own bounded search (e.g. an unreachable field combination like day 31
     * of February, or a genuinely finite YEAR field that has been exhausted).
     */
    static CronRunTimesResult computeRunTimes(CronRunTimesInput input, boolean forward) {
        int requested = input.getCount();
        int count = requested == 0 ? DEFAULT_COUNT : requested;
        if (count < 0 || count > MAX_COUNT) {
            return CronRunTimesResult.newBuilder()
                    .setError(error("INVALID_ARGUMENT",
                            "count must be between 1 and " + MAX_COUNT + " (0 defaults to 1); got " + requested))
                    .build();
        }

        Cron cron;
        try {
            cron = parse(input.getCron(), input.getDialect());
        } catch (IllegalArgumentException e) {
            return CronRunTimesResult.newBuilder().setError(error("INVALID_CRON", e.getMessage())).build();
        }

        ZonedDateTime cursor;
        try {
            cursor = parseInstant(input.getFromTime());
        } catch (RuntimeException e) {
            return CronRunTimesResult.newBuilder().setError(error("INVALID_TIMESTAMP", e.getMessage())).build();
        }

        ExecutionTime executionTime = ExecutionTime.forCron(cron);
        List<String> results = new ArrayList<>(count);
        boolean truncated = false;
        for (int i = 0; i < count; i++) {
            Optional<ZonedDateTime> step = forward ? executionTime.nextExecution(cursor) : executionTime.lastExecution(cursor);
            if (!step.isPresent()) {
                truncated = true;
                break;
            }
            cursor = step.get();
            results.add(formatInstant(cursor));
        }

        return CronRunTimesResult.newBuilder()
                .addAllRunTimes(results)
                .setTruncated(truncated)
                .build();
    }

    private static Function<Cron, Cron> disambiguationRule() {
        return mapped -> {
            CronField dow = mapped.retrieve(CronFieldName.DAY_OF_WEEK);
            CronField dom = mapped.retrieve(CronFieldName.DAY_OF_MONTH);
            if (dow == null || dom == null) {
                return mapped;
            }
            if (dow.getExpression() instanceof QuestionMark || dom.getExpression() instanceof QuestionMark) {
                return mapped;
            }
            boolean dowSupportsQ = dow.getConstraints().getSpecialChars().contains(SpecialChar.QUESTION_MARK);
            boolean domSupportsQ = dom.getConstraints().getSpecialChars().contains(SpecialChar.QUESTION_MARK);
            if (!dowSupportsQ && !domSupportsQ) {
                return mapped;
            }
            Map<CronFieldName, CronField> fields = new EnumMap<>(mapped.retrieveFieldsAsMap());
            if (dow.getExpression() instanceof Always && dowSupportsQ) {
                fields.put(CronFieldName.DAY_OF_WEEK, new CronField(CronFieldName.DAY_OF_WEEK, FieldExpression.questionMark(), dow.getConstraints()));
            } else if (dom.getExpression() instanceof Always && domSupportsQ) {
                fields.put(CronFieldName.DAY_OF_MONTH, new CronField(CronFieldName.DAY_OF_MONTH, FieldExpression.questionMark(), dom.getConstraints()));
            }
            return new SingleCron(mapped.getCronDefinition(), new ArrayList<>(fields.values()));
        };
    }
}
