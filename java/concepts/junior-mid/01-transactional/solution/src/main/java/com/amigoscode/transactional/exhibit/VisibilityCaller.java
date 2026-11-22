package com.amigoscode.transactional.exhibit;

import com.amigoscode.transactional.exhibit.VisibilityExhibit.Observation;
import org.springframework.stereotype.Component;

/**
 * Another bean in the same package, so it is allowed to call the protected and package-private
 * methods. It holds the PROXY of VisibilityExhibit, like every bean that has one injected.
 */
@Component
public class VisibilityCaller {

    private final VisibilityExhibit exhibit;

    public VisibilityCaller(VisibilityExhibit exhibit) {
        this.exhibit = exhibit;
    }

    public Observation callPublic() {
        return exhibit.publicMethod();
    }

    public Observation callProtected() {
        return exhibit.protectedMethod();
    }

    public Observation callPackagePrivate() {
        return exhibit.packagePrivateMethod();
    }

    public Observation callFinal() {
        return exhibit.finalMethod();
    }

    public Observation callPrivate() {
        return exhibit.callsPrivateMethod();
    }
}
