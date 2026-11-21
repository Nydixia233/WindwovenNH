plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

version = providers.gradleProperty("windwovenVersion").get()
extra["modVersion"] = version.toString()

tasks.named<Jar>("jar") {
    from("LICENSE") { into("META-INF") }
}

tasks.withType<AbstractArchiveTask>().configureEach {
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

val qa = sourceSets.create("qa") {
    compileClasspath += sourceSets.main.get().compileClasspath
}
val qaInput = tasks.register<org.gradle.jvm.tasks.Jar>("qaInput") {
    from(qa.output)
    archiveClassifier.set("qa-input")
}
val production = tasks.named<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("reobfJar")
tasks.register<com.gtnewhorizons.retrofuturagradle.mcp.ReobfuscatedJar>("qaJar") {
    setInputJarFromTask(qaInput)
    val settings = production.get()
    mcVersion.set(settings.mcVersion)
    srg.set(settings.srg)
    fieldCsv.set(settings.fieldCsv)
    methodCsv.set(settings.methodCsv)
    exceptorCfg.set(settings.exceptorCfg)
    recompMcJar.set(settings.recompMcJar)
    extraSrgEntries.set(emptyList())
    referenceClasspath.from(settings.referenceClasspath)
    destinationDirectory.set(layout.buildDirectory.dir("qa"))
    archiveBaseName.set("windwovennh-fixture")
}

// Offline reference, fitter and comparison tools. No Minecraft or runtime distribution dependency.
val reference = sourceSets.create("reference") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += sourceSets.main.get().output
}
tasks.register<JavaExec>("referenceLab") {
    group = "verification"
    classpath = reference.runtimeClasspath
    mainClass.set("io.github.windwovennh.cloth.CalibrationLab")
    args(providers.gradleProperty("calibrationMode").orElse("verify").get(),
        layout.buildDirectory.dir("reference/windwovennh/${project.version}").get().asFile.absolutePath,
        providers.gradleProperty("baselineJar").orElse("build/libs/trailcloth-0.2.1.jar").get())
}

// The tests are standalone Java executables; no extra testing libraries are needed.
val clothTest = tasks.register<JavaExec>("clothTest") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.windwovennh.cloth.ClothChecks")
}
val configTest = tasks.register<JavaExec>("configTest") {
    group = "verification"
    dependsOn(tasks.testClasses)
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("io.github.windwovennh.LegacyConfigChecks")
}
tasks.test { enabled = false }
tasks.check { dependsOn(clothTest, configTest) }

tasks.register<JavaExec>("renameParity") {
    group = "verification"
    dependsOn(production)
    classpath = reference.runtimeClasspath
    mainClass.set("io.github.windwovennh.cloth.RenameParity")
    args(providers.gradleProperty("parityBaselineJar").orElse("build/libs/trailcloth-0.2.2.jar").get(),
        production.get().archiveFile.get().asFile.absolutePath)
}

// Standalone rebuildable source archive, with no generated files or game assets.
tasks.register<Zip>("sourceBundle") {
    group = "distribution"
    archiveFileName.set("windwovennh-${project.version}-source.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    setIncludeEmptyDirs(false)
    into("WindwovenNH-${project.version}") {
        from(rootDir) {
            include("src/**", "gradle/**", "tools/*.ps1", "tools/*.py", "licenses/**", "gradle.properties",
                "settings.gradle.kts", "build.gradle.kts", "gradlew", "gradlew.bat",
                ".java-version", "LICENSE", "*.md")
            filesMatching("gradlew") { permissions { unix("755") } }
        }
        // Gradle's directory tree excludes VCS metadata by default. These two files are public.
        from(files(".gitignore", ".gitattributes"))
    }
}
