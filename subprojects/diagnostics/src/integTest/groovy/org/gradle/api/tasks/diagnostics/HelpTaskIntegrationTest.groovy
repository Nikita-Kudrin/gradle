/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.TestResources
import org.gradle.util.GradleVersion
import org.junit.Rule
import spock.lang.Unroll

class HelpTaskIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    public final TestResources resources = new TestResources(temporaryFolder)
    def version = GradleVersion.current().version

    @Unroll
    def "shows help message when tasks #tasks run in a directory with no build definition present"() {
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()

        when:
        run(*tasks)

        then:
        output.contains """
> Task :help

Welcome to Gradle ${version}.

Directory '$testDirectory' does not contain a Gradle build.

To create a new build in this directory, run gradle init

For more detail on the 'init' task, see https://docs.gradle.org/$version/userguide/build_init_plugin.html

For more detail on creating a Gradle build, see https://docs.gradle.org/$version/userguide/tutorial_using_tasks.html

To see a list of command-line options, run gradle --help

For more detail on using Gradle, see https://docs.gradle.org/$version/userguide/command_line_interface.html

For troubleshooting, visit https://help.gradle.org

BUILD SUCCESSFUL"""

        and:
        // Directory is still empty
        testDirectory.list().size() == 0

        and:
        // Uses a directory under the user home for those things that happen to need a cache directory
        executer.gradleUserHomeDir.file("undefined-build").assertIsDir()

        where:
        tasks << [["help"], [], [":help"]]
    }

    def "shows help message when run in a directory under the root directory of another build"() {
        given:
        settingsFile.createFile()
        def sub = file("sub").createDir()

        when:
        executer.inDirectory(sub)
        run "help"

        then:
        output.contains """
> Task :help

Welcome to Gradle ${version}.

Directory '$sub' does not contain a Gradle build.
"""

        and:
        // Directory is still empty
        sub.list().size() == 0
    }

    def "shows help message when run in users home directory"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()

        // the default, if running from user home dir
        def gradleUserHomeDir = file(".gradle")
        executer.withGradleUserHomeDir(gradleUserHomeDir)

        when:
        run "help"

        then:
        output.contains """
> Task :help

Welcome to Gradle ${version}.

Directory '$testDirectory' does not contain a Gradle build.
"""

        and:
        // Uses a directory under the user home for those things that happen to need a cache directory
        gradleUserHomeDir.file("undefined-build").assertIsDir()
    }

    def "shows help message when build is defined using build script only"() {
        given:
        useTestDirectoryThatIsNotEmbeddedInAnotherBuild()
        buildFile.createFile()

        when:
        run "help"

        then:
        output.contains """
> Task :help

Welcome to Gradle ${version}.

To run a build, run gradle <task> ...
"""
    }

    def "shows help message when run from subproject directory"() {
        given:
        settingsFile << """
            include("sub")
        """
        def sub = file("sub").createDir()

        when:
        executer.inDirectory(sub)
        run "help"

        then:
        output.contains """
> Task :sub:help

Welcome to Gradle ${version}.

To run a build, run gradle <task> ...
"""
    }

    def "shows basic welcome message for current project only"() {
        given:
        settingsFile << "include 'a', 'b', 'c'"

        when:
        run "help"

        then:
        result.groupedOutput.taskCount == 1
        output.contains """
> Task :help

Welcome to Gradle ${version}.

To run a build, run gradle <task> ...

To see a list of available tasks, run gradle tasks

To see more detail about a task, run gradle help --task <task>

To see a list of command-line options, run gradle --help

For more detail on using Gradle, see https://docs.gradle.org/$version/userguide/command_line_interface.html

For troubleshooting, visit https://help.gradle.org

BUILD SUCCESSFUL"""
    }

    def "can print help for implicit tasks"() {
        when:
        run "help", "--task", "dependencies"
        then:
        output.contains """Detailed task information for dependencies

Path
     :dependencies

Type
     DependencyReportTask (org.gradle.api.tasks.diagnostics.DependencyReportTask)

Options
     --configuration     The configuration to generate the report for.

Description
     Displays all dependencies declared in root project '${testDirectory.getName()}'.

Group
     help

BUILD SUCCESSFUL"""
    }

    def "can print help for placeholder added tasks"() {
        when:
        run "help", "--task", "help"
        then:
        output.contains """Detailed task information for help

Path
     :help

Type
     Help (org.gradle.configuration.Help)

Options
     --task     The task to show help for.

Description
     Displays a help message.

Group
     help

BUILD SUCCESSFUL"""
    }

    def "help for tasks same type different descriptions"() {
        setup:
        settingsFile.text = """
include ":someproj"
"""
        buildFile.text = """
        task hello {
            description = "hello task from root"
        }
        project(":someproj"){
            task hello {
                description = "hello task from someproj"
            }
        }
"""
        when:
        run "help", "--task", "hello"
        then:
        output.contains """Detailed task information for hello

Paths
     :hello
     :someproj:hello

Type
     Task (org.gradle.api.Task)

Descriptions
     (:hello) hello task from root
     (:someproj:hello) hello task from someproj

Group
     -

BUILD SUCCESSFUL"""
    }

    def "help for tasks same type different groups"() {
        setup:
        settingsFile.text = """
include ":someproj1"
include ":someproj2"
"""
        buildFile.text = """
        task hello {
            group = "group of root task"
        }
        project(":someproj1"){
            task hello {
                group = "group of subproject task"
            }
        }
        project(":someproj2"){
            task hello {
                group = "group of subproject task"
            }
        }
"""
        when:
        run "help", "--task", "hello"
        then:
        output.contains """Detailed task information for hello

Paths
     :hello
     :someproj1:hello
     :someproj2:hello

Type
     Task (org.gradle.api.Task)

Description
     -

Groups
     (:hello) group of root task
     (:someproj1:hello) group of subproject task
     (:someproj2:hello) group of subproject task

BUILD SUCCESSFUL"""
    }

    def "matchingTasksOfSameType"() {
        setup:
        settingsFile << "include ':subproj1'"
        buildFile << "allprojects{ apply plugin:'java'}"
        when:
        run "help", "--task", ":jar"
        then:
        output.contains """Detailed task information for :jar

Path
     :jar

Type
     Jar (org.gradle.api.tasks.bundling.Jar)

Description
     Assembles a jar archive containing the main classes.

Group
     build

BUILD SUCCESSFUL"""

        when:
        run "help", "--task", "jar"
        then:
        output.contains """Detailed task information for jar

Paths
     :jar
     :subproj1:jar

Type
     Jar (org.gradle.api.tasks.bundling.Jar)

Description
     Assembles a jar archive containing the main classes.

Group
     build

BUILD SUCCESSFUL"""

    }

    def "multipleMatchingTasksOfDifferentType"() {
        setup:
        settingsFile << "include ':subproj1'"
        buildFile << """task someTask(type:Jar){
            description = "an archiving operation"
        }

        project(":subproj1"){
            task someTask(type:Copy){
                description = "a copy operation"
            }
        }"""

        when:
        run "help", "--task", "someTask"
        then:
        output.contains """Detailed task information for someTask

Path
     :subproj1:someTask

Type
     Copy (org.gradle.api.tasks.Copy)

Description
     a copy operation

Group
     -

----------------------

Path
     :someTask

Type
     Jar (org.gradle.api.tasks.bundling.Jar)

Description
     an archiving operation

Group
     -

----------------------

BUILD SUCCESSFUL"""
    }

    def "error message contains possible candidates"() {
        buildFile.text = """
        task aTask
"""
        when:
        fails "help", "--task", "bTask"
        then:
        failure.assertHasCause("Task 'bTask' not found in root project '${testDirectory.getName()}'. Some candidates are: 'aTask', 'tasks'")
    }

    def "tasks can be defined by camelCase matching"() {
        buildFile.text = """
        task someCamelCaseTask{
            description = "a description"
        }"""
        when:
        run "help", "--task", "sCC"
        then:
        output.contains """Detailed task information for sCC

Path
     :someCamelCaseTask

Type
     Task (org.gradle.api.Task)

Description
     a description

Group
     -

BUILD SUCCESSFUL"""

    }

    def "prints hint when using invalid command line options"() {
        when:
        fails "help", "--tasssk", "help"

        then:
        failure.assertHasDescription("Problem configuring task :help from command line.")
        failure.assertHasCause("Unknown command-line option '--tasssk'.")
        failure.assertHasResolutions(
            "Run gradle help --task :help to get task usage details.",
            "Run with --info or --debug option to get more log output.",
            "Run with --scan to get full insights.",
        )
    }

    def "listsEnumAndBooleanCmdOptionValues"() {
        when:
        run "help", "--task", "hello"
        then:
        output.contains """Detailed task information for hello

Paths
     :hello
     :proj1:hello
     :proj2:hello

Type
     CustomTask (CustomTask)

Options
     --booleanValue     Configures a boolean flag in CustomTask.

     --enumValue     Configures an enum value in CustomTask.
                     Available values are:
                          ABC
                          DEF
                          GHIJKL

Description
     -

Group
     -

BUILD SUCCESSFUL"""
    }

    def "listsCommonDynamicAvailableValues"() {
        when:
        run "help", "--task", "hello"
        then:
        output.contains """Detailed task information for hello

Paths
     :sub1:hello
     :sub2:hello

Type
     CustomTask (CustomTask)

Options
     --stringValue     Configures a string value in CustomTask.
                       Available values are:
                            optionA
                            optionB
                            optionC

Description
     -

Group
     -

BUILD SUCCESSFUL"""
    }

    def "sortsOptionsInAlphabeticOrder"() {
        when:
        run "help", "--task", "hello"

        then:
        output.contains """
Options
     --valueA     descA

     --valueB     descB

     --valueC     descC"""
    }
}
