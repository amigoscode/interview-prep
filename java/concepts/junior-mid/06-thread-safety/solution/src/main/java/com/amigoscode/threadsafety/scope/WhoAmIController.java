package com.amigoscode.threadsafety.scope;

import com.amigoscode.threadsafety.visits.VisitCounter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * EXHIBIT for ./inspect. Answers one question per request: which thread served me, and which
 * objects did it use? Nothing to change here.
 */
@RestController
public class WhoAmIController {

    public record WhoAmI(String thread, String controller, String singletonService, String requestScopedBean,
                         String prototypeBean) {
    }

    private final VisitCounter visitCounter;
    private final RequestInfo requestInfo;
    private final PrototypeHelper prototypeHelper;

    public WhoAmIController(VisitCounter visitCounter, RequestInfo requestInfo, PrototypeHelper prototypeHelper) {
        this.visitCounter = visitCounter;
        this.requestInfo = requestInfo;
        this.prototypeHelper = prototypeHelper;
    }

    /** holdMillis keeps the request open, so several requests are inside this method at the same time. */
    @GetMapping("/api/whoami")
    public WhoAmI whoAmI(@RequestParam(defaultValue = "0") long holdMillis) throws InterruptedException {
        if (holdMillis > 0) {
            Thread.sleep(Math.min(holdMillis, 2_000));
        }
        return new WhoAmI(
                Thread.currentThread().getName(),
                identity(this),
                identity(visitCounter),
                requestInfo.name(),
                prototypeHelper.name());
    }

    private static String identity(Object object) {
        return object.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(object));
    }
}
