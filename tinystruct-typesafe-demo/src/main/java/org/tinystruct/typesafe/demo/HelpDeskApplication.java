package org.tinystruct.typesafe.demo;

import org.tinystruct.AbstractApplication;
import org.tinystruct.ApplicationException;
import org.tinystruct.data.component.Builder;
import org.tinystruct.system.annotation.Action;
import org.tinystruct.system.annotation.Argument;

/**
 * Demo: help desk. Create, escalate and close a ticket.
 *
 * <pre>
 *   bin/dispatcher semantic --input "open a high priority ticket for the login problem"
 *   bin/dispatcher semantic --input "escalate ticket 42"
 *   bin/dispatcher semantic --input "close ticket 99"
 * </pre>
 */
public class HelpDeskApplication extends AbstractApplication {

    public enum Priority { LOW, MEDIUM, HIGH, CRITICAL }

    @Override
    public void init() {
        setTemplateRequired(false);
    }

    @Override
    public String version() {
        return "1.0.0-SNAPSHOT";
    }

    @Action(value = "create-ticket",
            description = "Create a help desk ticket with a short title and a priority.",
            arguments = {
                    @Argument(key = "title", description = "A short description of the problem."),
                    @Argument(key = "priority", description = "How urgent it is: LOW, MEDIUM, HIGH or CRITICAL.")
            })
    public Builder createTicket(String title, Priority priority) throws ApplicationException {
        if (title == null || title.isBlank()) throw new ApplicationException("title must not be blank.");
        if (title.length() > 200) throw new ApplicationException("title is too long.");
        Builder result = new Builder();
        result.put("action", "create-ticket");
        result.put("title", title);
        result.put("priority", priority.name());
        return result;
    }

    @Action(value = "escalate-ticket",
            description = "Escalate an existing ticket, by its number, to a higher support tier.",
            arguments = {@Argument(key = "ticketId", type = "number", description = "The ticket number.")})
    public Builder escalateTicket(int ticketId) throws ApplicationException {
        requirePositive(ticketId);
        Builder result = new Builder();
        result.put("action", "escalate-ticket");
        result.put("ticketId", ticketId);
        return result;
    }

    @Action(value = "close-ticket",
            description = "Close and resolve an existing ticket, by its number.",
            arguments = {@Argument(key = "ticketId", type = "number", description = "The ticket number.")})
    public Builder closeTicket(int ticketId) throws ApplicationException {
        requirePositive(ticketId);
        Builder result = new Builder();
        result.put("action", "close-ticket");
        result.put("ticketId", ticketId);
        return result;
    }

    private static void requirePositive(int ticketId) throws ApplicationException {
        if (ticketId <= 0) throw new ApplicationException("ticketId must be positive.");
    }
}
