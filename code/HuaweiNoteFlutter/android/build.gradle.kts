allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

val newBuildDir: Directory =
    rootProject.layout.buildDirectory
        .dir("../../build")
        .get()
rootProject.layout.buildDirectory.value(newBuildDir)

subprojects {
    val newSubprojectBuildDir: Directory = newBuildDir.dir(project.name)
    project.layout.buildDirectory.value(newSubprojectBuildDir)
}
subprojects {
    project.evaluationDependsOn(":app")
}

subprojects {
    project.buildscript {
        configurations.all {
            resolutionStrategy {
                force("com.android.tools.build:gradle:8.1.0")
                force("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.22")
            }
        }
    }
    project.configurations.all {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.9.22")
            force("org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.9.22")
            force("androidx.core:core:1.12.0")
            force("androidx.core:core-ktx:1.12.0")
        }
    }
}


tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
