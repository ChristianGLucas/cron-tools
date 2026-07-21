package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDialect;
import gen.Messages.CronRunTimesInput;
import gen.Messages.CronRunTimesResult;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class NextRunTimesTest {

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

    // Independent oracle, computed with the system `date` command (not this
    // package, not cron-utils): 2026-07-20 is a Monday, so "0 9 * * 1-5"
    // (weekdays at 09:00) counting forward from 2026-07-20T00:00:00Z visits
    // Mon/Tue/Wed at 09:00 on the 20th/21st/22nd, in order.
    //   $ date -d 2026-07-20 +%A   -> Monday
    //   $ date -d 2026-07-21 +%A   -> Tuesday
    //   $ date -d 2026-07-22 +%A   -> Wednesday
    @Test
    public void testNextThreeWeekdayMorningsFromMondayMidnight() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .setCount(3)
                .build();
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals("", result.getError().getCode());
        assertFalse(result.getTruncated());
        assertEquals(
                List.of("2026-07-20T09:00:00Z", "2026-07-21T09:00:00Z", "2026-07-22T09:00:00Z"),
                result.getRunTimesList());
    }

    // "Strictly after" contract: asking from the exact instant of a match
    // must not return that same instant back.
    @Test
    public void testFromTimeIsExclusive() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T09:00:00Z")
                .setCount(1)
                .build();
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals(List.of("2026-07-21T09:00:00Z"), result.getRunTimesList());
    }

    @Test
    public void testCountZeroDefaultsToOne() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .build(); // count left at proto3 default 0
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals(1, result.getRunTimesList().size());
    }

    @Test
    public void testCountAboveCeilingIsRejected() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("* * * * *")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .setCount(501)
                .build();
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals("INVALID_ARGUMENT", result.getError().getCode());
        assertEquals(0, result.getRunTimesList().size());
    }

    @Test
    public void testMalformedFromTimeReturnsStructuredError() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("not-a-timestamp")
                .setCount(1)
                .build();
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals("INVALID_TIMESTAMP", result.getError().getCode());
    }

    // February never has a 31st day in the Gregorian calendar, for any year
    // — an independent, calendar-level fact. cron-utils bounds its own
    // search (100-year / 100000-iteration ceiling) rather than looping
    // forever, so this must return promptly with truncated=true and no
    // results, not hang and not error.
    @Test
    public void testUnreachableFieldComboTruncatesInsteadOfHanging() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 0 31 2 *")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .setCount(3)
                .build();
        CronRunTimesResult result = NextRunTimes.nextRunTimes(ax, input);
        assertEquals("", result.getError().getCode());
        assertTrue(result.getTruncated());
        assertEquals(0, result.getRunTimesList().size());
    }
}
