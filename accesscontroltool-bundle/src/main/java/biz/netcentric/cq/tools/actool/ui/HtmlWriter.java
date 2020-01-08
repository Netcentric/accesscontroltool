package biz.netcentric.cq.tools.actool.ui;

import static org.apache.commons.lang3.StringEscapeUtils.escapeHtml4;

import java.io.PrintWriter;

/** Utility class for writing html as regular templating cannot be used for web console plugin. */
class HtmlWriter {

    final PrintWriter pw;
    final boolean isTouchUi;

    HtmlWriter(final PrintWriter pw, boolean isTouchUi) {
        this.pw = pw;
        this.isTouchUi = isTouchUi;
    }

    void openTable(String id) {
        pw.println("<table id='"+id+"' " + (isTouchUi ? " is='coral-table'" : " class='content' cellpadding='0' cellspacing='0' width='100%'") + ">");
    }

    void closeTable() {
        pw.println("</table>");
    }

    void tableHeader(String title, int colspan, boolean escape) {
        tr();
        pw.print("<th " + (isTouchUi ? "  is='coral-table-headercell'" : " class='content container'") + " colspan='" + colspan + "'>");
        pw.print(escape ? escapeHtml4(title) : title);
        pw.println("</th>");
        closeTr();
    }

    void print(String s) {
        pw.print(s);
    }

    void println(String s) {
        pw.println(s);
    }

    void newLine() {
        pw.println("<br/>");
    }

    void openTd() {
        openTd(0);
    }

    void openTd(int colspan) {
        pw.print("<td " + (isTouchUi ? "is='coral-table-cell'" : " class='content'") + (colspan>0?" colspan='" + colspan + "'":"")+">");
    }

    void closeTd() {
        pw.print("</td>");
    }

    void td(final String label) {
        td(label, 1);
    }

    void td(final String label, int colspan) {
        openTd(colspan);
        pw.print(escapeHtml4(label));
        closeTd();
    }

    void tr() {
        pw.println("<tr " + (isTouchUi ? " is='coral-table-row'" : " class='content'") + ">");
    }

    void closeTr() {
        pw.println("</tr>");
    }

    void tableHeader(String title, int colspan) {
        tableHeader(title, colspan, true);
    }

}