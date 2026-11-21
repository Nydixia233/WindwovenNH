import org.apache.tools.ant.DirectoryScanner

rootProject.name = "windwovennh"

pluginManagement {
    repositories {
        maven("https://nexus.gtnewhorizons.com/repository/public/") {
            mavenContent {
                includeGroup("com.gtnewhorizons")
                includeGroupByRegex("com\\.gtnewhorizons\\..+")
            }
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("com.gtnewhorizons.gtnhsettingsconvention") version "2.0.34"
}

// Include repository hygiene files in the source ZIP, but still exclude .git itself.
DirectoryScanner.removeDefaultExclude("**/.gitignore")
DirectoryScanner.removeDefaultExclude("**/.gitattributes")
