package org.plovdev.sgoclient.reports.dto.requests;

import org.plovdev.sgoclient.reports.SGOReportOutputType;
import org.plovdev.sgoclient.reports.SGOReportType;
import org.plovdev.sgoclient.reports.dto.ReportFilter;

import java.util.List;

public class JournalAccessReportRequest extends SGOReportRequest {
    public JournalAccessReportRequest(SGOReportOutputType outputType, ReportFilter classIupFilter) {
        super(SGOReportType.JOURNAL_ACCESS, outputType, List.of(classIupFilter));
    }

    public JournalAccessReportRequest(ReportFilter classIupFilter) {
        super(SGOReportType.JOURNAL_ACCESS, SGOReportOutputType.HTML, List.of(classIupFilter));
    }
}