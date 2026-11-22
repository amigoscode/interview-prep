package com.amigoscode.restapi.support;

import com.amigoscode.restapi.ApiExceptionHandler;
import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderController;
import com.amigoscode.restapi.order.OrderService;
import com.amigoscode.restapi.order.OrderStatus;
import com.amigoscode.restapi.order.Product;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * The controller, the service and the exception handler wired together by hand: no Spring context,
 * no database, no network. The repository is InMemoryOrders. A whole test class runs in milliseconds.
 *
 * It starts with three orders:
 * 1 PENDING (Maria, 1 MONITOR, 200.00, with notes, a phone number and an internal note) | 2 PAID | 3 SHIPPED
 */
public final class UnitApi {

    public static final String VALID_ORDER = """
            {"customer": "Nina Patel", "product": "MOUSE", "quantity": 2, "shippingAddress": "8 Queen Square, Bath"}
            """;

    public final InMemoryOrders orders = new InMemoryOrders();
    private final MockMvc mvc;

    public UnitApi() {
        Order maria = new Order("Maria Jones", Product.MONITOR, 1, "12 Mill Lane, Leeds");
        maria.setNotes("Ring the bell twice");
        maria.setContactPhone("0113 496 0001");
        maria.setInternalNote("Card was declined once in May");
        orders.save(maria);

        Order liam = new Order("Liam Brown", Product.MONITOR, 2, "9 Castle Road, Cardiff");
        liam.setStatus(OrderStatus.PAID);
        orders.save(liam);

        Order chen = new Order("Chen Wei", Product.KEYBOARD, 1, "31 Dock Street, Hull");
        chen.setStatus(OrderStatus.SHIPPED);
        orders.save(chen);

        mvc = MockMvcBuilders.standaloneSetup(new OrderController(new OrderService(orders)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    public Order order(long id) {
        return orders.findById(id).orElseThrow();
    }

    public ResultActions get(String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.get(path));
    }

    public ResultActions post(String path) throws Exception {
        return mvc.perform(MockMvcRequestBuilders.post(path));
    }

    public ResultActions post(String path, String json) throws Exception {
        return mvc.perform(json(MockMvcRequestBuilders.post(path), json));
    }

    public ResultActions put(String path, String json) throws Exception {
        return mvc.perform(json(MockMvcRequestBuilders.put(path), json));
    }

    public ResultActions patch(String path, String json) throws Exception {
        return mvc.perform(json(MockMvcRequestBuilders.patch(path), json));
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String json) {
        return request.contentType(MediaType.APPLICATION_JSON).content(json);
    }
}
