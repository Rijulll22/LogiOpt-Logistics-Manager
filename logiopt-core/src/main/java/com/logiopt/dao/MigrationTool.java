package com.logiopt.dao;

import com.logiopt.exception.DataAccessException;

import java.io.PrintStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Explicit command for applying migrations; the web application never migrates on its own.
 *
 * <pre>
 * java -cp "target\logiopt\WEB-INF\classes;target\logiopt\WEB-INF\lib\*" com.logiopt.dao.MigrationTool --target=test [--status]
 * java -cp "..." com.logiopt.dao.MigrationTool --target=main --confirm-database=logiopt [--status]
 * </pre>
 *
 * --target=test uses LOGIOPT_TEST_DB_URL and refuses any database whose name does not end in
 * "_test". --target=main uses LOGIOPT_DB_URL and runs only if --confirm-database repeats the
 * database name in that URL. Only the database name is printed, never the URL or credentials.
 */
public final class MigrationTool {

    static final int OK = 0;
    static final int FAILED = 1;
    static final int USAGE = 2;

    private static final String USAGE_TEXT = "Usage: MigrationTool --target=test [--status]\n"
            + "       MigrationTool --target=main --confirm-database=<name in LOGIOPT_DB_URL> [--status]";

    private MigrationTool() {
    }

    /**
     * Runs the tool.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    static int run(String[] args, PrintStream out, PrintStream err) {
        String target = null;
        String confirm = null;
        boolean status = false;
        for (String arg : args) {
            if (arg.startsWith("--target=")) {
                target = arg.substring("--target=".length());
            } else if (arg.startsWith("--confirm-database=")) {
                confirm = arg.substring("--confirm-database=".length());
            } else if ("--status".equals(arg)) {
                status = true;
            } else {
                err.println("Unknown argument: " + arg);
                err.println(USAGE_TEXT);
                return USAGE;
            }
        }
        try {
            DBConnection db;
            if ("test".equals(target)) {
                db = DBConnection.fromTestConfiguration();
                try (Connection con = db.getConnection()) {
                    DBConnection.requireTestDatabase(con);
                }
            } else if ("main".equals(target)) {
                db = DBConnection.fromConfiguration();
                String name = db.getDatabaseName();
                if (name == null || !name.equals(confirm)) {
                    err.println("Refusing: --confirm-database must equal the database name in "
                            + DBConnection.ENV_URL + " (found '" + name + "').");
                    return USAGE;
                }
            } else {
                err.println(USAGE_TEXT);
                return USAGE;
            }
            out.println("Target database: " + db.getDatabaseName());
            MigrationRunner runner = new MigrationRunner(db);
            List<String> lines = status ? runner.status() : runner.migrate();
            if (lines.isEmpty()) {
                out.println("Already up to date.");
            }
            for (String line : lines) {
                out.println(line);
            }
            return OK;
        } catch (DataAccessException e) {
            err.println(e.getMessage());
            return FAILED;
        } catch (SQLException e) {
            err.println("Database connection could not be closed cleanly.");
            return FAILED;
        }
    }
}
