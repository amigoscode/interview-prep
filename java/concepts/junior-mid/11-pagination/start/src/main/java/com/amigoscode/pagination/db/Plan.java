package com.amigoscode.pagination.db;

import java.util.ArrayList;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A query plan, parsed from the output of EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON). A plan is a tree
 * of nodes: this class flattens it into a list, which is enough to answer "how many rows were read?".
 */
public final class Plan {

    /**
     * One step of the plan.
     *
     * @param type            "Seq Scan", "Index Scan", "Sort", "Incremental Sort", "Limit", "Aggregate"...
     * @param relation        the table the step reads, or null
     * @param index           the index the step uses, or null
     * @param backward        true when the index is read from its end to its start (ORDER BY ... DESC)
     * @param condition       the condition answered by the index (Index Cond), or null
     * @param filter          the condition checked row by row after reading (Filter), or null
     * @param rows            rows the step passed on
     * @param removedByFilter rows the step read and then threw away
     * @param heapFetches     for an Index Only Scan: how many times it still had to visit the table
     */
    public record Node(String type, String relation, String index, boolean backward, String condition, String filter,
                       long rows, long removedByFilter, long heapFetches) {

        /** True for the steps that read a table: every kind of scan except the bitmap's index half. */
        public boolean readsTable() {
            return relation != null && type.endsWith("Scan") && !type.equals("Bitmap Index Scan");
        }

        @Override
        public String toString() {
            return type + (backward ? " Backward" : "") + (index == null ? "" : " using " + index) + (relation == null ? "" : " on " + relation);
        }
    }

    private static final JsonMapper JSON = new JsonMapper();

    private final List<Node> nodes = new ArrayList<>();
    private final double executionMillis;
    private final long pages;

    private Plan(JsonNode root) {
        JsonNode top = root.get(0);
        JsonNode plan = top.path("Plan");
        this.executionMillis = top.path("Execution Time").asDouble(0);
        this.pages = plan.path("Shared Hit Blocks").asLong(0) + plan.path("Shared Read Blocks").asLong(0);
        collect(plan);
    }

    public static Plan parse(String json) {
        return new Plan(JSON.readTree(json));
    }

    private void collect(JsonNode node) {
        long loops = Math.max(1, node.path("Actual Loops").asLong(1));
        nodes.add(new Node(
                text(node, "Node Type"),
                text(node, "Relation Name"),
                text(node, "Index Name"),
                "Backward".equals(text(node, "Scan Direction")),
                text(node, "Index Cond"),
                text(node, "Filter"),
                Math.round(node.path("Actual Rows").asDouble(0) * loops),
                Math.round((node.path("Rows Removed by Filter").asDouble(0)
                        + node.path("Rows Removed by Index Recheck").asDouble(0)) * loops),
                Math.round(node.path("Heap Fetches").asDouble(0))));
        JsonNode children = node.path("Plans");
        for (int i = 0; i < children.size(); i++) {
            collect(children.get(i));
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

    /** True if any step's type contains the text: has("Seq Scan"), has("Sort") (which also finds "Incremental Sort"). */
    public boolean has(String nodeType) {
        return nodes.stream().anyMatch(node -> node.type().contains(nodeType));
    }

    /** The names of the indexes the plan uses. */
    public List<String> indexes() {
        return nodes.stream().map(Node::index).filter(index -> index != null).distinct().toList();
    }

    /** The steps that read a table. */
    public List<Node> scans() {
        return nodes.stream().filter(Node::readsTable).toList();
    }

    /** Rows the query returned. */
    public long rowsReturned() {
        return nodes.getFirst().rows();
    }

    /** Rows read from tables (or from an index, for an index-only scan), kept or not. */
    public long rowsRead() {
        return scans().stream().mapToLong(node -> node.rows() + node.removedByFilter()).sum();
    }

    /** Rows that were read and then thrown away by a Filter. */
    public long rowsThrownAway() {
        return nodes.stream().mapToLong(Node::removedByFilter).sum();
    }

    /** Table visits made by index-only scans. 0 is the goal. */
    public long heapFetches() {
        return nodes.stream().mapToLong(Node::heapFetches).sum();
    }

    /** 8 kB pages the query touched, in tables and indexes together. */
    public long pages() {
        return pages;
    }

    public double executionMillis() {
        return executionMillis;
    }

    /** For example "Index Scan Backward using idx_orders_created_at on orders". */
    public String describe() {
        List<String> steps = nodes.stream()
                .filter(node -> node.relation() != null || node.index() != null)
                .map(Node::toString)
                .toList();
        return steps.isEmpty() ? nodes.getFirst().type() : String.join(" + ", steps);
    }

    @Override
    public String toString() {
        return describe();
    }
}
