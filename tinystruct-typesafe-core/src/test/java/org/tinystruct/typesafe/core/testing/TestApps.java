package org.tinystruct.typesafe.core.testing;

import org.tinystruct.AbstractApplication;
import org.tinystruct.system.ApplicationManager;
import org.tinystruct.system.Settings;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;
import org.tinystruct.system.annotation.Action.Mode;

import java.util.List;
import java.util.Set;

/**
 * Real tinystruct applications used by the tests, installed into the real {@code ActionRegistry}
 * exactly as {@code bin/dispatcher --import} would. Nothing here is mocked.
 */
public final class TestApps {

    public enum Role { ADMIN, EDITOR, VIEWER }

    /** Records what the routed actions were called with. */
    public static final class Calls {
        public static volatile String last;
    }

    public static class UserApp extends AbstractApplication {
        @Override
        public void init() {
            setTemplateRequired(false);
        }

        @Override
        public String version() {
            return "1.0";
        }

        @Action(value = "create-user", description = "Create a user account.",
                arguments = {
                        @Argument(key = "name", description = "The user's name"),
                        @Argument(key = "role", description = "The role of the account")})
        public String createUser(String name, Role role) {
            Calls.last = "create-user:" + name + ":" + role;
            return Calls.last;
        }

        @Action(value = "assign-roles", description = "Assign roles to a user.",
                arguments = {
                        @Argument(key = "name", description = "The user's name"),
                        @Argument(key = "roles", description = "The roles to assign")})
        public String assignRoles(String name, Set<Role> roles) {
            Calls.last = "assign-roles:" + name + ":" + roles;
            return Calls.last;
        }

        @Action(value = "tag-user", description = "Tag a user.",
                arguments = {
                        @Argument(key = "tags", description = "The tags")})
        public String tagUser(List<Role> tags) {
            Calls.last = "tag-user:" + tags;
            return Calls.last;
        }

        @Action(value = "delete-user", description = "Permanently delete a user.",
                arguments = {@Argument(key = "name", description = "The user to delete")})
        public String deleteUser(String name) {
            Calls.last = "delete-user:" + name;
            return Calls.last;
        }

        @Action(value = "set-active", description = "Turn an account on or off.",
                arguments = {
                        @Argument(key = "name", description = "The user's name"),
                        @Argument(key = "active", type = "boolean", description = "Whether the account is active")})
        public String setActive(String name, boolean active) {
            Calls.last = "set-active:" + name + ":" + active;
            return Calls.last;
        }

        @Action(value = "add-credit", description = "Add credit to an account.",
                arguments = {
                        @Argument(key = "name", description = "The user's name"),
                        @Argument(key = "amount", type = "number", description = "Amount of credit")})
        public String addCredit(String name, int amount) {
            Calls.last = "add-credit:" + name + ":" + amount;
            return Calls.last;
        }

        /** Optional trailing parameter, expressed the tinystruct way: an overload with fewer parameters. */
        @Action(value = "create-customer", description = "Create a customer.",
                arguments = {
                        @Argument(key = "name", description = "Customer name"),
                        @Argument(key = "email", description = "Customer email", optional = true)})
        public String createCustomer(String name, String email) {
            Calls.last = "create-customer:" + name + ":" + email;
            return Calls.last;
        }

        @Action(value = "create-customer", description = "Create a customer.",
                arguments = {@Argument(key = "name", description = "Customer name")})
        public String createCustomer(String name) {
            Calls.last = "create-customer:" + name;
            return Calls.last;
        }

        @Action(value = "post-only", description = "Only over HTTP POST.", mode = Mode.HTTP_POST,
                arguments = {@Argument(key = "name", description = "A name")})
        public String postOnly(String name) {
            Calls.last = "post-only:" + name;
            return Calls.last;
        }

        @Action(value = "cli-only", description = "Only from the command line.", mode = Mode.CLI,
                arguments = {@Argument(key = "name", description = "A name")})
        public String cliOnly(String name) {
            Calls.last = "cli-only:" + name;
            return Calls.last;
        }

        /** Its pattern can match a create-user path whose first argument is "admin". */
        @Action(value = "create-user/admin", description = "Shadows create-user for one name.",
                arguments = {@Argument(key = "extra", description = "Anything")})
        public String shadow(String extra) {
            Calls.last = "SHADOW:" + extra;
            return Calls.last;
        }

        @Action(value = "bad-type", description = "Has a parameter type that cannot be asked.",
                arguments = {@Argument(key = "when", description = "A date-time")})
        public String badType(java.util.Map<String, String> when) {
            return "x";
        }

        @Action(value = "undeclared", description = "Has a parameter without an @Argument.")
        public String undeclared(String name) {
            return name;
        }
    }

    private static boolean installed;

    /** Installs the test applications once. */
    public static synchronized void install() {
        if (!installed) {
            ApplicationManager.install(new UserApp(), new Settings());
            installed = true;
        }
    }

    private TestApps() {}
}
