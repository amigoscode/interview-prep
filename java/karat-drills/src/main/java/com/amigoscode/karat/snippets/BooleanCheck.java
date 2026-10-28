package com.amigoscode.karat.snippets;

/** Theory question: "Analyze the following code. Is it correct, or is a fix needed?" */
public final class BooleanCheck {

    private BooleanCheck() {
    }

    /** Returns whether {@code value} is true. A missing value counts as false. */
    public static boolean isValid(Boolean value) {
        // Bug was value.equals(...): a null Boolean threw NullPointerException. Put the constant first.
        return Boolean.TRUE.equals(value);
    }
}
