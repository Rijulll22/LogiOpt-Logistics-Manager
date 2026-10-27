package com.logiopt.desktop.support;

import com.logiopt.app.SchemaStatus;

/**
 * Text of the start-up dialog shown when the database cannot be used. It names the database version
 * and the version this build needs; the MigrationTool command is only written to the log.
 */
public final class SchemaDialogText {

    /** Window title. */
    public static final String TITLE = "LogiOpt cannot start";

    private final String header;
    private final String content;

    private SchemaDialogText(String header, String content) {
        this.header = header;
        this.content = content;
    }

    /**
     * @param status result of the schema check (not usable)
     * @return dialog text
     */
    public static SchemaDialogText of(SchemaStatus status) {
        String versions = "Database version: " + (status.getAppliedVersion() == null ? "unknown"
                : String.valueOf(status.getAppliedVersion())) + "\nRequired version: " + status.getRequiredVersion();
        switch (status.getState()) {
            case BEHIND:
                return new SchemaDialogText("The database must be upgraded first.",
                        versions + "\n\nLogiOpt never changes the database schema by itself. Ask your administrator "
                                + "to run MigrationTool; the exact command is in the log. Then choose Retry.");
            case FAILED:
                return new SchemaDialogText("A database migration is recorded as failed.",
                        versions + "\n\nThe database must be repaired before LogiOpt can use it. The log names the "
                                + "MigrationTool command to inspect it. Then choose Retry.");
            case UNREACHABLE:
                return new SchemaDialogText("The database could not be reached.",
                        versions + "\n\nCheck that MySQL is running and that LOGIOPT_DB_URL, LOGIOPT_DB_USER and "
                                + "LOGIOPT_DB_PASSWORD are set for this window. Then choose Retry.");
            default:
                return new SchemaDialogText("The database is ready.", versions);
        }
    }

    /** @return short header line */
    public String getHeader() {
        return header;
    }

    /** @return body text with both versions */
    public String getContent() {
        return content;
    }
}
