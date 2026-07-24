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

public class PreviousRunTimesTest {

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

    // Independent oracle (same `date`-verified calendar as NextRunTimesTest):
    // 2026-07-22 is a Wednesday. Walking backward from its midnight, the most
    // recent "0 9 * * 1-5" (weekdays at 09:00) occurrences are Tuesday
    // 2026-07-21 09:00 then Monday 2026-07-20 09:00, most-recent-first —
    // Wednesday's own 09:00 has not happened yet at Wednesday 00:00.
    @Test
    public void testPreviousTwoWeekdayMorningsFromWednesdayMidnight() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-22T00:00:00Z")
                .setCount(2)
                .build();
        CronRunTimesResult result = PreviousRunTimes.previousRunTimes(ax, input);
        assertEquals("", result.getError().getCode());
        assertFalse(result.getTruncated());
        assertEquals(
                List.of("2026-07-21T09:00:00Z", "2026-07-20T09:00:00Z"),
                result.getRunTimesList());
    }

    // "Strictly before" contract: asking from the exact instant of a match
    // must not return that same instant back.
    @Test
    public void testFromTimeIsExclusive() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("0 9 * * 1-5")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T09:00:00Z") // Monday 09:00 itself
                .setCount(1)
                .build();
        CronRunTimesResult result = PreviousRunTimes.previousRunTimes(ax, input);
        // The Friday before Monday 2026-07-20 is 2026-07-17.
        assertEquals(List.of("2026-07-17T09:00:00Z"), result.getRunTimesList());
    }

    @Test
    public void testNegativeCountIsRejected() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("* * * * *")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .setCount(-1)
                .build();
        CronRunTimesResult result = PreviousRunTimes.previousRunTimes(ax, input);
        assertEquals("INVALID_ARGUMENT", result.getError().getCode());
    }

    @Test
    public void testInvalidCronReturnsStructuredError() {
        AxiomContext ax = new TestContext();
        CronRunTimesInput input = CronRunTimesInput.newBuilder()
                .setCron("nope")
                .setDialect(CronDialect.UNIX)
                .setFromTime("2026-07-20T00:00:00Z")
                .setCount(1)
                .build();
        CronRunTimesResult result = PreviousRunTimes.previousRunTimes(ax, input);
        assertEquals("INVALID_CRON", result.getError().getCode());
    }
}
