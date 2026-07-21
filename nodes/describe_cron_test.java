package nodes;

import axiom.AxiomContext;
import gen.Messages.CronDescriptionResult;
import gen.Messages.CronDialect;
import gen.Messages.DescribeCronInput;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class DescribeCronTest {

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

    // Independent oracle: "0 9 * * 1-5" unambiguously means "09:00, Monday
    // through Friday" — any correct English rendering must name the hour 09
    // and both weekday boundary names. We assert those semantic anchors
    // rather than a brittle full-string match against the underlying
    // formatting library's exact phrasing.
    @Test
    public void testEnglishDescriptionNamesHourAndWeekdayRange() {
        AxiomContext ax = new TestContext();
        DescribeCronInput input = DescribeCronInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setLocale("en").build();
        CronDescriptionResult result = DescribeCron.describeCron(ax, input);
        assertEquals("", result.getError().getCode());
        String d = result.getDescription().toLowerCase();
        assertTrue(d.contains("09:00"), "expected the hour 09:00 in: " + d);
        assertTrue(d.contains("monday"), "expected 'monday' in: " + d);
        assertTrue(d.contains("friday"), "expected 'friday' in: " + d);
    }

    // Empty locale defaults to English (verified against the same node with
    // locale unset producing an English sentence, not an error).
    @Test
    public void testEmptyLocaleDefaultsToEnglish() {
        AxiomContext ax = new TestContext();
        DescribeCronInput input = DescribeCronInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).build();
        CronDescriptionResult result = DescribeCron.describeCron(ax, input);
        assertEquals("", result.getError().getCode());
        assertTrue(result.getDescription().toLowerCase().contains("monday"));
    }

    // French rendering of the same expression must still name the hour, and
    // must use the French weekday names, not the English ones — verifies the
    // locale parameter actually changes the output, not just accepted.
    @Test
    public void testFrenchLocaleUsesFrenchWeekdayNames() {
        AxiomContext ax = new TestContext();
        DescribeCronInput input = DescribeCronInput.newBuilder().setCron("0 9 * * 1-5").setDialect(CronDialect.UNIX).setLocale("fr").build();
        CronDescriptionResult result = DescribeCron.describeCron(ax, input);
        assertEquals("", result.getError().getCode());
        String d = result.getDescription().toLowerCase();
        assertTrue(d.contains("09:00"));
        assertTrue(d.contains("lundi"), "expected French 'lundi' in: " + d);
        assertTrue(d.contains("vendredi"), "expected French 'vendredi' in: " + d);
        assertFalse(d.contains("monday"));
    }

    @Test
    public void testInvalidCronReturnsStructuredErrorNotCrash() {
        AxiomContext ax = new TestContext();
        DescribeCronInput input = DescribeCronInput.newBuilder().setCron("not a cron").setDialect(CronDialect.UNIX).build();
        CronDescriptionResult result = DescribeCron.describeCron(ax, input);
        assertEquals("INVALID_CRON", result.getError().getCode());
        assertEquals("", result.getDescription());
    }
}
