# 📊 GitHub Analyzer (enfoque docente)

Aplicación Java (Swing) para analizar actividad en repositorios de GitHub de proyectos en equipo (p. ej., alumnado). Soporta repositorios **públicos** y **privados** (si el token tiene permisos).

> 🧭 Enfoque: estas métricas están pensadas para estimar **práctica real de programación** (especialmente en Java) en un contexto de aprendizaje. Por ello, algunas cifras **no coinciden** con GitHub en *Insights → Contributors* (ver “📐 Significado de las estadísticas”).

---

## ✨ Qué hace la aplicación

- 🌳 Muestra un **árbol** con los repositorios analizados.
- 🧾 Al seleccionar un repo, presenta **estadísticas generales** del repositorio.
- 👥 Muestra una **tabla por persona** con métricas de commits y líneas (centradas en `.java`).
- 🧩 Incluye un resumen de **tipos de fichero** encontrados.
- 🔄 Permite **refrescar** los datos desde GitHub o trabajar en **modo offline** con caché.

---

## 🧑‍🏫 Idea docente (por qué estas métricas)

En grupos con dominio limitado de Git, sin pruebas automatizadas y con poca documentación/diseño, suele ser útil medir:

- **Commits Java (sin merges)** → frecuencia de trabajo real en código
- **Churn Java (añadidas + borradas)** → volumen de edición efectiva
- **Net Java (añadidas − borradas)** → crecimiento neto (opcional para interpretar)

Estas métricas son **indicadores**: ayudan a orientar la revisión y a detectar casos atípicos, pero no sustituyen a la valoración cualitativa.

---

## 🚀 Cómo ejecutar

### Requisitos
- ☕ Java 17+ (recomendado)
- 🗂️ Las librerías están incluidas en una carpeta `lib/`
- 🔑 Token de GitHub

### Clase principal
- `es.deusto.prog3.githubanalyzer.Main`

---

## ⚙️ Configuración

La aplicación usa dos ficheros principales:

### 1) `resources/config.properties`

```properties
github.user=_USERNAME_              # Username (informativo)
github.token=_TOKEN_                # Token de acceso (recomendado para repos privados)
update.from.github=_<yes|no>_       # yes: descarga online | no: modo offline (usa stats.dat)
repositories.file=resources/repositories.txt  # Lista de repos a analizar
stats.file=resources/stats.dat      # Caché binaria de la última descarga
```

### 2) `resources/repositories.txt`

Un repositorio por línea:

```txt
https://github.com/OWNER/REPO1
https://github.com/OWNER/REPO2
```

---

## 🔐 Token de GitHub

Para repos privados o para evitar limitaciones por refrescos repetidos, usa un token con permisos de lectura sobre los repositorios.

> 🧠 Consejo práctico: si refrescas muchas veces seguidas (p. ej. 20 repos), GitHub puede limitar temporalmente las peticiones. En ese caso, utiliza el modo offline (`update.from.github=no`) y vuelve a refrescar más tarde.

---

## 📐 Significado de las estadísticas

### 🧾 Estadísticas generales del repositorio

- **🧱 Total commits (unique)**  
  Número de commits **únicos** detectados en el repositorio (deduplicados por SHA) considerando el historial analizado (incluyendo todas las ramas conocidas).  
  ✅ Útil como indicador de actividad global.  
  ⚠️ No equivale a “commits Java por persona”, porque esa métrica es distinta (ver tabla).

- **📅 Creation date**  
  Fecha de creación del repositorio en GitHub.

- **⏱️ First commit / Last commit**  
  Primer y último commit detectados en el historial analizado. Sirven para estimar el periodo real de trabajo.

- **📄 Total lines of code**  
  Número de líneas existentes en ficheros **`.java`** (lectura del contenido).  
  📌 Es una “foto” del código actual, no una medida directa de esfuerzo.

- **🔁 Java churn (added + deleted)**  
  Total de líneas **añadidas + borradas** en `.java` (a partir de commits analizados, excluyendo merge commits).  
  ✅ Métrica útil como proxy de edición real.  
  ⚠️ Puede inflarse con pegados masivos, formateos o generación automática.

- **🔗 External references**  
  Nº de coincidencias en `.java` de patrones: `IAG` o `FUENTE-EXTERNA`.  
  📌 Señal para identificar referencias externas o código creado con IA Generativa.

---

### 👤 Tabla por persona (colaboradores/autores)

> Identidad: cuando GitHub lo permite, se usa el **login**; si no, se usa información de autoría del commit.

- **✅ Java commits**  
  Número de commits (**excluyendo merges**) que modifican al menos un fichero `.java`.  
  🎯 En este proyecto se interpreta como “commits de trabajo real”.

- **➕ Java added / ➖ Java deleted**  
  Líneas añadidas/borradas en `.java` (sumadas sobre commits no-merge).

- **🔁 Java churn**  
  `added + deleted` en `.java`.  
  ✅ Se usa para valorar la contribución relativa y evitar sesgos (por ejemplo, solo medir añadidas penaliza a quien corrige borrando).

- **📊 % Java churn**  
  Proporción del churn total del repo atribuida a esa persona.  
  📌 Útil para comparar contribución dentro del equipo.

- **🧩 Java files**  
  Nº de ficheros `.java` distintos modificados por la persona.  
  📌 Ayuda a distinguir “intervención en varias partes” vs “trabajo localizado”.

- **📅 First / Last commit (user)**  
  Primera/última fecha de commit no-merge considerada para esa persona.

---

## 🧑‍🏫 Ejemplo de interpretación para el profesorado (rápida y práctica)

> Objetivo: orientar la revisión y detectar casos a revisar, no “poner nota automática”.

- ✅ **Contribución equilibrada**: varios miembros con % churn similar y commits Java repartidos → el trabajo suele estar más distribuido.
- ⚠️ **Un “motor” del equipo**: 1 persona con >50–60% del churn y muchos commits Java → probablemente ha llevado el peso; revisar reparto de tareas y autoría.
- ⚠️ **Aporte mínimo**: churn muy bajo (≈0) o commits Java casi nulos → revisar historial, comunicación del equipo y evidencia adicional (issues, commits, explicación oral).
- 🧠 **Patrón típico de IA/pegado**: churn muy alto con muy pocos commits Java (p. ej. 2 commits y 2000 líneas) → pedir defensa: explicación del código, trazado y preguntas de comprensión.
- 🔁 **Trabajo “de corrección”**: deleted alto y churn alto, pero net bajo → puede ser limpieza/corrección; comprobar si también hay commits Java sostenidos y cambios distribuidos.
- ⏱️ **Ritmo irregular**: casi todo el churn en los últimos días → suele indicar acumulación y riesgo de baja comprensión; útil para planificar el examen individual.

---

## 🧠 Limitaciones (para evitar malentendidos)

- Estas métricas no miden calidad directamente (correctitud, diseño, estilo).
- Cambios de formato o grandes pegados pueden inflar churn.
- Los commits pueden variar según hábitos (micro-commits vs commits grandes).
- Si GitHub limita peticiones (rate limit), el refresco online puede tardar o fallar; usa el modo offline si es necesario.

---

## 📜 Licencia

Este proyecto se distribuye bajo la **MIT License**.  
Más información: https://opensource.org/license/mit/