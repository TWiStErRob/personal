package net.twisterrob.inventory.build

import net.twisterrob.gradle.android.androidComponents
import net.twisterrob.inventory.build.dsl.android
import net.twisterrob.inventory.build.dsl.autoNamespace

plugins {
	id("net.twisterrob.inventory.build.hilt")
}

dependencies {
	"implementation"(platform("net.twisterrob.inventory.build:platform"))
	"testImplementation"(platform("net.twisterrob.inventory.build:platform"))
	"androidTestImplementation"(platform("net.twisterrob.inventory.build:platform"))
}

android {
	namespace = project.autoNamespace
	compileSdk = 34
	defaultConfig {
		minSdk = 21
	}
	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_1_7
		targetCompatibility = JavaVersion.VERSION_1_8
		tasks.withType<JavaCompile>().configureEach {
			// warning: [options] source value 7 is obsolete and will be removed in a future release
			options.compilerArgs.add("-Xlint:-options")
		}
	}
	lint {
		checkReleaseBuilds = false
		baseline = rootDir.resolve("config/lint/lint-baseline-${project.name}.xml")
		lintConfig = rootDir.resolve("config/lint/lint.xml")

		/*
		 * Work around
		 * > CannotEnableHidden: Issue Already Disabled
		 * > ../../../android: Issue StopShip was configured with severity fatal in android,
		 * > but was not enabled (or was disabled) in library annotations
		 * > Any issues that are specifically disabled in a library cannot be re-enabled in a dependent project.
		 * > To fix this you need to also enable the issue in the library project.
		 * Strangely even though both projects have my plugin applied which adds the fatal.
		 */
		fatal.remove("StopShip")

		// TODEL https://issuetracker.google.com/issues/170658134
		androidComponents.finalizeDsl {
			if (it.buildFeatures.viewBinding /*nullable*/ == true || project.path == ":android") {
				it.lint.disable += "UnusedIds"
			}
		}
	}
}
