package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDialect;
import gen.Messages.CronFieldBreakdownResult;
import gen.Messages.CronFieldInfo;
import gen.Messages.FieldBreakdownInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class FieldBreakdownTest {

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

    // Independent oracle: reading "*/15 9-17 * * 1-5" field by field against
    // the crontab grammar itself — "*/15" is a step (every), "9-17" is a
    // range (between), the two "*" are wildcards (always), "1-5" is a range
    // (between) — gives exactly this classification without reference to
    // cron-utils' internal implementation.
    @Test
    public void testUnixFiveFieldBreakdown() {
        AxiomContext ax = new TestContext();
        FieldBreakdownInput input = FieldBreakdownInput.newBuilder().setCron("*/15 9-17 * * 1-5").setDialect(CronDialect.UNIX).build();
        CronFieldBreakdownResult result = FieldBreakdown.fieldBreakdown(ax, input);
        assertEquals("", result.getError().getCode());
        List<CronFieldInfo> fields = result.getFieldsList();
        assertEquals(5, fields.size());

        assertEquals("minute", fields.get(0).getName());
        assertEquals("*/15", fields.get(0).getRawExpression());
        assertEquals("every", fields.get(0).getKind());

        assertEquals("hour", fields.get(1).getName());
        assertEquals("9-17", fields.get(1).getRawExpression());
        assertEquals("between", fields.get(1).getKind());

        assertEquals("day_of_month", fields.get(2).getName());
        assertEquals("*", fields.get(2).getRawExpression());
        assertEquals("always", fields.get(2).getKind());

        assertEquals("month", fields.get(3).getName());
        assertEquals("*", fields.get(3).getRawExpression());
        assertEquals("always", fields.get(3).getKind());

        assertEquals("day_of_week", fields.get(4).getName());
        assertEquals("1-5", fields.get(4).getRawExpression());
        assertEquals("between", fields.get(4).getKind());
    }

    // Quartz has 6 fields (seconds included) and, since day-of-week is
    // explicit here, day-of-month must be "?" — the disambiguation quartz
    // itself requires, independent of anything this package computes.
    @Test
    public void testQuartzSixFieldBreakdownIncludesSecondsAndQuestionMark() {
        AxiomContext ax = new TestContext();
        FieldBreakdownInput input = FieldBreakdownInput.newBuilder().setCron("0 0 9 ? * MON-FRI").setDialect(CronDialect.QUARTZ).build();
        CronFieldBreakdownResult result = FieldBreakdown.fieldBreakdown(ax, input);
        assertEquals("", result.getError().getCode());
        List<CronFieldInfo> fields = result.getFieldsList();
        assertEquals(6, fields.size());

        assertEquals("second", fields.get(0).getName());
        assertEquals("on", fields.get(0).getKind());

        assertEquals("day_of_month", fields.get(3).getName());
        assertEquals("?", fields.get(3).getRawExpression());
        assertEquals("question_mark", fields.get(3).getKind());

        assertEquals("day_of_week", fields.get(5).getName());
        // MON-FRI renumbered into Quartz's 1=Sunday..7=Saturday scheme.
        assertEquals("2-6", fields.get(5).getRawExpression());
        assertEquals("between", fields.get(5).getKind());
    }

    @Test
    public void testInvalidCronReturnsEmptyFieldsAndError() {
        AxiomContext ax = new TestContext();
        FieldBreakdownInput input = FieldBreakdownInput.newBuilder().setCron("garbage").setDialect(CronDialect.UNIX).build();
        CronFieldBreakdownResult result = FieldBreakdown.fieldBreakdown(ax, input);
        assertEquals("INVALID_CRON", result.getError().getCode());
        assertEquals(0, result.getFieldsList().size());
    }
}
