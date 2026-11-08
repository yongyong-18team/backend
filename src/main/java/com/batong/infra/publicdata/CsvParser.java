package com.batong.infra.publicdata;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CsvParser {

    private static final char BOM = '\uFEFF';

    private CsvParser() {
    }

    /** 헤더 줄을 키로 쓴 행 목록. 행마다 열 수가 헤더와 같아야 한다 */
    public static List<Map<String, String>> parseWithHeader(Reader reader) throws IOException {
        return parseWithHeader(readAll(reader));
    }

    public static List<Map<String, String>> parseWithHeader(String text) {
        List<List<String>> rows = parse(text);
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> header = rows.get(0);
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 1; i < rows.size(); i++) {
            List<String> row = rows.get(i);
            if (row.size() != header.size()) {
                throw new IllegalArgumentException(
                    "CSV " + (i + 1) + "번째 행의 열 수가 헤더와 다릅니다 (헤더 " + header.size() + ", 행 " + row.size() + ")");
            }
            Map<String, String> map = new LinkedHashMap<>();
            for (int c = 0; c < header.size(); c++) {
                map.put(header.get(c), row.get(c));
            }
            result.add(map);
        }
        return result;
    }

    public static List<List<String>> parse(Reader reader) throws IOException {
        return parse(readAll(reader));
    }

    public static List<List<String>> parse(String text) {
        String src = (!text.isEmpty() && text.charAt(0) == BOM) ? text.substring(1) : text;

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldQuoted = false;

        for (int i = 0; i < src.length(); i++) {
            char ch = src.charAt(i);
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < src.length() && src.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        inQuotes = false;
                    }
                } else {
                    field.append(ch);
                }
                continue;
            }
            if (ch == '"' && field.length() == 0 && !fieldQuoted) {
                inQuotes = true;
                fieldQuoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldQuoted = false;
            } else if (ch == '\r' || ch == '\n') {
                if (ch == '\r' && i + 1 < src.length() && src.charAt(i + 1) == '\n') {
                    i++;
                }
                endRow(rows, row, field, fieldQuoted);
                row = new ArrayList<>();
                field.setLength(0);
                fieldQuoted = false;
            } else {
                field.append(ch);
            }
        }
        if (inQuotes) {
            throw new IllegalArgumentException("CSV에 닫히지 않은 따옴표가 있습니다");
        }
        if (field.length() > 0 || fieldQuoted || !row.isEmpty()) {
            endRow(rows, row, field, fieldQuoted);
        }
        return rows;
    }

    /** 마지막 값을 행에 넣고, 완전히 빈 줄(값 없이 줄바꿈만)이면 버린다 */
    private static void endRow(List<List<String>> rows, List<String> row, StringBuilder field, boolean fieldQuoted) {
        boolean blankLine = row.isEmpty() && field.length() == 0 && !fieldQuoted;
        if (blankLine) {
            return;
        }
        row.add(field.toString());
        rows.add(row);
    }

    private static String readAll(Reader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[4096];
        int n;
        while ((n = reader.read(buf)) != -1) {
            sb.append(buf, 0, n);
        }
        return sb.toString();
    }
}
