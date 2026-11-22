package com.amigoscode.sqltraps.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A query plan, parsed from the output of EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON). A plan is a tree
 * of steps: this class flattens it into a list and answers the questions you ask of a slow query:
 * which join did Postgres really use, how many rows did it read, how far off was its estimate,
 * did a sort spill to disk, where did the time go.
 */
public final class Plan {

    /**
     * One step of the plan.
     *
     * @param type            "Seq Scan", "Index Scan", "Hash Join", "Nested Loop", "Sort", "Limit", "Aggregate"...
     * @param joinType        for a join: "Inner", "Left", "Right", "Full", "Semi" or "Anti". Otherwise null
     * @param relation        the table the step reads, or null
     * @param index           the index the step uses, or null
     * @param subPlan         "SubPlan 1" when this step, or a step above it, is a subquery run once per row. Otherwise null
     * @param filter          the condition checked row by row after reading (Filter), or null
     * @param estimatedRows   rows the planner expected this step to pass on, each time it runs
     * @param actualRows      rows the step really passed on, each time it ran (an average when loops is above 1)
     * @param loops           how many times the step ran
     * @param removedByFilter rows the step read and then threw away, all loops together
     * @param selfMillis      time spent in this step itself, without the steps below it, all loops together
     * @param sortMethod      for a Sort: "quicksort", "top-N heapsort", "external merge". Otherwise null
     * @param sortOnDisk      for a Sort: true when it did not fit in memory and used temporary files
     * @param sortKilobytes   for a Sort: memory or disk space used
     * @param cutShort        true below a Limit: the step was stopped early, so it returned fewer rows than a full run
     */
    public record Node(String type, String joinType, String relation, String index, String subPlan, String filter,
                       long estimatedRows, long actualRows, long loops, long removedByFilter, double selfMillis,
                       String sortMethod, boolean sortOnDisk, long sortKilobytes, boolean cutShort) {

        /** True for the steps that read a table: every kind of scan except the bitmap's index half. */
        public boolean readsTable() {
            return relation != null && type.endsWith("Scan") && !type.equals("Bitmap Index Scan");
        }

        public boolean isJoin() {
            return joinType != null;
        }

        /** Rows the step passed on, all loops together. */
        public long rowsOut() {
            return actualRows * loops;
        }

        /** Rows the step read, kept or not, all loops together. */
        public long rowsRead() {
            return rowsOut() + removedByFilter;
        }

        /**
         * How far the planner's estimate was from what happened: 1 means spot on, 90000 means the
         * real number was 90,000 times bigger or smaller than expected.
         */
        public double estimateFactor() {
            double estimated = Math.max(1, estimatedRows);
            double actual = Math.max(1, actualRows);
            return Math.max(estimated / actual, actual / estimated);
        }

        @Override
        public String toString() {
            return type + (index == null ? "" : " using " + index) + (relation == null ? "" : " on " + relation)
                    + (subPlan == null ? "" : " (" + subPlan + ")");
        }
    }

    private static final JsonMapper JSON = new JsonMapper();

    private final List<Node> nodes = new ArrayList<>();
    private final double executionMillis;
    private final long pages;
    private final int rowWidth;

    private Plan(JsonNode root) {
        JsonNode top = root.get(0);
        JsonNode plan = top.path("Plan");
        this.executionMillis = top.path("Execution Time").asDouble(0);
        this.pages = plan.path("Shared Hit Blocks").asLong(0) + plan.path("Shared Read Blocks").asLong(0);
        this.rowWidth = plan.path("Plan Width").asInt(0);
        collect(plan, null, false);
    }

    public static Plan parse(String json) {
        return new Plan(JSON.readTree(json));
    }

