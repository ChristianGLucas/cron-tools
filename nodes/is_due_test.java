package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDialect;
import gen.Messages.CronIsDueResult;
import gen.Messages.IsDueInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class IsDueTest {

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

    // Independent oracle: 2026-07-20T09:00:00Z is exactly Monday 09:00 (see
    // `date -d 2026-07-20 +%A` -> Monday), a direct match for "0 9 * * 1-5".
    @Test
    public void testExactMatchIsDue() {
        AxiomContext ax = new TestContext();
        IsDueInput input = IsDueInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setCheckTime("2026-07-20T09:00:00Z").build();
        CronIsDueResult result = IsDue.isDue(ax, input);
        assertEquals("", result.getError().getCode());
        assertTrue(result.getIsDue());
    }

    @Test
    public void testOneMinuteOffIsNotDue() {
        AxiomContext ax = new TestContext();
        IsDueInput input = IsDueInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setCheckTime("2026-07-20T09:01:00Z").build();
        CronIsDueResult result = IsDue.isDue(ax, input);
        assertEquals("", result.getError().getCode());
        assertFalse(result.getIsDue());
    }

    // 2026-07-25 is a Saturday (`date -d 2026-07-25 +%A` -> Saturday),
    // outside the 1-5 (Mon-Fri) day-of-week range even at the matching hour.
    @Test
    public void testWeekendIsNotDue() {
        AxiomContext ax = new TestContext();
        IsDueInput input = IsDueInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setCheckTime("2026-07-25T09:00:00Z").build();
        CronIsDueResult result = IsDue.isDue(ax, input);
        assertEquals("", result.getError().getCode());
        assertFalse(result.getIsDue());
    }

    @Test
    public void testInvalidCronReturnsStructuredError() {
        AxiomContext ax = new TestContext();
        IsDueInput input = IsDueInput.newBuilder().setCron("***").setDialect(CronDialect.UNIX).setCheckTime("2026-07-20T09:00:00Z").build();
        CronIsDueResult result = IsDue.isDue(ax, input);
        assertEquals("INVALID_CRON", result.getError().getCode());
    }

    @Test
    public void testMalformedCheckTimeReturnsStructuredError() {
        AxiomContext ax = new TestContext();
        IsDueInput input = IsDueInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setCheckTime("garbage").build();
        CronIsDueResult result = IsDue.isDue(ax, input);
        assertEquals("INVALID_TIMESTAMP", result.getError().getCode());
    }
}
