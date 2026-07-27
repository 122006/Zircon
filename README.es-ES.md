# Zircon [![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

<a href="https://github.com/122006/Zircon/releases"><img src="https://img.shields.io/github/release/122006/Zircon.svg?style=flat-square"></a>
<a href="https://plugins.jetbrains.com/plugin/19146-zircon"><img src="https://img.shields.io/jetbrains/plugin/v/19146-zircon.svg?style=flat-square"></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/javase-jdk8-downloads.html"><img src="https://img.shields.io/badge/JDK-8-green.svg" alt="jdk-8" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk11-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-11-green.svg" alt="jdk-11" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-17-green.svg" alt="jdk-17" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk21-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-21-green.svg" alt="jdk-21" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk22-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-22-green.svg" alt="jdk-22" /></a>
<a target="_blank" href="https://www.oracle.com/java/technologies/javase/jdk22-archive-downloads.html"><img src="https://img.shields.io/badge/JDK-23-green.svg" alt="jdk-23" /></a>
-----------------

## Zircon te permite utilizar algunas sintaxis especiales directamente en el código del lenguaje Java

* **Acceso rápido**: Úsalo en proyectos Java existentes, se introduce con tan solo **2** líneas de código.
* **Integración fluida**: Todas las nuevas sintaxis son totalmente compatibles con la sintaxis base de Java 8-23; no es necesario cambiar de lenguaje para mejorar la experiencia de desarrollo.
* **Dependencias seguras**: No depende de librerías de terceros y el resultado de la compilación es un archivo jar normal, sin contagio de dependencias.

----------------

#### Características sintácticas soportadas:

### 1. Métodos de extensión globales

> Extiende libremente la implementación de métodos en código existente. Permite implementar funcionalidades como métodos de nivel superior, sustitución de métodos, etc.<p>
> Especialmente, permite restringir la extensión solo a clases que contengan anotaciones específicas (ej. @Service, @Repository, @Data)

![](others/exmethod_show4.gif)

### 2. Encadenamiento opcional (Optional Chaining)

`String text=XXX.returnNull()?.getText(); // No lanza NullPointerException, sino que devuelve null`

> Simplifica el proceso de acceso a propiedades y métodos de objetos o arrays anidados cuando las propiedades intermedias pueden ser null.
>
> El operador de encadenamiento opcional (`?.`) le permite acceder a propiedades o métodos sin necesidad de comprobaciones explícitas de null. Si cualquier propiedad intermedia en la cadena es null, la expresión se corta (short-circuit) y el resultado se establece como null.
>
> En programación, el "short-circuit" se refiere al comportamiento donde la evaluación de la expresión se detiene inmediatamente al encontrar un valor null mientras se recorre la cadena de propiedades o métodos. En lugar de continuar evaluando, el resultado se establece inmediatamente en null, omitiendo cualquier acceso posterior.
>
> Si el encadenamiento opcional es seguido por una expresión `elvis`, la expresión `elvis` actuará simultáneamente como el valor predeterminado del encadenamiento. Especialmente, para <kbd>sentencias de asignación única</kbd>, si la cadena no se cumple, la ejecución de dicha sentencia se omite directamente.

### 3. Expresión `elvis`

`String text= xxxx.returnNull() ?: "valor predeterminado"; // uso simple`

> Cuando el resultado de la expresión a la izquierda es null, devuelve el valor de la expresión a la derecha.

### 4. Cadenas de plantillas interpoladas (Interpolated Template Strings)

`String text=$"My name is $ID.name ";// uso simple`

`String text=f"My age is ${%02d:ID.age} ";// cadena de plantilla con formato`
> La función de interpolación de cadenas se construye sobre la base de la funcionalidad de formato compuesto, proporcionando una sintaxis más legible y conveniente para incluir los resultados de expresiones en la cadena final.

---------------

1. Soporta todos los proyectos que utilizan el lenguaje java (javac), incluyendo Android, SpringBoot, JavaFX, etc.

2. Soporta Java 8 ~ Java 23.

---------------

### Instrucciones de uso

#### [Cadenas de plantillas interpoladas (Ir al enlace)](mds/README_ZrString.md)

#### [Métodos de extensión globales (Ir al enlace)](mds/README_ZrExMethod.md)

