/*
Copyright 2005 - 2026 Advantage Solutions, s. r. o.

This file is part of ORIGAM (http://www.origam.org).

ORIGAM is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

ORIGAM is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with ORIGAM. If not, see <http://www.gnu.org/licenses/>.
*/

package com.origam.xslfo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
        XslFoRenderer renderer = XslFoRenderer.create(testConfig(null));

        RenderResult result = renderer.render(MINIMAL_FO.getBytes(StandardCharsets.UTF_8));

        assertValidOnePagePdf(result);
    }

    @Test
    void rendersMultilingualFoWithConfiguredFonts() throws Exception {
        Path configFile = Path.of("config/fop.xconf").toAbsolutePath().normalize();
        XslFoRenderer renderer = XslFoRenderer.create(testConfig(configFile));
        byte[] xslFo = Files.readAllBytes(Path.of("src/test/resources/multilingual.fo"));

        RenderResult result = renderer.render(xslFo);

        assertValidOnePagePdf(result);
    }

    private static AppConfig testConfig(Path fopConfigFile) {
        return new AppConfig(
                "127.0.0.1",
                8080,
                1,
                1024 * 1024,
                Duration.ofSeconds(5),
                Path.of(".").toAbsolutePath().normalize().toUri(),
                fopConfigFile,
                false);
    }

    private static void assertValidOnePagePdf(RenderResult result) throws IOException {
        assertEquals(1, result.pageCount());
        assertTrue(result.pdf().length > 100);
        assertEquals("%PDF", new String(result.pdf(), 0, 4, StandardCharsets.US_ASCII));
    }
}
