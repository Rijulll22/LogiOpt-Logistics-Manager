package com.logiopt.report;

import java.nio.file.Path;

/**
 * A report file that may be sent to its owner: the resolved file inside the reports directory and
 * the generated name the browser should save it as. The path is never shown to users.
 */
public final class ReportDownload {

    private final Path file;
    private final String downloadName;

    ReportDownload(Path file, String downloadName) {
        this.file = file;
        this.downloadName = downloadName;
    }

    /** @return resolved file inside the reports directory */
    public Path getFile() {
        return file;
    }

    /** @return safe generated file name for Content-Disposition */
    public String getDownloadName() {
        return downloadName;
    }
}
