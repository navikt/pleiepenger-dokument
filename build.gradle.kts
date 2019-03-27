import org.gradle.internal.impldep.org.fusesource.jansi.AnsiRenderer.test
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktorVersion = ext.get("ktorVersion").toString()

val dusseldorfKtorVersion = "1.1.3.ab74601"
val wiremockVersion = "2.19.0"
val amazonawsVersion = "1.11.526"
val slf4jVersion = "1.7.26"
val tikaVersion = "1.20"

val mainClass = "no.nav.helse.PleiepengerDokumentKt"

plugins {
    kotlin("jvm") version "1.3.21"
}

buildscript {
    apply("https://raw.githubusercontent.com/navikt/dusseldorf-ktor/9a0939d9e7858e46df9e1e22602e1e1d8b342de5/gradle/dusseldorf-ktor.gradle.kts")
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.3.21")
    }
}

dependencies {
    // Server
    compile ( "no.nav.helse:dusseldorf-ktor-core:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-jackson:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-metrics:$dusseldorfKtorVersion")
    compile ( "no.nav.helse:dusseldorf-ktor-health:$dusseldorfKtorVersion")
    compile("io.ktor:ktor-auth-jwt:$ktorVersion")

    // Client
    compile ( "no.nav.helse:dusseldorf-ktor-client:$dusseldorfKtorVersion")
    compile ("io.ktor:ktor-client-json-jvm:$ktorVersion")
    compile ("io.ktor:ktor-client-jackson:$ktorVersion")

    // Lagring
    compile("com.amazonaws:aws-java-sdk-s3:$amazonawsVersion")
    compile("org.slf4j:jcl-over-slf4j:$slf4jVersion")

    // Sjekke dokumenter
    compile("org.apache.tika:tika-core:$tikaVersion")

    // Test
    testCompile ("com.github.tomakehurst:wiremock:$wiremockVersion")
    testCompile("io.ktor:ktor-server-test-host:$ktorVersion") {
        exclude(group = "org.eclipse.jetty")
    }
    testCompile ("com.nimbusds:oauth2-oidc-sdk:5.56")
    testCompile("org.testcontainers:localstack:1.10.6")
}

repositories {
    maven("https://dl.bintray.com/kotlin/ktor")
    maven("https://kotlin.bintray.com/kotlinx")
    maven("http://packages.confluent.io/maven/")

    jcenter()
    mavenLocal()
    mavenCentral()
}


java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}


tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<KotlinCompile>("compileTestKotlin") {
    kotlinOptions.jvmTarget = "1.8"
}

tasks.named<Jar>("jar") {
    baseName = "app"

    manifest {
        attributes["Main-Class"] = mainClass
        attributes["Class-Path"] = configurations["compile"].map {
            it.name
        }.joinToString(separator = " ")
    }

    configurations["compile"].forEach {
        val file = File("$buildDir/libs/${it.name}")
        if (!file.exists())
            it.copyTo(file)
    }
}

tasks.withType<Wrapper> {
    gradleVersion = "5.2.1"
}
