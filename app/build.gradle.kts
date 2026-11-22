import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

plugins {
    id("com.android.application")
}

android {
    namespace = "com.form.mobile"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.form.mobile"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("generated/form-assets"))
}

// The original project contains ready-to-use dist/ files; no Node toolchain is needed.
// Keep a fixed source revision so upstream edits won't silently change an Android build.
val formRevision = "dfcdb61d9f86832ef1104a97d5141f8592546b07"
val generatedAssets = layout.buildDirectory.dir("generated/form-assets")

val prepareFormAssets = tasks.register("prepareFormAssets") {
    description = "Fetch the upstream Form dist and apply Android-only UI/bridge assets"
    group = "build"
    inputs.property("revision", formRevision)
    inputs.files("src/main/mobile/form-mobile.css", "src/main/mobile/form-mobile.js")
    outputs.dir(generatedAssets)

    doLast {
        val assetsRoot = generatedAssets.get().asFile
        assetsRoot.deleteRecursively()
        val target = File(assetsRoot, "site")
        target.mkdirs()
        val archive = File(temporaryDir, "Form-upstream.zip")
        archive.parentFile.mkdirs()
        val url = URL("https://codeload.github.com/youraipulse/Form/zip/$formRevision")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 30000
            readTimeout = 120000
            setRequestProperty("User-Agent", "FormMobile-Android-Build")
            instanceFollowRedirects = true
        }
        try {
            if (connection.responseCode != 200) {
                throw GradleException("Cannot download Form revision: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input -> archive.outputStream().use(input::copyTo) }
        } finally {
            connection.disconnect()
        }

        var files = 0
        ZipInputStream(archive.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val marker = "/dist/"
                val pos = entry.name.indexOf(marker)
                if (pos >= 0) {
                    val relativePath = entry.name.substring(pos + marker.length)
                    if (relativePath.isNotBlank()) {
                        val destination = File(target, relativePath)
                        if (!destination.canonicalPath.startsWith(target.canonicalPath + File.separator)) {
                            throw GradleException("Unsafe ZIP path: $relativePath")
                        }
                        if (entry.isDirectory) {
                            destination.mkdirs()
                        } else {
                            destination.parentFile.mkdirs()
                            destination.outputStream().use { zip.copyTo(it) }
                            files++
                        }
                    }
                }
                zip.closeEntry()
            }
        }
        archive.delete()
        if (files < 8 || !File(target, "index.html").exists()) {
            throw GradleException("Upstream Form archive has no usable dist directory")
        }

        file("src/main/mobile/form-mobile.css").copyTo(File(target, "form-mobile.css"), overwrite = true)
        file("src/main/mobile/form-mobile.js").copyTo(File(target, "form-mobile.js"), overwrite = true)
        val index = File(target, "index.html")
        var html = index.readText(Charsets.UTF_8)
        html = html.replace(
            "width=device-width,initial-scale=1",
            "width=device-width,initial-scale=1,viewport-fit=cover"
        )
        html = html.replace("</head>", "<link rel=\"stylesheet\" href=\"/form-mobile.css\"></head>")
        html = html.replace("</body>", "<script src=\"/form-mobile.js\"></script></body>")
        index.writeText(html, Charsets.UTF_8)
        println("Prepared Form Android assets: $files original files + mobile overrides")
    }
}

tasks.configureEach {
    // Android Gradle plugin creates these tasks after configuration.
    if (name.startsWith("merge") && name.endsWith("Assets")) {
        dependsOn(prepareFormAssets)
    }
}
