package com.batong.infra.publicdata;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvParserTest {

    @Test
    void 쉼표로_나누고_빈_값은_빈_문자열로_남긴다() {
        assertThat(CsvParser.parse("a,,c\n1,2,3")).containsExactly(
            List.of("a", "", "c"),
            List.of("1", "2", "3"));
    }

    @Test
    void 따옴표_안의_쉼표는_값의_일부다() {
        List<List<String>> rows = CsvParser.parse("하선이네,\"경기도 용인시 처인구 둔전로 15, 105동 306호\",CHEOIN");

        assertThat(rows).containsExactly(List.of("하선이네", "경기도 용인시 처인구 둔전로 15, 105동 306호", "CHEOIN"));
    }

    @Test
    void 이중_따옴표는_따옴표_하나로_읽는다() {
        assertThat(CsvParser.parse("\"그는 \"\"안녕\"\"이라고 했다\",x"))
            .containsExactly(List.of("그는 \"안녕\"이라고 했다", "x"));
    }

    @Test
    void 따옴표_안의_줄바꿈은_값의_일부다() {
        assertThat(CsvParser.parse("\"1층\n2층\",x\ny,z"))
            .containsExactly(List.of("1층\n2층", "x"), List.of("y", "z"));
    }

    @Test
    void CRLF와_마지막_줄_개행_없음과_빈_줄을_처리한다() {
        assertThat(CsvParser.parse("a,b\r\n\r\nc,d\r\ne,f")).containsExactly(
            List.of("a", "b"),
            List.of("c", "d"),
            List.of("e", "f"));
    }

    @Test
    void 맨_앞_BOM은_제거한다() {
        assertThat(CsvParser.parse("\uFEFFsource,name\nMANUAL,두드림1")).containsExactly(
            List.of("source", "name"),
            List.of("MANUAL", "두드림1"));
    }

    @Test
    void 빈_따옴표_값도_한_칸으로_센다() {
        assertThat(CsvParser.parse("a,\"\",c")).containsExactly(List.of("a", "", "c"));
    }

    @Test
    void 닫히지_않은_따옴표는_예외다() {
        assertThatThrownBy(() -> CsvParser.parse("a,\"b\nc,d"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("따옴표");
    }

    @Test
    void 헤더를_키로_쓴_행_목록을_만든다() throws IOException {
        String csv = "source,external_id,name\nYONGIN_API,32,용인시처인장애인복지관\nMANUAL,MANUAL-01,마실장애인지원센터\n";

        List<Map<String, String>> rows = CsvParser.parseWithHeader(new StringReader(csv));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0)).containsEntry("source", "YONGIN_API").containsEntry("name", "용인시처인장애인복지관");
        assertThat(rows.get(1)).containsEntry("external_id", "MANUAL-01");
    }

    @Test
    void 열_수가_헤더와_다르면_몇_번째_행인지_알려준다() {
        assertThatThrownBy(() -> CsvParser.parseWithHeader("a,b,c\n1,2,3\n4,5"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("3번째 행");
    }

    @Test
    void 내용이_없으면_빈_목록이다() {
        assertThat(CsvParser.parseWithHeader("")).isEmpty();
        assertThat(CsvParser.parse("\n\n")).isEmpty();
    }
}