    private void collect(JsonNode node, String enclosingSubPlan, boolean cutShort) {
        String subPlan = "SubPlan".equals(text(node, "Parent Relationship")) ? text(node, "Subplan Name") : enclosingSubPlan;
        long loops = node.path("Actual Loops").asLong(1);
        double childrenMillis = 0;
        JsonNode children = node.path("Plans");
        for (int i = 0; i < children.size(); i++) {
            JsonNode child = children.get(i);
            childrenMillis += child.path("Actual Total Time").asDouble(0) * child.path("Actual Loops").asLong(1);
        }
        double selfMillis = Math.max(0, node.path("Actual Total Time").asDouble(0) * loops - childrenMillis);
        nodes.add(new Node(
                text(node, "Node Type"),
                text(node, "Join Type"),
                text(node, "Relation Name"),
                text(node, "Index Name"),
                subPlan,
                text(node, "Filter"),
                Math.round(node.path("Plan Rows").asDouble(0)),
                Math.round(node.path("Actual Rows").asDouble(0)),
                loops,
                Math.round((node.path("Rows Removed by Filter").asDouble(0)
                        + node.path("Rows Removed by Join Filter").asDouble(0)
                        + node.path("Rows Removed by Index Recheck").asDouble(0)) * Math.max(1, loops)),
                selfMillis,
                text(node, "Sort Method"),
                "Disk".equals(text(node, "Sort Space Type")),
                node.path("Sort Space Used").asLong(0),
                cutShort));
        boolean limit = "Limit".equals(text(node, "Node Type"));
        for (int i = 0; i < children.size(); i++) {
            collect(children.get(i), subPlan, cutShort || limit);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asString();
    }

    /** Every step, the outermost first. */
    public List<Node> nodes() {
        return List.copyOf(nodes);
    }

    /** True if any step's type contains the text: has("Seq Scan"), has("Nested Loop"), has("Sort"). */
    public boolean has(String nodeType) {
        return nodes.stream().anyMatch(node -> node.type().contains(nodeType));
    }

    /** The join type of every join step: "Inner", "Left", "Right", "Semi", "Anti"... */
    public List<String> joinTypes() {
        return nodes.stream().filter(Node::isJoin).map(Node::joinType).toList();
    }

    /** The names of the subqueries that run once per row of the step above them: "SubPlan 1", "SubPlan 2". */
    public List<String> subPlans() {
        return nodes.stream().map(Node::subPlan).filter(name -> name != null).distinct().toList();
    }

    /** The names of the indexes the plan uses. */
    public List<String> indexes() {
        return nodes.stream().map(Node::index).filter(index -> index != null).distinct().toList();
    }

    /** The steps that read a table. */
    public List<Node> scans() {
        return nodes.stream().filter(Node::readsTable).toList();
    }

    /** The first step that reads this table. */
    public Optional<Node> scanOf(String table) {
        return scans().stream().filter(node -> node.relation().equals(table)).findFirst();
    }

    /** The Sort steps. */
    public List<Node> sorts() {
        return nodes.stream().filter(node -> node.sortMethod() != null).toList();
    }

    /** Rows the query returned. */
    public long rowsReturned() {
        return nodes.getFirst().rowsOut();
    }

    /** Rows read from tables, kept or not, all loops together. */
    public long rowsRead() {
        return scans().stream().mapToLong(Node::rowsRead).sum();
    }

    /**
     * The step whose row estimate was furthest from what happened. When several steps are equally
     * far off, the deepest one wins: that is where the mistake started. Steps below a Limit are
     * left out, because they were stopped early on purpose.
     */
    public Node worstEstimate() {
        Node worst = nodes.getFirst();
        for (Node node : nodes) {
            if (node.loops() > 0 && !node.cutShort() && node.estimateFactor() >= worst.estimateFactor()) {
                worst = node;
            }
        }
        return worst;
    }

    /** The step that took the most time itself. */
    public Node slowest() {
        return nodes.stream().max(Comparator.comparingDouble(Node::selfMillis)).orElse(nodes.getFirst());
    }

    /** The planner's estimate of the average size of one result row, in bytes. */
    public int rowWidth() {
        return rowWidth;
    }

    /** 8 kB pages the query touched, in tables and indexes together. */
    public long pages() {
        return pages;
    }

    public double executionMillis() {
        return executionMillis;
    }

    /** For example "Hash Right Join + Seq Scan on orders + Seq Scan on customers". */
    public String describe() {
        List<String> steps = nodes.stream()
                .filter(node -> node.isJoin() || node.relation() != null)
                .filter(node -> !node.type().equals("Bitmap Index Scan"))
                .map(Node::toString)
                .toList();
        return steps.isEmpty() ? nodes.getFirst().type() : String.join(" + ", steps);
    }

    @Override
    public String toString() {
        return describe();
    }
}
