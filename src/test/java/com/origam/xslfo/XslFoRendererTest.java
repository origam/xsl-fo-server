package com.origam.xslfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class XslFoRendererTest {
    private static final String MINIMAL_FO = """
            <?xml version="1.0" encoding="UTF-8"?>
            <fo:root xmlns:fo="http://www.w3.org/1999/XSL/Format">
              <fo:layout-master-set>
                <fo:simple-page-master master-name="A4" page-height="29.7cm" page-width="21cm" margin="2cm">
                  <fo:region-body/>
                </fo:simple-page-master>
              </fo:layout-master-set>
              <fo:page-sequence master-reference="A4">
                <fo:flow flow-name="xsl-region-body">
                  <fo:block font-family="sans-serif" font-size="14pt">Hello from ORIGAM XSL-FO Server</fo:block>
                </fo:flow>
              </fo:page-sequence>
            </fo:root>
            """;

    @Test
    void rendersMinimalFoToPdf() throws Exception {
        AppConfig config = new AppConfig(
                "127.0.0.1",
                8080,
                1,
                1024 * 1024,
                Duration.ofSeconds(5),
                Path.of(".").toAbsolutePath().normalize().toUri(),
                null,
                false);
        XslFoRenderer renderer = XslFoRenderer.create(config);

        RenderResult result = renderer.render(MINIMAL_FO.getBytes(StandardCharsets.UTF_8));

        assertEquals(1, result.pageCount());
        assertTrue(result.pdf().length > 100);
        assertEquals("%PDF", new String(result.pdf(), 0, 4, StandardCharsets.US_ASCII));
    }
}
