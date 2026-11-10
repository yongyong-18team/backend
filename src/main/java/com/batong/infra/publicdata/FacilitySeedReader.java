package com.batong.infra.publicdata;

import com.batong.global.enums.District;
import com.batong.global.enums.FacilitySource;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 시설 시드 CSV를 {@link FacilitySeedRow} 목록으로 읽는다.
 * 잘못된 줄이 하나라도 있으면 몇 번째 데이터 행인지 알려 주는 예외를 던지고, 일부만 적재하지 않도록 전체를 거부한다.
 */
public final class FacilitySeedReader {

    private FacilitySeedReader() {
    }

    public static List<FacilitySeedRow> read(Reader reader) throws IOException {
        return convert(CsvParser.parseWithHeader(reader));
    }

    static List<FacilitySeedRow> convert(List<Map<String, String>> records) {
        List<FacilitySeedRow> rows = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < records.size(); i++) {
            int line = i + 1;
            FacilitySeedRow row = toRow(records.get(i), line);
            if (!keys.add(row.source() + "/" + row.externalId())) {
                throw new IllegalArgumentException(
                    "데이터 " + line + "번째 행: (source, external_id)가 중복입니다 " + row.source() + "/" + row.externalId());
            }
            rows.add(row);
        }
        return rows;
    }

    private static FacilitySeedRow toRow(Map<String, String> r, int line) {
        try {
            if (!"FACILITY".equals(required(r, "resource_kind"))) {
                throw new IllegalArgumentException("resource_kind는 FACILITY여야 합니다");
            }
            return new FacilitySeedRow(
                FacilitySource.valueOf(required(r, "source")),
                required(r, "external_id"),
                required(r, "name"),
                optional(r, "phone"),
                optional(r, "official_url"),
                required(r, "facility_type"),
                District.valueOf(required(r, "district")),
                optional(r, "address"),
                decimal(r, "lat"),
                decimal(r, "lng"),
                optional(r, "services"),
                OffsetDateTime.parse(required(r, "fetched_at")).toInstant());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("데이터 " + line + "번째 행: " + e.getMessage(), e);
        }
    }

    private static String required(Map<String, String> r, String column) {
        String v = optional(r, column);
        if (v == null) {
            throw new IllegalArgumentException(column + " 값이 비어 있습니다");
        }
        return v;
    }

    /** 없는 열이거나 공백뿐이면 null */
    private static String optional(Map<String, String> r, String column) {
        String v = r.get(column);
        if (v == null || v.isBlank()) {
            return null;
        }
        return v.strip();
    }

    private static BigDecimal decimal(Map<String, String> r, String column) {
        String v = optional(r, column);
        return v == null ? null : new BigDecimal(v);
    }
}
