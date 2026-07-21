package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDialect;
import gen.Messages.CronValidationResult;
import gen.Messages.ValidateCronInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ValidateCronTest {

    static final class TestContext implements AxiomContext {
        public Logger log() {
            return new Logger() {
                public void debug(String m, Map<String, String> a) {}
                public void info(String m, Map<String, String> a)  {}
                public void warn(String m, Map<String, String> a)  {}
                public void error(String m, Map<String, String> a) {}
            };
        }
        public Secrets secrets() { return name -> Optional.empty(); }
        public String executionId() { return "test-execution-id"; }
        public String flowId() { return "test-flow-id"; }
        public String tenantId() { return "test-tenant-id"; }
        public Reflection reflection() {
            return () -> new FlowReflection() {
                public List<ReflectionNode> nodes() { return List.of(); }
                public List<ReflectionEdge> edges() { return List.of(); }
                public List<ReflectionEdge> loopEdges() { return List.of(); }
                public FlowPosition position() { return new FlowPosition(0, 0, Map.of(), List.of()); }
                public String graphId() { return ""; }
            };
        }
        public Mutation mutation() {
            return () -> new FlowMutation() {
                public int addNode(String pkg, String ver, CanvasPosition pos) { return 0; }
                public void addEdge(int src, int dst, EdgeCondition cond) {}
            };
        }
    }

    // Independent oracle: "0 9 * * 1-5" is the textbook "9am on weekdays"
    // expression — every crontab reference agrees this is syntactically
    // valid UNIX crontab and re-prints unchanged.
    @Test
    public void testValidWeekdayMorning() {
        AxiomContext ax = new TestContext();
        ValidateCronInput input = ValidateCronInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).build();
        CronValidationResult result = ValidateCron.validateCron(ax, input);
        assertTrue(result.getValid());
        assertEquals("0 9 * * 1-5", result.getNormalized());
        assertEquals("", result.getError().getCode());
    }

    // UNIX day-of-week's valid range is 0-7 (0 and 7 both mean Sunday); 8 is
    // out of range regardless of implementation — a hand-known fact about
    // crontab syntax, not something derived from cron-utils itself.
    @Test
    public void testInvalidDayOfWeekOutOfRange() {
        AxiomContext ax = new TestContext();
        ValidateCronInput input = ValidateCronInput.newBuilder().setCron("0 9 * * 8").setDialect(CronDialect.UNIX).build();
        CronValidationResult result = ValidateCron.validateCron(ax, input);
        assertFalse(result.getValid());
        assertEquals("", result.getNormalized());
        assertEquals("INVALID_CRON", result.getError().getCode());
        assertTrue(result.getError().getMessage().contains("8"));
    }

    // UNIX crontab always needs exactly 5 fields; this has 4.
    @Test
    public void testInvalidFieldCount() {
        AxiomContext ax = new TestContext();
        ValidateCronInput input = ValidateCronInput.newBuilder().setCron("0 9 * *").setDialect(CronDialect.UNIX).build();
        CronValidationResult result = ValidateCron.validateCron(ax, input);
        assertFalse(result.getValid());
        assertEquals("INVALID_CRON", result.getError().getCode());
    }

    @Test
    public void testEmptyCronIsRejectedNotCrashed() {
        AxiomContext ax = new TestContext();
        ValidateCronInput input = ValidateCronInput.newBuilder().setCron("").setDialect(CronDialect.UNIX).build();
        CronValidationResult result = ValidateCron.validateCron(ax, input);
        assertFalse(result.getValid());
        assertEquals("INVALID_CRON", result.getError().getCode());
    }

    // Independent oracle for the day-of-week renumbering: Quartz numbers
    // 1=Sunday..7=Saturday (per the Quartz Job Scheduler's own documented
    // convention), so Monday-Friday is 2-6 — not the UNIX 1-5.
    @Test
    public void testValidQuartzNormalizesNamedWeekdays() {
        AxiomContext ax = new TestContext();
        ValidateCronInput input = ValidateCronInput.newBuilder().setCron("0 0 9 ? * MON-FRI").setDialect(CronDialect.QUARTZ).build();
        CronValidationResult result = ValidateCron.validateCron(ax, input);
        assertTrue(result.getValid());
        assertEquals("0 0 9 ? * 2-6", result.getNormalized());
    }
}
