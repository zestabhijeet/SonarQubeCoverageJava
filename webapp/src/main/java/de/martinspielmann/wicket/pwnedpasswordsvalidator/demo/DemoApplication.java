package de.martinspielmann.wicket.pwnedpasswordsvalidator.demo;

import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;

/**
 * Wicket application for the pwned-passwords validator demo.
 * Registered in WEB-INF/web.xml and served by Tomcat.
 */
public class DemoApplication extends WebApplication {

    @Override
    public Class<? extends WebPage> getHomePage() {
        return HomePage.class;
    }
}
