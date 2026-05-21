package org.plovdev.sgoclient.example;

import org.plovdev.sgoclient.core.SGOClient;
import org.plovdev.sgoclient.core.SGOSession;
import org.plovdev.sgoclient.core.dto.Schools;
import org.plovdev.sgoclient.core.security.AuthKeys;
import org.plovdev.sgoclient.reports.SGOReportCreator;
import org.plovdev.sgoclient.reports.dto.ReportFilter;
import org.plovdev.sgoclient.reports.dto.SGOReport;
import org.plovdev.sgoclient.reports.dto.requests.JournalAccessReportRequest;

import java.nio.file.Files;
import java.nio.file.Path;

public class Main {
    public static void main(String[] args) {
        AuthKeys keys = AuthKeys.load("MY_NAME", "MY_PASS");

        try (SGOClient client = new SGOClient()) {
            SGOSession session = client.createSession(keys, Schools.MAOU6);

            SGOReportCreator creator = new SGOReportCreator(client);
            SGOReport report = creator.createReport(new JournalAccessReportRequest(ReportFilter.classIUPFilter("458655")));
            Files.write(Path.of("report.html"), report.getReportBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}