> ¿Cómo definir un método de extensión? [
*Acceso rápido al ejemplo `ExMethodUtil`*](https://github.com/122006/ExMethodUtil/tree/main/impl/src/main/java/zircon/example)

#### [Encadenamiento opcional & expresión `elvis` (Ir al enlace)](mds/README_ZrOptionalChaining.md)

### Introducción del plugin

<details>
  <summary>Proyectos construidos con Gradle (Haga clic para expandir)</summary>

#### Uso del plugin ZrString para introducir dependencias automáticamente

Paso 1. Realice lo siguiente en su archivo `build.gradle` del proyecto raíz:

````
buildscript {
    repositories {
        maven { url 'https://jitpack.io' }
    }
    dependencies {
        classpath 'com.github.122006.Zircon:gradle:3.3.2'
    }
}
````

Versión actual: [![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

Paso 2. En la primera línea del `build.gradle` del módulo que necesite el plugin, introduzca `apply plugin: 'zircon'`.

</details>
<details>
  <summary>Proyectos construidos con Maven (Haga clic para expandir)</summary>
Paso 1. Agregar dependencias

	    <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>javac</artifactId>
            <version>3.3.2</version>
            <scope>provided</scope>
        </dependency>
	    <dependency>
            <groupId>com.github.122006.Zircon</groupId>
            <artifactId>zircon</artifactId>
            <version>3.3.2</version>
        </dependency>

Paso 2. Configurar repositorio jitpack

	    <repositories>
        	<repository>
        	    <id>jitpack.io</id>
        	    <url>https://jitpack.io</url>
        	</repository>
        </repositories>

Versión actual: [![](https://jitpack.io/v/122006/Zircon.svg)](https://jitpack.io/#122006/Zircon)

Paso 3. Configurar parámetros de javac `-Xplugin:ZrExMethod -Xplugin:ZrString`

        <plugin>
          <groupId>org.apache.maven.plugins</groupId>
          <artifactId>maven-compiler-plugin</artifactId>
          <configuration>
            <compilerArgs>
              <arg>-Xplugin:ZrExMethod</arg>
              <arg>-Xplugin:ZrString</arg>
            </compilerArgs>
          </configuration>
        </plugin>

</details>

### Instalación del plugin de IDEA

#### Instalación manual (Recomendado)

1. Haga clic [aquí \[ijplugin.zip\]](ijplugin/build/distributions/ijplugin-4.7.zip) para descargar (o busque el archivo `/ijplugin/build/distributions/ijplugin-xxx.zip` en el directorio).
2. Después de descargar, arrastre el archivo a IDEA para la instalación automática o cárguelo desde una ruta específica en IDEA:
   > Para Windows & Linux - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Icono de engranaje</kbd> > <kbd>
   Install Plugin from Disk...</kbd>\
   > Para Mac - <kbd>IntelliJ IDEA</kbd> > <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Icono de engranaje</kbd> > <kbd>
   Install Plugin from Disk...</kbd>

#### Carga desde el repositorio de plugins de la IDE

Para Windows & Linux - <kbd>File</kbd> > <kbd>Settings</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search
for "Zircon"</kbd> > <kbd>Install Plugin</kbd> > <kbd>Restart IntelliJ IDEA</kbd>

Para Mac - <kbd>IntelliJ IDEA</kbd> > <kbd>Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search
for "Zircon"</kbd> > <kbd>Install Plugin</kbd>  > <kbd>Restart IntelliJ IDEA</kbd>

#### Carga vía web

<a href="https://plugins.jetbrains.com/plugin/19146-zircon">
    <img src="https://user-images.githubusercontent.com/12044174/123105697-94066100-d46a-11eb-9832-338cdf4e0612.png" width="300"/>
</a>

### Otras notas

1. Por favor, mantenga el plugin de IDEA actualizado a la última versión. La revisión del repositorio de plugins puede tener retrasos; se recomienda la instalación manual prioritariamente.

--------------

## ChangeLog

### v3.3.2

1. Corregido el problema donde no se reconocía el encadenamiento opcional al realizar saltos de línea [#17](https://github.com/122006/Zircon/issues/17)
2. Optimización de cadenas de plantillas estilo JSON

<details>
  <summary>Actualizaciones de dependencias históricas</summary>

### v2.2

1. Refactorización del código existente para mejorar el rendimiento de compilación y la extensibilidad.
2. Uso de Gradle para compilar el plugin de IDEA.

### v2.4

1. Soporte para JDK 11 y Android 30.

### v2.5

1. Soporte para el uso de comillas sin escapar dentro de bloques de código internos.

### v2.7

1. Ya no se soporta la sintaxis de usar comillas simples para escapar comillas dobles.
2. Soporte para configurar proyectos usando el plugin de Gradle.
3. Refactorización para soportar JDK 16 y JDK 17.

### v3.0

1. Soporte para métodos de extensión.

### v3.1.2

1. Soporte para llamadas a métodos de extensión en referencias externas dentro de referencias a métodos de miembros.

### v3.1.3

1. Corregido un problema que causaba tiempos de compilación excesivamente largos.

### v3.1.4

1. El plugin de Gradle ahora soporta la introducción mediante el método de ID.

### v3.1.6

1. Corregido un problema de error de resolución en casos especiales donde el nombre del método coincide pero los parámetros difieren.
2. Corregido el problema donde se indicaba una referencia duplicada al usar referencias de métodos al sobrescribir forzosamente la implementación original.

### v3.1.8

1. Corregido el error de build en proyectos construidos con Maven en IDEA.

### v3.2.0

1. Reutilización de tipos de parámetros ya resueltos para aumentar la velocidad de compilación.
2. Corregido un problema raro de puntero incorrecto en clases anónimas de múltiples capas.
3. Ahora, si existen múltiples implementaciones de métodos de extensión coincidentes, se utilizará automáticamente la implementación con la ruta más cercana.

### v3.2.2

1. Optimización de la estructura de dependencias del proyecto.

### v3.2.3

1. Soporte para Java 21 y Java 22.
2. Optimización de la estructura de compilación del proyecto.

### v3.2.6

1. Ahora los métodos de extensión se importan según la lista de imports, corrigiendo problemas ocasionales en tiempo de compilación.
2. Al extender `Class<?>` en métodos de extensión de instancia, se permite omitir `.class`, similar al efecto de un método estático pero obteniendo el tipo real.
3. Optimización de la velocidad de compilación.
4. Adición de la anotación `@ExMethodIDE` para mejorar las características de sugerencia de la IDE.

### v3.3.0

1. Soporte para expresiones `elvis`.
2. Soporte para sintaxis de encadenamiento opcional.
3. Soporte para cadenas de plantillas estilo JSON.

</details>

### Plugin de IDEA 4.8

1. Modificaciones para la revisión del Marketplace de plugins de IntelliJ.

<details>
  <summary>Actualizaciones históricas del plugin de IDEA</summary>

### Plugin de IDEA 2.0

1. Soporte para sugerencias automáticas de formateadores y errores de coincidencia de tipos en `f-string`.
2. Soporte para reconocer y convertir automáticamente cadenas normales en `$-string`.

### Plugin de IDEA 2.1

1. Los caracteres estructurales de las cadenas de plantilla se resaltarán con colores especiales.

### Plugin de IDEA 2.3

1. Corregido el problema donde la inspección anómala de código dejaba de funcionar después de un tiempo de inicio.

### Plugin de IDEA 2.4

1. Soporte para métodos de extensión.
2. Ya no se sugerirá la funcionalidad de cadenas de plantilla en código donde no se haya introducido el proyecto.

### Plugin de IDEA 2.5

1. Optimización de la visualización de métodos de extensión.

### Plugin de IDEA 2.6

1. Optimización de la visualización de métodos de extensión.

### Plugin de IDEA 2.7

1. Soporte relacionado con la importación automática de paquetes para métodos de extensión.

### Plugin de IDEA 2.8

1. Soporte para llamadas a métodos de extensión en referencias externas dentro de referencias a métodos de miembros.

### Plugin de IDEA 2.9

1. Soporte para salto (clic) en referencias de métodos de extensión en versiones de IDEA 203 y superiores. En versiones inferiores a 203, saltará al objeto proxy.

### Plugin de IDEA 3.0

1. Refactorización de los métodos de extensión y las sugerencias automáticas. Ahora soporta inferencia de genéricos de proxy e inferencia de arrays genéricos.

### Plugin de IDEA 3.1

1. Mejora del efecto combinado entre cadenas de plantilla y funciones de extensión. Soporte para importación automática de paquetes al usar funciones de extensión.

### Plugin de IDEA 3.2

1. Corregidos problemas de compatibilidad con la versión 2023.3 de IDEA.
2. Ya no se sugerirán métodos estáticos al autocompletar después de una variable.

### Plugin de IDEA 3.3

1. Corrección de varios problemas.

### Plugin de IDEA 3.4

1. Refuerzo del soporte para genéricos de clases proxy en la función de autocompletado.

### Plugin de IDEA 3.5

1. Refuerzo del soporte para genéricos de clases proxy: optimización del análisis de herencia de genéricos.
2. 

### Plugin de IDEA 3.6

1. Optimizaciones funcionales.

### Plugin de IDEA 3.8

1. Soporte para resolución automática de métodos con el mismo nombre.
2. En caso de conflicto con métodos originales, se utilizará automáticamente el método original.

### Plugin de IDEA 4.1

1. Expansión de la capacidad de anotación de métodos de extensión, soportando las propiedades de anotación `@ExMethod` añadidas en 3.2.5 y la anotación `@ExMethodIDE`.
2. Ahora el rango de detección del plugin se limita al módulo donde el plugin haya sido declarado, y solo proporcionará los métodos de extensión introducidos.

### Plugin de IDEA 4.2

1. Corregido el problema de la versión 4.1 donde no se autocompletaban los métodos de extensión para arrays de tipos primitivos.
2. Adición de mensajes de sugerencia e importaciones automáticas al usar métodos de extensión de tipo cover.

### Plugin de IDEA 4.4

1. Soporte para encadenamiento opcional & `elvis`.

### Plugin de IDEA 4.6

1. Soporte para versiones de IDEA hasta la 2025.3.

### Plugin de IDEA 4.7

1. Ahora, al emparejar métodos de extensión, se juzgarán los genéricos de la cadena de herencia del objeto para emparejar correctamente el método correspondiente.

</details>

## Proyectos Relacionados

### ExMethodUtil

El proyecto [ExMethodUtil](https://github.com/122006/ExMethodUtil) encapsula métodos utilitarios comunes de Java y puede usarse para experimentar o probar la funcionalidad de los métodos de extensión.

> El proyecto principal de Zircon no incluye ningún método de extensión predefinido; puedes introducir este proyecto para experimentar rápidamente con Zircon.

`implementation 'com.github.122006:ExMethodUtil:1.1.8'`
