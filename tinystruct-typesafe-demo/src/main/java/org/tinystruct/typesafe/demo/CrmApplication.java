package org.tinystruct.typesafe.demo;

import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

/**
 * Demo: CRM. Create, update and delete a customer.
 *
 * <pre>
 *   bin/dispatcher semantic --input "add a customer called Alice"
 *   bin/dispatcher semantic --input "set Bob's email to bob@example.com"
 *   bin/dispatcher semantic --input "delete customer John"     (held for confirmation)
 * </pre>
 */
public class CrmApplication extends AbstractApplication {

    @Override
    public void init() {
        setTemplateRequired(false);
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    @Action(value = "create-customer",
            description = "Create a new CRM customer with a name.",
            arguments = {@Argument(key = "name", description = "The customer's full name.")})
    public Builder createCustomer(String name) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "create-customer");
        result.put("name", name);
        return result;
    }

    @Action(value = "update-customer",
            description = "Change the email address of an existing CRM customer.",
            arguments = {
                    @Argument(key = "name", description = "The customer to update."),
                    @Argument(key = "email", description = "The new email address.")
            })
    public Builder updateCustomer(String name, String email) throws ApplicationException {
        requireName(name);
        if (email == null || !email.contains("@")) throw new ApplicationException("email must be an email address.");
        Builder result = new Builder();
        result.put("action", "update-customer");
        result.put("name", name);
        result.put("email", email);
        return result;
    }

    @Action(value = "delete-customer",
            description = "Permanently delete a CRM customer. Irreversible; held for confirmation.",
            arguments = {@Argument(key = "name", description = "The customer to delete permanently.")})
    public Builder deleteCustomer(String name) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "delete-customer");
        result.put("name", name);
        result.put("deleted", true);
        return result;
    }

    private static void requireName(String name) throws ApplicationException {
        if (name == null || name.isBlank()) throw new ApplicationException("name must not be blank.");
        if (name.length() > 100) throw new ApplicationException("name is too long.");
    }
}
