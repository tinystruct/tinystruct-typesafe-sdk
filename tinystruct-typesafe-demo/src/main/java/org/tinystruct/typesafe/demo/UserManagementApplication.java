package org.tinystruct.typesafe.demo;

import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

import java.util.Set;

/**
 * Demo: user management. Every action is an ordinary tinystruct action, runnable directly
 * ({@code bin/dispatcher create-user/John/ADMIN}) or through the semantic dispatcher.
 *
 * <p>Every parameter is declared in {@code @Action(arguments = ...)}: that is what tinystruct records
 * as the action's command metadata, and what the semantic dispatcher turns into questions.
 *
 * <pre>
 *   bin/dispatcher semantic --input "create an admin account for John"
 *   bin/dispatcher semantic --input "give Alice editor and viewer access"
 *   bin/dispatcher semantic --input "delete user Bob"        (held for confirmation)
 *   bin/dispatcher semantic/confirm/&lt;id&gt;
 * </pre>
 */
public class UserManagementApplication extends AbstractApplication {

    public enum Role { ADMIN, EDITOR, VIEWER, SUPPORT, READONLY }

    @Override
    public void init() {
        setTemplateRequired(false);
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    @Action(value = "create-user",
            description = "Create a new user account with a name and a role.",
            arguments = {
                    @Argument(key = "name", description = "The user's name."),
                    @Argument(key = "role", description = "The role of the account: ADMIN, EDITOR, VIEWER, SUPPORT or READONLY.")
            })
    public Builder createUser(String name, Role role) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "create-user");
        result.put("name", name);
        result.put("role", role.name());
        return result;
    }

    @Action(value = "assign-roles",
            description = "Assign one or more roles to an existing user account.",
            arguments = {
                    @Argument(key = "name", description = "The user to give the roles to."),
                    @Argument(key = "roles", description = "The roles to assign.")
            })
    public Builder assignRoles(String name, Set<Role> roles) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "assign-roles");
        result.put("name", name);
        result.put("roles", roles.toString());
        return result;
    }

    @Action(value = "deactivate-user",
            description = "Deactivate a user account. The account is disabled, not deleted.",
            arguments = {@Argument(key = "name", description = "The user to deactivate.")})
    public Builder deactivateUser(String name) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "deactivate-user");
        result.put("name", name);
        return result;
    }

    @Action(value = "delete-user",
            description = "Permanently delete a user account. Irreversible; held for confirmation.",
            arguments = {@Argument(key = "name", description = "The user to delete permanently.")})
    public Builder deleteUser(String name) throws ApplicationException {
        requireName(name);
        Builder result = new Builder();
        result.put("action", "delete-user");
        result.put("name", name);
        result.put("deleted", true);
        return result;
    }

    private static void requireName(String name) throws ApplicationException {
        if (name == null || name.isBlank()) throw new ApplicationException("name must not be blank.");
        if (name.length() > 100) throw new ApplicationException("name is too long.");
    }
}
