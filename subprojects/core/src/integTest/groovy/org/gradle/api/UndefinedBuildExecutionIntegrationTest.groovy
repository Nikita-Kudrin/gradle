/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile
import spock.lang.Unroll

class UndefinedBuildExecutionIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
    }

    @Unroll
    def "fails when attempting to execute tasks #tasks in directory with no settings or build file"() {
        when:
        fails(*tasks)

        then:
        isEmpty(testDirectory)
        failure.assertHasDescription("Directory '$testDirectory' does not contain a Gradle build.")
        failure.assertHasResolutions(
            "Run gradle init to create a new Gradle build in this directory.",
            "Run with --info or --debug option to get more log output.") // Don't suggest running with --scan for a missing build

        where:
        tasks << [["tasks"], ["unknown"]]
    }

    // Documents existing behaviour, not desired behaviour
    def "allows an included build with no settings or build file"() {
        given:
        settingsFile << """
            includeBuild("empty")
            includeBuild("lib")
        """
        def dir = file("empty")
        dir.createDir()
        file("lib/build.gradle") << """
            plugins {
                id("java-library")
            }
            group = "lib"
        """
        buildFile << """
            plugins {
                id("java-library")
            }
            dependencies { implementation "lib:lib:1.2" }
        """

        when:
        succeeds("build")

        then:
        noExceptionThrown()
    }

    def "fails when target of GradleBuild task has no settings or build file"() {
        given:
        buildFile << """
            task build(type: GradleBuild) {
                dir = 'empty'
                tasks = ['tasks']
            }
        """
        def dir = file("empty")
        dir.createDir()

        when:
        fails("build")

        then:
        isEmpty(dir)
        failure.assertHasDescription("Execution failed for task ':build'.")
        failure.assertHasCause("Directory '$dir' does not contain a Gradle build.")
    }

    def "fails when user home directory is used and Gradle has not been run before"() {
        when:
        // the default, if running from user home dir
        def gradleUserHomeDir = file(".gradle")
        executer.withGradleUserHomeDir(gradleUserHomeDir)
        fails("tasks")

        then:
        isEmpty(testDirectory)
        failure.assertHasDescription("Directory '$testDirectory' does not contain a Gradle build.")
    }

    def "does not delete an existing .gradle directory"() {
        given:
        def textFile = file(".gradle/thing.txt")
        textFile << "content"

        when:
        fails("tasks")

        then:
        testDirectory.assertHasDescendants(".gradle/thing.txt")
        textFile.assertIsFile()
        textFile.text == "content"
    }

    @Unroll
    def "does not treat build as undefined when root #fileName is present but settings file is not"() {
        when:
        file(fileName) << """
            tasks.register("build")
        """
        succeeds("build")

        then:
        noExceptionThrown()

        where:
        fileName << ["build.gradle", "build.gradle.kts"]
    }

    @Unroll
    def "does not treat build as undefined when root build file is not present but #fileName is"() {
        when:
        settingsFile << """
            include("child")
        """
        file("child/build.gradle") << """
            task build
        """
        succeeds("tasks")

        then:
        noExceptionThrown()

        where:
        fileName << ["settings.gradle", "settings.gradle.kts"]
    }

    def "does not treat buildSrc with no build or settings file as undefined build"() {
        given:
        settingsFile.touch()
        file("buildSrc/src/main/groovy/Dummy.groovy") << "class Dummy {}"

        expect:
        succeeds("tasks") // without deprecation warning
        result.assertTaskExecuted(":buildSrc:jar")

        executer.withArguments("-p", "buildSrc")
        succeeds("tasks")
    }

    def "treats empty buildSrc as undefined build"() {
        given:
        settingsFile.touch()
        file("buildSrc").createDir()

        expect:
        succeeds("tasks")

        executer.withArguments("-p", "buildSrc")
        fails("tasks")
    }

    @Unroll
    def "does not fail when executing #flag in undefined build"() {
        when:
        executer.requireDaemon().requireIsolatedDaemons()
        succeeds(flag)

        then:
        isEmpty(testDirectory)

        where:
        flag << ["--version", "--help", "-h", "-?", "--help"]
    }

    void isEmpty(TestFile dir) {
        dir.assertIsDir()
        assert dir.listFiles().size() == 0
    }
}
