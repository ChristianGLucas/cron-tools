package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDialect;
import gen.Messages.CronMigrateResult;
import gen.Messages.MigrateCronInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MigrateCronTest {

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

    // Independent oracle for UNIX -> QUARTZ: Quartz numbers day-of-week
    // 1=Sunday..7=Saturday (documented Quartz convention) versus UNIX's
    // 0=Sunday..6=Saturday, so Monday-Friday (UNIX "1-5") becomes "2-6".
    // Quartz also requires seconds (prepended "0") and exactly one of
    // day-of-month/day-of-week to be "?" — since day-of-week is explicit
    // here, day-of-month becomes "?", and Quartz's optional trailing year
    // field is emitted as "*".
    @Test
    public void testUnixToQuartzRenumbersWeekdaysAndAddsSecondsAndQuestionMark() {
        AxiomContext ax = new TestContext();
        MigrateCronInput input = MigrateCronInput.newBuilder().setCron("0 9 * * 1-5").setFromDialect(CronDialect.UNIX).setToDialect(CronDialect.QUARTZ).build();
        CronMigrateResult result = MigrateCron.migrateCron(ax, input);
        assertEquals("", result.getError().getCode());
        assertEquals("0 0 9 ? * 2-6 *", result.getMigratedCron());
    }

    // The exact round-trip inverse of the above: Quartz's "2-6" (Mon-Fri) and
    // "?" day-of-month become UNIX's "1-5" and "*", seconds are dropped
    // (UNIX has no seconds field).
    @Test
    public void testQuartzToUnixRoundTrips() {
        AxiomContext ax = new TestContext();
        MigrateCronInput input = MigrateCronInput.newBuilder().setCron("0 0 9 ? * 2-6").setFromDialect(CronDialect.QUARTZ).setToDialect(CronDialect.UNIX).build();
        CronMigrateResult result = MigrateCron.migrateCron(ax, input);
        assertEquals("", result.getError().getCode());
        assertEquals("0 9 * * 1-5", result.getMigratedCron());
    }

    // SPRING53 uses the same 0=Sunday..6=Saturday numbering as UNIX (unlike
    // Quartz), so the day-of-week field is untouched — only seconds and the
    // day-of-month "?" disambiguation are added.
    @Test
    public void testUnixToSpring53KeepsWeekdayNumbering() {
        AxiomContext ax = new TestContext();
        MigrateCronInput input = MigrateCronInput.newBuilder().setCron("0 9 * * 1-5").setFromDialect(CronDialect.UNIX).setToDialect(CronDialect.SPRING53).build();
        CronMigrateResult result = MigrateCron.migrateCron(ax, input);
        assertEquals("", result.getError().getCode());
        assertEquals("0 0 9 ? * 1-5", result.getMigratedCron());
    }

    // CRON4J has no "?" support and the same day-of-week numbering as UNIX,
    // so a UNIX cron migrates to CRON4J unchanged.
    @Test
    public void testUnixToCron4jIsUnchanged() {
        AxiomContext ax = new TestContext();
        MigrateCronInput input = MigrateCronInput.newBuilder().setCron("0 9 * * 1-5").setFromDialect(CronDialect.UNIX).setToDialect(CronDialect.CRON4J).build();
        CronMigrateResult result = MigrateCron.migrateCron(ax, input);
        assertEquals("", result.getError().getCode());
        assertEquals("0 9 * * 1-5", result.getMigratedCron());
    }

    @Test
    public void testInvalidSourceCronReturnsStructuredError() {
        AxiomContext ax = new TestContext();
        MigrateCronInput input = MigrateCronInput.newBuilder().setCron("not a cron").setFromDialect(CronDialect.UNIX).setToDialect(CronDialect.QUARTZ).build();
        CronMigrateResult result = MigrateCron.migrateCron(ax, input);
        assertEquals("INVALID_CRON", result.getError().getCode());
        assertEquals("", result.getMigratedCron());
    }
}
