package de.martinspielmann.wicket.pwnedpasswordsvalidator.demo;

import de.martinspielmann.wicket.pwnedpasswordsvalidator.PwnedPasswordsValidator;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.PasswordTextField;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;

/**
 * Single page with a password field checked by {@link PwnedPasswordsValidator}.
 * Submitting a known-breached password (e.g. "password123") shows an error.
 */
public class HomePage extends WebPage {
    private static final long serialVersionUID = 1L;

    public HomePage(final PageParameters parameters) {
        super(parameters);

        add(new Label("version", getApplication().getFrameworkSettings().getVersion()));

        Form<Void> form = new Form<Void>("form") {
            private static final long serialVersionUID = 1L;

            @Override
            protected void onSubmit() {
                info("Good news: this password was not found in any known data breach.");
            }
        };
        add(form);
        form.add(new FeedbackPanel("feedback"));
        form.add(new PasswordTextField("pw", new Model<>("")).add(new PwnedPasswordsValidator()));
    }
}
