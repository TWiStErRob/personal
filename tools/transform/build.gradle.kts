plugins {
	id("org.gradle.java")
	id("org.gradle.java-test-fixtures")
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8
	targetCompatibility = JavaVersion.VERSION_1_8
	registerFeature("sharedIntegrationTests") {
		usingSourceSet(sourceSets.create("integrationTest"))
		disablePublication()
	}
}

dependencies {
	integrationTestImplementation(project)
	integrationTestImplementation(testFixtures(project))
	integrationTestImplementation(libs.test.junit4)
}

// TODO Not using `org.gradle.test-report-aggregation`, because https://github.com/gradle/gradle/issues/24272.
val integrationTestAggregateTestReport by tasks.registering(TestReport::class) {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Generates aggregated test report for all integration tests."
	// testResults.from(testing.suites*.targets*.testTask) added later when configuring the test suites.
	destinationDirectory.convention(java.testReportDir.dir("integration-tests/aggregated-results"))
}

val integrationTests by tasks.registering {
	// dependsOn("integration*Test") added later when configuring the test suites.
	finalizedBy(integrationTestAggregateTestReport)
}

tasks.check.configure {
	dependsOn(integrationTests)
	dependsOn(integrationTestAggregateTestReport)
}

@Suppress("UnstableApiUsage") // JvmTestSuite and all methods.
testing.suites {
	named<JvmTestSuite>("test").configure {
		// Keep defaults.
	}
	registerIntegrationTest("java") {
		dependencies {
			// None, use default from JRE bootclasspath.
		}
	}
	registerIntegrationTest("xalan") {
		dependencies {
			implementation(libs.xml.xalan)
			implementation(libs.xml.xalan.serializer)
		}
	}
	registerIntegrationTest("saxon") {
		dependencies {
			implementation(libs.xml.saxon)
		}
	}
}

@Suppress("UnstableApiUsage") // TestSuite and all methods.
fun NamedDomainObjectContainerScope<TestSuite>.registerIntegrationTest(
	name: String,
	configure: JvmTestSuite.() -> Unit
) {
	register<JvmTestSuite>("${name}IntegrationTest") {
		testType.set("${TestSuiteType.INTEGRATION_TEST}-${name}")
		useJUnit(libs.versions.test.junit4)
		dependencies {
			implementation(project())
			implementation(testFixtures(project()))
			runtimeOnly(sharedIntegrationTests(project()))
		}
		targets.configureEach {
			integrationTests.configure { dependsOn(testTask) }
			integrationTestAggregateTestReport.configure { testResults.from(testTask) }
			testTask.configure {
				enableAssertions = true
				//val dataXml = file("${rootDir}/../temp/test/data.xml")
				systemProperty("net.twisterrob.inventory.transform.name", name)
				fun Test.inputFile(prop: String, file: File) {
					inputs.file(file)
						.withPropertyName(prop)
						.withPathSensitivity(PathSensitivity.RELATIVE)
					systemProperty(prop, file)
				}
				inputFile(
					"net.twisterrob.inventory.transform.xml",
					project(":android:backup").file("src/main/assets/demo.xml")
				)
				inputFile(
					"net.twisterrob.inventory.transform.xsd",
					project(":android:backup").file("src/main/assets/data.xml.xsd")
				)
				inputFile(
					"net.twisterrob.inventory.transform.xslt.html",
					project(":android:backup").file("src/main/assets/data.html.xslt")
				)
				inputFile(
					"net.twisterrob.inventory.transform.xslt.csv",
					project(":android:backup").file("src/main/assets/data.csv.xslt")
				)
				val transformDir = layout.buildDirectory.file("transformed")
				outputs.dir(transformDir).withPropertyName("transformDir")
				systemProperty(
					"net.twisterrob.inventory.transform.output",
					transformDir.get().asFile
				)
				testClassesDirs = files(
					testClassesDirs, // Keep original.
					sourceSets["integrationTest"].output.classesDirs,
				)
			}
		}
		configure()
	}
}

fun DependencyHandler.integrationTestImplementation(dependencyNotation: Any): Dependency? =
	add("integrationTestImplementation", dependencyNotation)

fun sharedIntegrationTests(dependency: ProjectDependency): ProjectDependency {
	dependency.capabilities {
		// See org.gradle.internal.component.external.model.ProjectDerivedCapability
		requireCapability("${dependency.group}:${dependency.name}-shared-integration-tests")
	}
	return dependency
}
