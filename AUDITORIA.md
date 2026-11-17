# 📋 Informe de Auditoría Técnica — GitHub Analyzer

> Historical snapshot of commit `8610c28`. Findings, version information and test
> counts describe that revision; they have not been revalidated as the current
> project status. Consult the latest CI run for current verification results.

**Fecha de la auditoría:** 17 de noviembre de 2026
**Repositorio:** `github-analyzer`
**Rama analizada:** `master` (commit `8610c28`)
**Entorno de ejecución verificado:** Java 17 / Temurin 21 (macOS / Linux / Windows)

---

## 🌟 1. Resumen Ejecutivo

El proyecto **GitHub Analyzer** es una herramienta de escritorio desarrollada en Java 17 (Swing) orientada a la docencia universitaria. Su propósito es inspeccionar repositorios GitHub de proyectos en equipo (especialmente de estudiantes de programación) y calcular métricas cuantitativas y cualitativas de contribución individual (líneas añadidas/eliminadas, churn en archivos `.java`, commits de merge, detección de código asistido por IA o fuentes externas, y reparto efectivo del trabajo).

### Estado General del Repositorio
- **Madurez técnica y estabilidad:** Muy alta.
- **Suite de pruebas:** **165 tests automatizados** ejecutados con éxito al **100%** (0 fallos, 0 errores, duración ~7 segundos).
- **Diseño arquitectónico:** Excelente separación de responsabilidades entre modelo de dominio inmutable, persistencia atómica, procesamiento de datos en segundo plano y presentación en Swing.
- **Seguridad en tiempo de ejecución:** Protección avanzada contra deserialización insegura ([JEP 290](https://openjdk.org/jeps/290)) y contra inyección de fórmulas CSV.
- **Áreas de mejora identificadas:** Credenciales PAT en almacenamiento local, dependencias binarias vendoreadas en `lib/` sin gestor declarativo, discrepancias de documentación en el recuento de tests y sincronización de versión para el próximo release.

---

## 🔒 2. Seguridad y Privacidad

| Aspecto | Nivel de Riesgo | Estado / Observación | Mitigación Recomendada |
| :--- | :---: | :--- | :--- |
| **Token de GitHub (PAT)** | ⚠️ **Medio** | En `resources/config.properties` existe un token en texto plano (`github.token=ghp_...`). Aunque el fichero está correctamente ignorado por Git en `resources/.gitignore` y nunca se ha subido al historial, un respaldo accidental o copia de directorio lo expondría. | Verificar que el token disponga de permisos mínimos de solo lectura. Considerar permitir la inyección opcional del token por variable de entorno (`GITHUB_TOKEN`). |
| **Privacidad de Estudiantes (PII)** | 🛡️ **Bajo** | Los datos en crudo (`resources/raw-data/*.dat`) y de autoría (`resources/authorship/*.properties`) se almacenan indexados por el hash SHA-256 de las URLs. `resources/repositories.txt` y los exports no se rastrean en Git. | Mantener las reglas vigentes en `resources/.gitignore`. |
| **Deserialización Java (RCE)** | 🛡️ **Bajo** | `DataManager` y `RawDataStore` emplean `ObjectInputFilter` con listas blancas estrictas (`es.deusto.prog3.githubanalyzer.domain.*`, `java.util.*`, `java.lang.*`, `!*`) y límites de bytes/profundidad. | Ninguna requerida; diseño seguro según JEP 290. |
| **Inyección de Fórmulas CSV** | 🛡️ **Bajo** | `DataManager.csvTextCell` escapa celdas que comiencen por `=`, `+`, `-`, `@` anteponiendo `'` y entrecomillando textos. | Ninguna requerida; previene ejecución de fórmulas en hojas de cálculo. |

---

## 🧪 3. Pruebas y Aseguramiento de Calidad (QA)

### Resultados de la Suite Automatizada
Comando ejecutado:
```bash
./scripts/run-tests.sh
```

- **Contenedores de test:** 20 iniciados / 20 exitosos.
- **Tests ejecutados:** **165 tests encontrados / 165 tests exitosos (100%)**.
- **Tests fallidos / abortados / omitidos:** 0.
- **Tiempo de ejecución:** 7.018 ms.

### Pruebas de Layout de Release
Comando ejecutado:
```bash
python3 scripts/check-release-layout.py
```
- **4/4 pruebas superadas:**
  - `test_legacy_root_layout`: OK.
  - `test_missing_expected_jar_rejects_stale_artifact`: OK.
  - `test_scripts_and_dist_layout`: OK.
  - `test_test_failure_is_propagated`: OK.

### Aspectos Destacados de la Suite
1. **Aislamiento Offline:** Empleo de adaptadores y mocks (`OfflinePages`, `OfflineCommitQuery`) para simular respuestas y fallos de la API de GitHub sin consumir rate limit.
2. **Pruebas de GUI en modo headless:** Pruebas de diálogo, refresco y formularios (`MainWindowRefreshTest`, `AuthorshipFormTest`, `ConfigurationFormTest`) sin requerir servidor gráfico activo.
3. **Casos límite cubiertos:** Deduplicación de ramas, preservación de archivos dañados, recálculo local independiente de la API, roles de profesor vs. estudiante, y métricas de churn.

---

## 🏗️ 4. Sistema de Compilación y Dependencias

### Estructura de Construcción
El proyecto no utiliza Maven ni Gradle; en su lugar, utiliza dependencias fijadas en `lib/` junto a scripts portables en `scripts/` y configuración para Eclipse IDE:
- `scripts/build-jar.sh` / `scripts/build-jar.bat`: Genera un runnable "fat JAR" autosuficiente en `dist/` (~4.4 MB) eliminando firmas digitales duplicadas (`META-INF/*.SF`, `*.DSA`, `*.RSA`).
- `scripts/run-tests.sh` / `scripts/run-tests.bat`: Compila y ejecuta los tests con la consola de JUnit Platform.

### Dependencias Vendoreadas (`lib/`)
| Componente | Versión | Tamaño | Licencia |
| :--- | :--- | :--- | :--- |
| `commons-io` | 2.22.0 | 596 KB | Apache-2.0 |
| `commons-lang3` | 3.21.0 | 732 KB | Apache-2.0 |
| `github-api` | 1.330 | 696 KB | MIT |
| `jackson-annotations` | 2.21 | 84 KB | Apache-2.0 |
| `jackson-core` | 2.21.7 | 584 KB | Apache-2.0 |
| `jackson-databind` | 2.21.7 | 1.6 MB | Apache-2.0 |
| `junit-platform-console-standalone` | 6.1.3 | 2.9 MB | EPL-2.0 |

**Observación:** Vendorear los JARs en Git agiliza la importación en Eclipse para estudiantes/profesores sin instalar gestores de paquetes, pero suma ~7.5 MB al árbol Git e impide que herramientas como Dependabot detecten vulnerabilidades automáticamente.

---

## 🔄 5. Integración Continua (CI/CD) y Releases

### Flujos de Trabajo en GitHub Actions
1. **`java-ci.yml` (Test and package):**
   - Ejecuta en cada push y PR hacia `master`.
   - Pasos: `actions/checkout@v6` (fetch-depth: 2), `actions/setup-java@v5` (Temurin 17), verificación de formato (`git diff --check`), validación de layout (`check-release-layout.py`), ejecución de tests (`run-tests.sh`) y empaquetado del JAR (`build-jar.sh`).
2. **`release.yml` (Publish release):**
   - Se activa ante tags de la forma `v*`.
   - Valida que el tag corresponda a un commit con ancestro en `origin/master`.
   - Comprueba que el tag coincida exactamente con la versión declarada en `CITATION.cff`.
   - Publica el release con su suma de verificación SHA-256.

### ⚠️ Hallazgo en la Gestión de Versiones
- El tag actual `v1.3.1` ya existe en el repositorio.
- La rama `master` tiene 12 commits posteriores que introducen nuevas funcionalidades esenciales (PR #5 y PR #6).
- Sin embargo, `CITATION.cff` sigue reflejando:
  ```yaml
  version: "1.3.1"
  date-released: 2026-10-08
  ```
- **Recomendación:** Antes de generar el siguiente tag (ej. `v1.3.2` o `v1.4.0`), se debe actualizar `version` y `date-released` en `CITATION.cff`, o de lo contrario el workflow de release fallará.

---

## 💻 6. Calidad de Código y Arquitectura

### Buenas Prácticas Observadas
- **Concurrencia Responsiva:** La interfaz Swing utiliza `SwingWorker` para trasladar llamadas de red y disco fuera del Event Dispatch Thread (EDT).
- **Control de Cuota de API:**
  - `MAX_THREADS = 4` y `MAX_CONCURRENT_LIST_FILES = 2` mediante `Semaphore`.
  - `MIN_DELAY_BETWEEN_HEAVY_CALLS_MS = 120` para evitar bloqueos por rate-limiting secundario en GitHub.
- **Escritura Atómica en Disco:** Las operaciones de persistencia en `RawDataStore`, `AuthorshipStore` y `DataManager` escriben en ficheros temporales antes de aplicar `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.

### Advertencias del Compilador (`javac -Xlint:all`)
- `RepoStats.java:49, 65` (`[serial]`): Campos declarados como interfaces (`List<UserStats>`, `Map<String, Integer>`) en una clase serializable.
- `MainWindow.java:368` (`[this-escape]`): Advertencia sobre posible escape de `this` antes de la inicialización completa en el constructor por el registro de listeners.
- Advertencias cosméticas de serialización en componentes Swing de la GUI.

---

## 📄 7. Documentación y Trazabilidad

- **Inconsistencia Detectada:** En [docs/ONBOARDING.md](docs/ONBOARDING.md) (Sección 4) se indica:
  > *"Currently 89 tests."*
  La suite cuenta actualmente con **165 tests**. Se aconseja actualizar dicha cifra.
- **Licenciamiento y Avisos:**
  - Código principal bajo licencia **Apache-2.0** ([LICENSE](LICENSE)).
  - [NOTICE](NOTICE) y [docs/THIRD_PARTY_NOTICES.md](docs/THIRD_PARTY_NOTICES.md) detallan con precisión las licencias de terceros y excluyen explícitamente los iconos de Flaticon de la licencia Apache.
  - Encabezados de código fuente declaran explícitamente la asistencia de herramientas de IA durante el desarrollo y auditoría.

---

## 📌 8. Plan de Acción y Recomendaciones Priorizadas

| Prioridad | Tarea | Impacto |
| :---: | :--- | :--- |
| **Alta** | **Actualizar `CITATION.cff`** con la versión siguiente (`1.3.2` o `1.4.0`) y fecha actual previa al próximo tag de Git. | Evita el bloqueo del pipeline `release.yml`. |
| **Media** | **Soporte de autodetección de `JAVA_HOME`** en `scripts/run-tests.sh` y `scripts/build-jar.sh` usando `/usr/libexec/java_home` en macOS. | Evita el error `Unable to locate a Java Runtime` en terminales de macOS. |
| **Baja** | **Corregir el recuento de pruebas** en `docs/ONBOARDING.md` (de 89 a 165). | Mantiene la documentación fiel a la realidad del código. |
| **Baja** | **Considerar soporte de variable de entorno** `GITHUB_TOKEN` como alternativa a `resources/config.properties`. | Mejora la higiene de seguridad de credenciales en entornos compartidos. |
