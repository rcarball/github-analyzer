El proyecto genera estadísticas de actividad de respositorios de GitHub. Permite el acceso a repositorios Públicos o repositorios privados a los que tiene acceso un determinado username.

La clase principal es: **es.deusto.prog3.githubanalyzer.Main**

Tiene dos ficheros de configuración:

**- resources/config.properties** (fichero de configuración del funcionamiento de la aplicación)
  - github.user=_USERNAME_                  # Username para establecer la conexión a GitHub
  - github.token=_TOKEN_                    # Token de acceso a GitHub
  - update.from.github=_<yes|no>_                    # (yes) obtiene la información en tiempo real | (no) trabajan en modo offline
  - repositories.file=_resources/repositories.txt_   # Lista de repositorios GitHub que se analizarán
  - stats.file=_resources/stats.dat_                 # Fichero binario con la información obtenida en la última descarga

**- resources/repositories.txt** (fichero con el listado de repositorios que se analizarán)
