Upgrade to Java 21

This project has been updated to target Java 21. Follow these steps to update developer machines and CI.

1. Install JDK 21
   - Recommended: Eclipse Temurin / Adoptium or Oracle JDK 21.
   - Windows: download and install the JDK, then set `JAVA_HOME`.

2. Set `JAVA_HOME` (PowerShell example):
```
setx JAVA_HOME "C:\\Program Files\\Java\\jdk-21" ; $env:JAVA_HOME = "C:\\Program Files\\Java\\jdk-21" ; $env:PATH = "$env:JAVA_HOME\\bin;$env:PATH"
```

3. Confirm Java version:
```
java -version
mvn -version
```

4. CI
   - Update your CI runner to use Java 21 (or newer) to match the `--release 21` compile target.

6. Vosk model path
   - You can set the Vosk model path using the `VOSK_MODEL_PATH` environment variable or the system property `-Dvosk.model.path=...` when running with Maven.
   - Example (PowerShell):
```
setx VOSK_MODEL_PATH "C:\\path\\to\\vosk-model"
$env:VOSK_MODEL_PATH = "C:\\path\\to\\vosk-model"
mvn exec:java
```

5. Notes
   - The POM now sets `maven.compiler.source`/`target` to `21` and configures the `maven-compiler-plugin` with `<release>21</release>`.
   - Building with a newer JDK (e.g., 25) is fine when `--release 21` is used, but CI should still use JDK 21 to match runtime expectations.

If you want, I can also add a `toolchains.xml` for stricter JDK selection in CI/developers. Let me know.