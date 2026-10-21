pluginManagement {
	repositories {
		gradlePluginPortal()
		mavenCentral()
	}
}

rootProject.name = "backend"

include(":slip-image-core")
project(":slip-image-core").projectDir = file("modules/slip-image-core")
