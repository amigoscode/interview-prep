package com.amigoscode.restapi.admin;

import com.amigoscode.restapi.order.Order;
import com.amigoscode.restapi.order.OrderService;
import com.amigoscode.restapi.order.OrderStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

/** The page staff use to cancel pending orders. Plain HTML, built by hand to keep the project small. */
@RestController
public class AdminPageController {

    private final OrderService service;

    public AdminPageController(OrderService service) {
        this.service = service;
    }

    @GetMapping(value = "/admin/pending-orders", produces = MediaType.TEXT_HTML_VALUE)
    public String pendingOrders() {
        StringBuilder html = new StringBuilder("<html><body><h1>Pending orders</h1><ul>\n");
        for (Order order : service.list(OrderStatus.PENDING)) {
            html.append("<li>#").append(order.getId()).append(' ').append(HtmlUtils.htmlEscape(order.getCustomer())).append(' ')
                    // TODO Task 2: a link. Anything that reads this page may request it.
                    .append("<a href=\"/api/orders/").append(order.getId()).append("/cancel\">Cancel</a></li>\n");
        }
        return html.append("</ul></body></html>\n").toString();
    }
}
