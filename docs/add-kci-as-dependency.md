## Adding CustomItems as dependency to your own project

### Publishing this fork on JitPack

`jitpack.yml` selects Java 25 and installs the Gradle profile from
`.github/jitpack/`. It publishes `bit-helper`, `shared-code`, `plug-in`,
`kci-nms`, and `custom-recipes` using Gradle's built-in `maven-publish` plugin.
Each module includes a library JAR, sources, and dependency metadata.
The Paper server plugin is built separately with the normal
`:plug-in:shadowJar` task.

The JitPack profile resolves Central releases through a Maven Central mirror
to avoid HTTP 429 failures on shared build infrastructure. It does not query
direct Central, including for libraries hosted in third-party repositories.
It does not load Shadow, which is unnecessary for these library publications.
The build also registers its checkout as a safe Git directory.

When consuming this fork, replace `knokko` in the coordinates below with
`weezard12` and choose the branch or commit containing your changes, for example
`testing-SNAPSHOT`.

See [JitPack's Gradle publishing guide](https://docs.jitpack.io/building/)
and the [Central mirror information](https://storage-download.googleapis.com/maven-central/index.html).

### Without build system
If you don't like build systems, you can simply download CustomItems.jar from Spigot or BukkitDev
and add it to your classpath.

### Gradle
Add the Jitpack maven repository:
```
repositories {
    // Your current repositories...
    maven { url 'https://jitpack.io' }
}
```
And add the modules you want to your dependencies:
```
dependencies {
    // Your current dependencies...
    
    // You probably want shared-code and plug-in
    compileOnly 'com.github.knokko.custom-items-gradle:shared-code:master-SNAPSHOT'
    compileOnly 'com.github.knokko.custom-items-gradle:plug-in:master-SNAPSHOT'
    
    // You may also want to have e.g. the Editor
    compileOnly 'com.github.knokko.custom-items-gradle:editor:master-SNAPSHOT'
}
```

### Maven
Add the Jitpack maven repository:
```
<repositories>
    # Your current repositories...
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```
And add the modules you want to your dependencies:
```
# Your current dependencies...
<dependency>
    <groupId>com.github.knokko.custom-items-gradle</groupId>
    <artifactId>shared-code</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
<dependency>
    <groupId>com.github.knokko.custom-items-gradle</groupId>
    <artifactId>plug-in</artifactId>
    <version>master-SNAPSHOT</version>
</dependency>
```
