package com.amigoscode.fetching.demo;

import com.amigoscode.fetching.FetchingApplication;
import com.amigoscode.fetching.customer.Customer;
import com.amigoscode.fetching.order.Order;
import com.amigoscode.fetching.order.OrderItem;
import com.amigoscode.fetching.sql.SqlRecorder;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.PluralAttribute;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Supplier;
import org.hibernate.Hibernate;
import org.hibernate.engine.FetchTiming;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.MappingMetamodel;
import org.hibernate.metamodel.mapping.AttributeMapping;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Makes lazy loading visible. It prints the fetch type of every association as Hibernate sees it,
 * then loads one order and shows what is really inside its fields: a proxy, a collection wrapper,
 * or the real thing. Then it touches them one at a time and shows the SQL each touch causes.
 * Run it with ./inspect (or .\inspect on Windows).
 */
public final class Inspect {

    public static void main(String[] args) {
        long orderId = 1;
        boolean twoBags = false;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--two-bags")) {
                twoBags = true;
            } else if (args[i].equals("--order") && i + 1 < args.length && args[i + 1].matches("\\d{1,9}")) {
                orderId = Long.parseLong(args[++i]);
            } else {
                System.out.println("Usage: ./inspect [--order <id>] [--two-bags]     (Windows: .\\inspect ...)");
                System.out.println();
                System.out.println("  --order <id>   which order to load (default: 1)");
                System.out.println("  --two-bags     also try to fetch two List collections in one query (Task 9)");
                return;
            }
        }
        if (!Db.isUp()) {
            return;
        }
        SpringApplication app = new SpringApplication(FetchingApplication.class);
        app.setBannerMode(Banner.Mode.OFF);
        app.setLogStartupInfo(false);
        try (ConfigurableApplicationContext context = app.run(
                "--spring.main.web-application-type=none",
                "--logging.level.root=ERROR")) {
            EntityManagerFactory factory = context.getBean(EntityManagerFactory.class);
            EntityManager entityManager = context.getBean(EntityManager.class);
            TransactionTemplate transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.setReadOnly(true);

            fetchTypes(factory);
            long id = orderId;
            Order[] detached = new Order[1];
            boolean found = Boolean.TRUE.equals(transaction.execute(status -> insideTransaction(entityManager, id, detached)));
            if (found) {
                afterTransaction(detached[0]);
            }
            if (twoBags) {
                twoBags(entityManager, transaction);
            }
            System.out.println();
        }
    }

    // ---- 1. the mapping ----------------------------------------------------------------------

    private static void fetchTypes(EntityManagerFactory factory) {
        MappingMetamodel hibernate = factory.unwrap(SessionFactoryImplementor.class).getMappingMetamodel();
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"association", "kind", "fetch", "loads a"});
        List<EntityType<?>> entities = new ArrayList<>(factory.getMetamodel().getEntities());
        entities.sort(Comparator.comparing(EntityType::getName));
        for (EntityType<?> entity : entities) {
            List<Attribute<?, ?>> attributes = new ArrayList<>(entity.getAttributes());
            attributes.sort(Comparator.comparing(Attribute::getName));
            for (Attribute<?, ?> attribute : attributes) {
                if (!attribute.isAssociation()) {
                    continue;
                }
                AttributeMapping mapping = hibernate.getEntityDescriptor(entity.getJavaType())
                        .findAttributeMapping(attribute.getName());
                boolean eager = mapping.getMappedFetchOptions().getTiming() == FetchTiming.IMMEDIATE;
                Class<?> target = attribute instanceof PluralAttribute<?, ?, ?> plural
                        ? plural.getElementType().getJavaType()
                        : attribute.getJavaType();
                rows.add(new String[] {
                        entity.getName() + "." + attribute.getName(),
                        attribute.getPersistentAttributeType().name().toLowerCase().replace('_', '-'),
                        eager ? "EAGER" : "LAZY",
                        attribute.isCollection() ? "List<" + target.getSimpleName() + ">" : target.getSimpleName()});
            }
        }
        System.out.println();
        System.out.println("1. FETCH TYPES (what Hibernate read from your entity classes)");
        System.out.println();
        printTable(rows);
        System.out.println();
        System.out.println("  EAGER = load it together with the owner, every time, whether anyone needs it or not.");
        System.out.println("  LAZY  = put a placeholder in the field and load it the first time somebody uses it.");
    }

    // ---- 2. one order, inside a transaction ----------------------------------------------------

    private static boolean insideTransaction(EntityManager entityManager, long id, Order[] detached) {
        SqlRecorder.reset();
        Order order = entityManager.find(Order.class, id);
        System.out.println();
        System.out.println("2. ORDER " + id + ", JUST LOADED: entityManager.find(Order.class, " + id + ")");
        System.out.println();
        printStatements("     ");
        if (order == null) {
            System.out.println("  Order " + id + " does not exist. Run ./demo once to fill the shop, or pick another --order.");
            return false;
        }
        System.out.println();
        System.out.println("  What is in the order's fields right now, before anyone used them:");
        System.out.println();
        printTable(fields(order));

        System.out.println();
        System.out.println("3. USE THEM, ONE AT A TIME (still inside the transaction, the session is open)");
        System.out.println();
        touch("order.getCustomer().getId()", () -> order.getCustomer().getId());
        touch("order.getCustomer().getName()", () -> order.getCustomer().getName());
        touch("order.getCustomer().getCity()", () -> order.getCustomer().getCity());
        touch("order.getItems().size()", () -> order.getItems().size());
        if (!order.getItems().isEmpty()) {
            OrderItem first = order.getItems().get(0);
            touch("items.get(0).getProduct().getName()", () -> first.getProduct().getName());
        }
        System.out.println();
        System.out.println("  The same fields now:");
        System.out.println();
        printTable(fields(order));

        // A second order that nobody touches: it leaves the transaction with its placeholders still empty.
        detached[0] = entityManager.find(Order.class, id + 1);
        return true;
    }

    private static List<String[]> fields(Order order) {
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[] {"field", "loaded?", "class of the object in the field"});
        rows.add(field("order.customer", order.getCustomer()));
        rows.add(field("order.items", order.getItems()));
        if (Hibernate.isInitialized(order.getItems()) && !order.getItems().isEmpty()) {
            rows.add(field("items.get(0).product", order.getItems().get(0).getProduct()));
        }
        return rows;
    }

    private static String[] field(String name, Object value) {
        return new String[] {name, Hibernate.isInitialized(value) ? "yes" : "NO", value.getClass().getName()};
    }

    private static void touch(String call, Supplier<Object> action) {
        SqlRecorder.reset();
        Object value = action.get();
        int statements = SqlRecorder.count();
        System.out.printf("  %-37s -> %-12s %d %s%n", call, value, statements, statements == 1 ? "statement" : "statements");
        printStatements("         ");
    }

    // ---- 3. after the transaction ------------------------------------------------------------

    private static void afterTransaction(Order order) {
        System.out.println();
        System.out.println("4. AFTER THE TRANSACTION (the session is closed)");
        System.out.println();
        if (order == null) {
            System.out.println("  There is no next order to try this on.");
            return;
        }
        System.out.println("  Order " + order.getId() + " was loaded in the same transaction, and nobody used its customer or items.");
        System.out.println();
        tryAfter("order.getCustomer().getName()", () -> order.getCustomer().getName());
        tryAfter("order.getItems().size()", () -> order.getItems().size());
    }

    private static void tryAfter(String call, Supplier<Object> action) {
        try {
            Object value = action.get();
            System.out.println("  " + call + " -> " + value + "   (it was already loaded, so no session is needed)");
        } catch (RuntimeException e) {
            System.out.println("  " + call);
            System.out.println("     -> " + e.getClass().getName() + ":");
            System.out.println("        " + e.getMessage());
        }
    }

    // ---- 4. two bags -------------------------------------------------------------------------

    private static void twoBags(EntityManager entityManager, TransactionTemplate transaction) {
        String jpql = "select c from Customer c join fetch c.orders o join fetch o.items";
        System.out.println();
        System.out.println("5. TWO BAGS: fetch two List collections in one query");
        System.out.println();
        System.out.println("  " + jpql);
        try {
            int customers = transaction.execute(status ->
                    entityManager.createQuery(jpql, Customer.class).getResultList().size());
            System.out.println("     -> " + customers + " customers. Hibernate accepted it.");
        } catch (RuntimeException e) {
            Throwable cause = e;
            while (cause.getCause() != null && !cause.getClass().getSimpleName().equals("MultipleBagFetchException")) {
                cause = cause.getCause();
            }
            System.out.println("     -> " + cause.getClass().getName() + ":");
            System.out.println("        " + cause.getMessage());
        }
    }

    // ---- printing ----------------------------------------------------------------------------

    private static void printStatements(String indent) {
        for (String statement : SqlRecorder.statements()) {
            System.out.println(indent + "sql: " + statement);
        }
    }

    private static void printTable(List<String[]> rows) {
        int[] widths = new int[rows.get(0).length];
        for (String[] row : rows) {
            for (int c = 0; c < row.length; c++) {
                widths[c] = Math.max(widths[c], row[c].length());
            }
        }
        for (int r = 0; r < rows.size(); r++) {
            StringBuilder line = new StringBuilder("  ");
            for (int c = 0; c < widths.length; c++) {
                line.append(String.format("%-" + widths[c] + "s", rows.get(r)[c])).append("  ");
            }
            System.out.println(line.toString().stripTrailing());
            if (r == 0) {
                StringBuilder rule = new StringBuilder("  ");
                for (int width : widths) {
                    rule.append("-".repeat(width)).append("  ");
                }
                System.out.println(rule.toString().stripTrailing());
            }
        }
    }

    private Inspect() {
    }
}
