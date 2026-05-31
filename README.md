<!--
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
-->

# ORIGAM XSL-FO Server

Standalone Java service for rendering XSL-FO to PDF with embedded Apache FOP. The service keeps the JVM and `FopFactory` warm, so ORIGAM can transform report data to XSL-FO and delegate only the PDF render step to a sidecar or Docker Compose service.

## API

| Method | Path | Purpose |
| --- | --- | --- |
| `GET` | `/health` | Liveness check. |
| `GET` | `/ready` | Readiness check, including startup warmup. |
| `POST` | `/render` | Request body is XSL-FO XML. Response is `application/pdf`. |

Example:

```bash
curl -sS \
  -H 'Content-Type: application/xml' \
  --data-binary @examples/minimal.fo \
  http://localhost:8080/render \
  -o report.pdf
```

## Run With Docker Compose

```bash
docker compose up --build
```

The compose file exposes the renderer on `http://localhost:8080`. Mount report images, custom fonts, and other relative resources into the container and point `FOP_BASE_URI` at that mounted path.

## Configuration

| Variable | Default | Description |
| --- | --- | --- |
| `APP_HOST` | `0.0.0.0` | Bind host. |
| `APP_PORT` | `8080` | HTTP port. `PORT` is also supported as a fallback. |
| `WORKER_THREADS` | available processors, minimum `2` | HTTP and render worker count. |
| `MAX_REQUEST_BYTES` | `20971520` | Maximum XSL-FO request body size. |
| `RENDER_TIMEOUT_SECONDS` | `60` | Per-request render timeout. |
| `WARMUP_ENABLED` | `true` | Runs a tiny render during startup. |
| `FOP_BASE_URI` | current working directory locally, `/app/work/` in Docker | Base URI for relative resources. |
| `FOP_CONFIG_FILE` | unset locally, `/app/config/fop.xconf` in Docker | Apache FOP configuration file. |

## Included Fonts

The Docker image includes this default international font pack:

```text
fontconfig
fonts-dejavu-core
fonts-liberation2
fonts-noto-core
fonts-noto-extra
```

This gives broad Noto coverage plus practical office-document fallbacks. Add report-specific corporate fonts through a custom image layer or a mounted FOP configuration.

The bundled FOP configuration explicitly registers known FOP-loadable TrueType fonts for Latin, Greek, Cyrillic, Arabic, Hebrew, Devanagari, symbols, and Arial-compatible fallback text. CJK fonts are not included by default because Ubuntu's Noto CJK package provides `.ttc` collections that this FOP stack does not load reliably. For production CJK reports, add FOP-compatible CJK font files through a custom image layer and register them in `config/fop.xconf`.

## ORIGAM Integration Shape

The prototype currently writes XSL-FO to a temp file and shells out to `fop`. With this service, keep the existing ORIGAM report lookup, default parameter population, data loading, and XSLT transformation. Replace only the `RenderPdfWithFop(...)` section with an HTTP call:

```csharp
using var client = new HttpClient
{
    BaseAddress = new Uri(
        Environment.GetEnvironmentVariable("XSLFO_RENDERER_URL")
        ?? "http://xslfo:8080")
};

using var content = new StringContent(
    resultDoc.Xml.OuterXml,
    Encoding.UTF8,
    "application/xml");
using var response = client.PostAsync("/render", content).GetAwaiter().GetResult();
response.EnsureSuccessStatusCode();
return response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult();
```

For an in-cluster sidecar, set `XSLFO_RENDERER_URL=http://127.0.0.1:8080`. For Docker Compose, use the service DNS name, for example `http://xslfo:8080`.

## Local Build

```bash
mvn test
mvn package
java -jar target/xsl-fo-server-0.1.0-SNAPSHOT-shaded.jar
```

This repository intentionally uses the embedded FOP API rather than starting an external `fop` process. Apache documents `FopFactory` as the reusable object for multiple rendering runs, which is the important warm-service optimization here.

## Notes

Treat this as an internal service. XSL-FO can reference external graphics and fonts, so do not expose `/render` directly to untrusted clients without network and resource restrictions.

## References

- [Apache FOP 2.11](https://xmlgraphics.apache.org/fop/2.11/)
- [Apache FOP embedding guide](https://xmlgraphics.apache.org/fop/trunk/embedding.html)
