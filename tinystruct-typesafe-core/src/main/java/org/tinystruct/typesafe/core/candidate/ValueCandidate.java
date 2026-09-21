package org.tinystruct.typesafe.core.candidate;

/**
 * A verbatim token span extracted from the user input as a candidate value for an open parameter.
 * The value passed to the action is always the exact span, never model-generated.
 */
public final class ValueCandidate {

    private final String value;
    private final int startIndex;
    private final int endIndex;

    public ValueCandidate(String value, int startIndex, int endIndex) {
        this.value = value;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
    }

    /** The verbatim text span from the user input. */
    public String getValue() { return value; }

    /** Start character index in the original input (inclusive). */
    public int getStartIndex() { return startIndex; }

    /** End character index in the original input (exclusive). */
    public int getEndIndex() { return endIndex; }

    @Override
    public String toString() {
        return "ValueCandidate{\"" + value + "\"}";
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ValueCandidate)) return false;
        return value.equals(((ValueCandidate) obj).value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }
}
