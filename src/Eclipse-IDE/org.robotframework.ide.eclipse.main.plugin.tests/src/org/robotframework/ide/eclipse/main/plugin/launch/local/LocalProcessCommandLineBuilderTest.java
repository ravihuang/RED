/*
 * Copyright 2018 Nokia Solutions and Networks
 * Licensed under the Apache License, Version 2.0,
 * see license.txt file for details.
 */
package org.robotframework.ide.eclipse.main.plugin.launch.local;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.variables.IStringVariableManager;
import org.eclipse.core.variables.IValueVariable;
import org.eclipse.core.variables.VariablesPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.rf.ide.core.RedTemporaryDirectory;
import org.rf.ide.core.environment.RobotRuntimeEnvironment;
import org.rf.ide.core.environment.SuiteExecutor;
import org.rf.ide.core.execution.RunCommandLineCallBuilder.RunCommandLine;
import org.rf.ide.core.project.RobotProjectConfig;
import org.rf.ide.core.project.RobotProjectConfig.LibraryType;
import org.rf.ide.core.project.RobotProjectConfig.ReferencedLibrary;
import org.rf.ide.core.project.RobotProjectConfig.ReferencedVariableFile;
import org.rf.ide.core.project.RobotProjectConfig.RelativeTo;
import org.rf.ide.core.project.RobotProjectConfig.RelativityPoint;
import org.rf.ide.core.project.RobotProjectConfig.SearchPath;
import org.robotframework.ide.eclipse.main.plugin.RedPreferences;
import org.robotframework.ide.eclipse.main.plugin.model.RobotModel;
import org.robotframework.ide.eclipse.main.plugin.model.RobotProject;
import org.robotframework.red.junit.ProjectProvider;
import org.robotframework.red.junit.ResourceCreator;
import org.robotframework.red.junit.RunConfigurationProvider;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;

@RunWith(MockitoJUnitRunner.class)
public class LocalProcessCommandLineBuilderTest {

    private static final String PROJECT_NAME = LocalProcessCommandLineBuilderTest.class.getSimpleName();

    private static final IStringVariableManager VARIABLE_MANAGER = VariablesPlugin.getDefault()
            .getStringVariableManager();

    private static final IValueVariable[] CUSTOM_VARIABLES = new IValueVariable[] {
            VARIABLE_MANAGER.newValueVariable("a_var", "a_desc", true, "a_value"),
            VARIABLE_MANAGER.newValueVariable("b_var", "b_desc", true, "b_value"),
            VARIABLE_MANAGER.newValueVariable("c_var", "c_desc", true, "c_value") };

    @ClassRule
    public static ProjectProvider projectProvider = new ProjectProvider(PROJECT_NAME);

    @Rule
    public ProjectProvider movedProjectProvider = new ProjectProvider(PROJECT_NAME + "Moved");

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Rule
    public ResourceCreator resourceCreator = new ResourceCreator();

    @Rule
    public RunConfigurationProvider runConfigurationProvider = new RunConfigurationProvider(
            RobotLaunchConfiguration.TYPE_ID);

    @Mock
    private RedPreferences preferences;

    @Mock
    private RobotRuntimeEnvironment environment;

    @BeforeClass
    public static void beforeSuite() throws Exception {
        VARIABLE_MANAGER.addVariables(CUSTOM_VARIABLES);

        projectProvider.createDir("001__suites_a");
        projectProvider.createFile("001__suites_a/s1.robot", "*** Test Cases ***", "001__case1", "  Log  10",
                "001__case2", "  Log  20");
        projectProvider.createFile("001__suites_a/s2.robot", "*** Test Cases ***", "001__case3", "  Log  10",
                "001__case4", "  Log  20");

        projectProvider.createDir("002__suites_b");
        projectProvider.createDir("002__suites_b/nested");
        projectProvider.createFile("002__suites_b/s3.robot", "*** Test Cases ***", "002__case5", "  Log  10",
                "002__case6", "  Log  20");
        projectProvider.createFile("002__suites_b/nested/s4.robot", "*** Test Cases ***", "002__case7", "  Log  10",
                "002__case8", "  Log  20");

        projectProvider.createDir("dir.with.dots");
        projectProvider.createFile("dir.with.dots/s.5.robot", "*** Test Cases ***", "case9", "  Log  10", "case10",
                "  Log  20");

        projectProvider.createFile("executable_script.bat");
    }

    @AfterClass
    public static void afterSuite() throws Exception {
        VARIABLE_MANAGER.removeVariables(CUSTOM_VARIABLES);
    }

    @Before
    public void beforeTest() throws Exception {
        projectProvider.configure();
    }

    @Test
    public void commandLineIsCreated_whenProjectDoesNotContainConfigurationFile() throws Exception {
        projectProvider.deconfigure();

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Jython);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(6)
                .startsWith("/path/to/executable", "-m", "robot.run")
                .doesNotContain("-J-cp", "-P", "-V")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWithInterpreterPath_whenActiveRuntimeEnvironmentIsUsed() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setInterpreterArguments("-a1 -a2");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .startsWith("/path/to/executable", "-a1", "-a2", "-m", "robot.run");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWithInterpreterName_whenProjectInterpreterIsNotUsed() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setInterpreterArguments("-a1 -a2");
        robotConfig.setUsingInterpreterFromProject(false);
        robotConfig.setInterpreter(SuiteExecutor.PyPy);

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .startsWith(SuiteExecutor.PyPy.executableName(), "-a1", "-a2", "-m", "robot.run");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWithDefaultInterpreterName_whenThereIsNoActiveRuntimeEnvironment() throws Exception {
        final RobotProject robotProject = spy(createRobotProject(projectProvider.getProject(), SuiteExecutor.Python));
        when(robotProject.getRuntimeEnvironment()).thenReturn(null);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setInterpreterArguments("-a1 -a2");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .startsWith(SuiteExecutor.Python.executableName(), "-a1", "-a2", "-m", "robot.run");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsPathToListenerAndArgumentFile_whenPreferenceIsSet() throws Exception {
        when(preferences.shouldLaunchUsingArgumentsFile()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("--listener", RedTemporaryDirectory.getTemporaryFile("TestRunnerAgent.py") + ":12345",
                        "--argumentfile")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getCommandLine()[6])
                .startsWith(RedTemporaryDirectory.createTemporaryDirectoryIfNotExists().toString());
        assertThat(commandLine.getArgumentFile())
                .hasValueSatisfying(argumentFile -> assertThat(argumentFile.generateContent())
                        .isEqualTo("# arguments automatically generated"));
    }

    @Test
    public void commandLineContainsSuitesToRun() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", emptyList(), "002__suites_b", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S1")
                .containsSequence("-s", PROJECT_NAME + ".Suites B")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsSuitesToRun_whenThereAreDotsInSuiteNames1() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("dir.with.dots", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-s", PROJECT_NAME + ".Dir.with.dots")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsSuitesToRun_whenThereAreDotsInSuiteNames2() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("dir.with.dots/s.5.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-s", PROJECT_NAME + ".Dir.with.dots.S.5")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsTestsToRun() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", asList("001__case1", "001__case2"),
                "001__suites_a/s2.robot", asList("001__case3")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(16)
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S1")
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S2")
                .containsSequence("-t", PROJECT_NAME + ".Suites A.S1.001__case1")
                .containsSequence("-t", PROJECT_NAME + ".Suites A.S1.001__case2")
                .containsSequence("-t", PROJECT_NAME + ".Suites A.S2.001__case3")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsTestsToRun_whenThereAreDotsInSuiteNames() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("dir.with.dots/s.5.robot", newArrayList("test 9")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-s", PROJECT_NAME + ".Dir.with.dots.S.5")
                .containsSequence("-t", PROJECT_NAME + ".Dir.with.dots.S.5.test 9")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineDoesNotContainNestedSuitesToRun() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "001__suites_a/s1.robot", emptyList(),
                "002__suites_b/nested/s4.robot", emptyList(), "002__suites_b/nested", emptyList(), "002__suites_b",
                emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-s", PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + ".Suites B")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void coreExceptionIsThrown_whenResourceDoesNotExist() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("not_existig_suite", asList("not_existig_case")));

        assertThatExceptionOfType(CoreException.class).isThrownBy(() -> createCommandLine(robotProject, robotConfig))
                .withMessage("Suite '%s' does not exist in project '%s'", "not_existig_suite", PROJECT_NAME)
                .withNoCause();
    }

    @Test
    public void commandLineTranslatesSuitesNames_whenNamesContainsDoubleUnderscores() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8).containsSequence("-s", PROJECT_NAME + ".Suites A");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineDoesNotTranslateTestNames_whenNamesContainsDoubleUnderscores() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", asList("001__case1", "001__case2")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(12)
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S1")
                .containsSequence("-t", PROJECT_NAME + ".Suites A.S1.001__case1")
                .containsSequence("-t", PROJECT_NAME + ".Suites A.S1.001__case2");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsSuitesToRun_whenProjectIsOutsideOfWorkspace() throws Exception {
        movedProjectProvider.createDir("suites");
        movedProjectProvider.createFile("suites/s1.robot", "*** Test Cases ***", "c1", "  Log  1");
        movedProjectProvider.createFile("suites/s2.robot", "*** Test Cases ***", "c2", "  Log  1");

        final File nonWorkspaceDir = tempFolder.newFolder("Project_outside");

        moveProject(movedProjectProvider.getProject(), nonWorkspaceDir);

        final RobotProject robotProject = createRobotProject(movedProjectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(movedProjectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("suites/s1.robot", emptyList(), "suites/s2.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-s", "Project Outside.Suites.S1")
                .containsSequence("-s", "Project Outside.Suites.S2")
                .doesNotContain("-t")
                .endsWith(nonWorkspaceDir.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsTestsToRun_whenProjectIsOutsideOfWorkspace() throws Exception {
        movedProjectProvider.createDir("suites");
        movedProjectProvider.createFile("suites/s1.robot", "*** Test Cases ***", "c11", "  Log  1", "c12", "  Log  2");
        movedProjectProvider.createFile("suites/s2.robot", "*** Test Cases ***", "c21", "  Log  1", "c22", "  Log  2");

        final File nonWorkspaceDir = tempFolder.newFolder("Project_outside_with_tests");

        moveProject(movedProjectProvider.getProject(), nonWorkspaceDir);

        final RobotProject robotProject = createRobotProject(movedProjectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(movedProjectProvider.getProject());
        robotConfig.setSuitePaths(
                ImmutableMap.of("suites/s1.robot", asList("c11", "c12"), "suites/s2.robot", asList("c21", "c22")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(18)
                .containsSequence("-s", "Project Outside With Tests.Suites.S1")
                .containsSequence("-s", "Project Outside With Tests.Suites.S2")
                .containsSequence("-t", "Project Outside With Tests.Suites.S1.c11")
                .containsSequence("-t", "Project Outside With Tests.Suites.S1.c12")
                .containsSequence("-t", "Project Outside With Tests.Suites.S2.c21")
                .containsSequence("-t", "Project Outside With Tests.Suites.S2.c22")
                .endsWith(nonWorkspaceDir.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenProjectIsOutsideOfWorkspace() throws Exception {
        final File nonWorkspaceDir = tempFolder.newFolder("Project_outside");
        final File nonWorkspaceTest = tempFolder.newFile("non_workspace_test.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceTest);

        movedProjectProvider.createDir("suites");
        movedProjectProvider.createFile("suites/s1.robot", "*** Test Cases ***", "c1", "  Log  1");
        resourceCreator.createLink(nonWorkspaceTest.toURI(), movedProjectProvider.getFile("suites/LinkedTest.robot"));
        moveProject(movedProjectProvider.getProject(), nonWorkspaceDir);

        final RobotProject robotProject = createRobotProject(movedProjectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(movedProjectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("suites", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", "Project Outside & Non Workspace Test.Project Outside.Suites")
                .containsSequence("-s", "Project Outside & Non Workspace Test.Non Workspace Test")
                .doesNotContain("-t")
                .endsWith(nonWorkspaceDir.getPath(), nonWorkspaceTest.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenWholeProjectIsSelected() throws Exception {
        final File nonWorkspaceFile = tempFolder.newFile("non_workspace.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile);
        resourceCreator.createLink(nonWorkspaceFile.toURI(), projectProvider.getFile("LinkedFile.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(7)
                .doesNotContain("-s", "-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceFile.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenLinkedSuiteFileIsSelected() throws Exception {
        final File nonWorkspaceTest = tempFolder.newFile("non_workspace_test.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceTest);
        resourceCreator.createLink(nonWorkspaceTest.toURI(), projectProvider.getFile("LinkedTestFile.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "LinkedTestFile.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Test." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Test.Non Workspace Test")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceTest.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenLinkedRpaSuiteFileIsSelected() throws Exception {
        final File nonWorkspaceTask = tempFolder.newFile("non_workspace_task.robot");
        Files.write("*** Tasks ***".getBytes(), nonWorkspaceTask);
        resourceCreator.createLink(nonWorkspaceTask.toURI(), projectProvider.getFile("LinkedTaskFile.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "LinkedTaskFile.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Task." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Task.Non Workspace Task")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceTask.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenLinkedFolderIsSelected() throws Exception {
        final File nonWorkspaceDir = tempFolder.newFolder("non_workspace_dir");
        resourceCreator.createLink(nonWorkspaceDir.toURI(), projectProvider.getDir("LinkedFolder"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "LinkedFolder", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Dir." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Dir.Non Workspace Dir")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceDir.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineDoesNotContainAdditionalDataSource_whenLinkedResourceFileIsSelected() throws Exception {
        final File nonWorkspaceResource = tempFolder.newFile("non_workspace_resource.robot");
        Files.write("*** Settings ***".getBytes(), nonWorkspaceResource);
        resourceCreator.createLink(nonWorkspaceResource.toURI(), projectProvider.getFile("LinkedResourceFile.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig
                .setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "LinkedResourceFile.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-s", PROJECT_NAME + ".Suites A")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineDoesNotContainAdditionalDataSource_whenVirtualFolderWithoutLinkedResourcesIsSelected()
            throws Exception {
        resourceCreator.createVirtual(projectProvider.getDir("VirtualDir"));
        resourceCreator.createVirtual(projectProvider.getDir("VirtualDir/NestedDir"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "VirtualDir", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-s", PROJECT_NAME + ".Suites A")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsAdditionalDataSource_whenVirtualFolderWithLinkedFileIsSelected() throws Exception {
        resourceCreator.createVirtual(projectProvider.getDir("VirtualDir"));
        resourceCreator.createVirtual(projectProvider.getDir("VirtualDir/NestedDir"));

        final File nonWorkspaceFile = tempFolder.newFile("non_workspace_file.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile);
        resourceCreator.createLink(nonWorkspaceFile.toURI(),
                projectProvider.getFile("VirtualDir/LinkedFileInsideVirtualDir.robot"));
        resourceCreator.createLink(nonWorkspaceFile.toURI(),
                projectProvider.getFile("VirtualDir/NestedDir/LinkedFileInsideVirtualDir.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "VirtualDir", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace File." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace File.Non Workspace File")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceFile.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsSeveralAdditionalDataSource_whenSeveralLinkedItemsAreFoundInSelection()
            throws Exception {
        resourceCreator.createVirtual(projectProvider.getDir("100__VirtualDir"));
        resourceCreator.createVirtual(projectProvider.getDir("100__VirtualDir/NestedDir"));

        final File nonWorkspaceDir = tempFolder.newFolder("dir");
        resourceCreator.createLink(nonWorkspaceDir.toURI(), projectProvider.getDir("100__VirtualDir/LinkedFolder"));
        final File nonWorkspaceFile1 = tempFolder.newFile("file 1.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile1);
        final File nonWorkspaceFile2 = tempFolder.newFile("file 2.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile2);
        resourceCreator.createLink(nonWorkspaceFile1.toURI(),
                projectProvider.getFile("100__VirtualDir/NestedDir/LinkedInsideVirtualDir.robot"));
        resourceCreator.createLink(nonWorkspaceFile2.toURI(), projectProvider.getFile("200__Linked.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList(), "100__VirtualDir", emptyList(),
                "200__Linked.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(17)
                .containsSequence("-s", PROJECT_NAME + " & Dir & File 1 & File 2." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Dir & File 1 & File 2.Dir")
                .containsSequence("-s", PROJECT_NAME + " & Dir & File 1 & File 2.File 1")
                .containsSequence("-s", PROJECT_NAME + " & Dir & File 1 & File 2.File 2")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceDir.getPath(),
                        nonWorkspaceFile1.getPath(), nonWorkspaceFile2.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsSeveralAdditionalDataSource_whenSeveralLinkedItemsAreFoundInTestCaseSelection()
            throws Exception {
        resourceCreator.createVirtual(projectProvider.getDir("100__VirtualDir"));
        resourceCreator.createVirtual(projectProvider.getDir("100__VirtualDir/NestedDir"));

        final File nonWorkspaceFile1 = tempFolder.newFile("file 1.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile1);
        final File nonWorkspaceFile2 = tempFolder.newFile("file 2.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile2);
        resourceCreator.createLink(nonWorkspaceFile1.toURI(),
                projectProvider.getFile("100__VirtualDir/NestedDir/LinkedInsideVirtualDir.robot"));
        resourceCreator.createLink(nonWorkspaceFile2.toURI(), projectProvider.getFile("200__Linked.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", asList("001__case1", "001__case2"),
                "100__VirtualDir/NestedDir/LinkedInsideVirtualDir.robot", asList("virt_case_1", "virt_case_2"),
                "200__Linked.robot", asList("link_case_1", "link_case_2")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(26)
                .containsSequence("-s", PROJECT_NAME + " & File 1 & File 2." + PROJECT_NAME + ".Suites A.S1")
                .containsSequence("-s", PROJECT_NAME + " & File 1 & File 2.File 1")
                .containsSequence("-s", PROJECT_NAME + " & File 1 & File 2.File 2")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2." + PROJECT_NAME + ".Suites A.S1.001__case1")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2." + PROJECT_NAME + ".Suites A.S1.001__case2")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2.File 1.virt_case_1")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2.File 1.virt_case_2")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2.File 2.link_case_1")
                .containsSequence("-t", PROJECT_NAME + " & File 1 & File 2.File 2.link_case_1")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceFile1.getPath(),
                        nonWorkspaceFile2.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsPythonPathsDefinedInRedXml_whenProjectRelativityPointIsUsed() throws Exception {
        final SearchPath searchPath1 = SearchPath.create("folder1");
        final SearchPath searchPath2 = SearchPath.create("folder2");
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addPythonPath(searchPath1);
        config.addPythonPath(searchPath2);
        config.setRelativityPoint(RelativityPoint.create(RelativeTo.PROJECT));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-P", String.format("%1$s%2$sfolder1:%1$s%2$sfolder2",
                        projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsPythonPathsDefinedInRedXml_whenWorkspaceRelativityPointIsUsed() throws Exception {
        final SearchPath searchPath1 = SearchPath.create(PROJECT_NAME + "/folder1");
        final SearchPath searchPath2 = SearchPath.create(PROJECT_NAME + "/folder2");
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addPythonPath(searchPath1);
        config.addPythonPath(searchPath2);
        config.setRelativityPoint(RelativityPoint.create(RelativeTo.WORKSPACE));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-P", String.format("%1$s%2$sfolder1:%1$s%2$sfolder2",
                        projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsPythonPathsForPythonLibrariesAddedToRedXml() throws Exception {
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addReferencedLibrary(ReferencedLibrary.create(LibraryType.PYTHON, "PyLib1", PROJECT_NAME + "/folder1"));
        config.addReferencedLibrary(ReferencedLibrary.create(LibraryType.PYTHON, "PyLib2", PROJECT_NAME + "/folder2"));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-P", String.format("%1$s%2$sfolder1:%1$s%2$sfolder2",
                        projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsClassPathsDefinedInRedXml_whenProjectRelativityPointIsUsed() throws Exception {
        final SearchPath searchPath1 = SearchPath.create("JavaLib1.jar");
        final SearchPath searchPath2 = SearchPath.create("JavaLib2.jar");
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addClassPath(searchPath1);
        config.addClassPath(searchPath2);
        config.setRelativityPoint(RelativityPoint.create(RelativeTo.PROJECT));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Jython);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-J-cp", String.format(".%1$s%2$s%3$sJavaLib1.jar%1$s%2$s%3$sJavaLib2.jar",
                        File.pathSeparator, projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsClassPathsDefinedInRedXml_whenWorkspaceRelativityPointIsUsed() throws Exception {
        final SearchPath searchPath1 = SearchPath.create(PROJECT_NAME + "/JavaLib1.jar");
        final SearchPath searchPath2 = SearchPath.create(PROJECT_NAME + "/JavaLib2.jar");
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addClassPath(searchPath1);
        config.addClassPath(searchPath2);
        config.setRelativityPoint(RelativityPoint.create(RelativeTo.WORKSPACE));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Jython);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-J-cp", String.format(".%1$s%2$s%3$sJavaLib1.jar%1$s%2$s%3$sJavaLib2.jar",
                        File.pathSeparator, projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsClassPathsForJavaLibrariesAddedToRedXml() throws Exception {
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addReferencedLibrary(
                ReferencedLibrary.create(LibraryType.JAVA, "JavaLib1", PROJECT_NAME + "/JavaLib1.jar"));
        config.addReferencedLibrary(
                ReferencedLibrary.create(LibraryType.JAVA, "JavaLib2", PROJECT_NAME + "/JavaLib2.jar"));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Jython);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-J-cp", String.format(".%1$s%2$s%3$sJavaLib1.jar%1$s%2$s%3$sJavaLib2.jar",
                        File.pathSeparator, projectProvider.getProject().getLocation().toOSString(), File.separator));
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsPathsForVariableFiles() throws Exception {
        final RobotProjectConfig config = new RobotProjectConfig();
        config.addReferencedVariableFile(ReferencedVariableFile.create(PROJECT_NAME + "/vars1.py"));
        config.addReferencedVariableFile(ReferencedVariableFile.create(PROJECT_NAME + "/vars2.py", "a", "b", "c"));
        projectProvider.configure(config);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Jython);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(12)
                .containsSequence("-V",
                        projectProvider.getProject().getLocation().toOSString() + File.separator + "vars1.py")
                .containsSequence("-V",
                        projectProvider.getProject().getLocation().toOSString() + File.separator + "vars2.py:a:b:c");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsTags() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setIsExcludeTagsEnabled(true);
        robotConfig.setExcludedTags(asList("EX_1", "EX_2"));
        robotConfig.setIsIncludeTagsEnabled(true);
        robotConfig.setIncludedTags(asList("IN_1", "IN_2"));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(14)
                .containsSequence("-i", "IN_1", "-i", "IN_2", "-e", "EX_1", "-e", "EX_2");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWitExecutableFilePath() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        final String executablePath = projectProvider.getFile("executable_script.bat").getLocation().toOSString();
        robotConfig.setExecutableFilePath(executablePath);

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(7).startsWith(executablePath);
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWithExecutableFilePath_whenPathContainsVariables() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setExecutableFilePath("${workspace_loc:/" + PROJECT_NAME + "/executable_script.bat}");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(7)
                .startsWith(projectProvider.getFile("executable_script.bat").getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineContainsExecutableFilePathWithArguments() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        final String executablePath = projectProvider.getFile("executable_script.bat").getLocation().toOSString();
        robotConfig.setExecutableFilePath(executablePath);
        robotConfig.setExecutableFileArguments("-arg1 abc -arg2 xyz");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11).startsWith(executablePath, "-arg1", "abc", "-arg2", "xyz");
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void commandLineStartsWithExecutableFilePathWithArgumentsAndEndsWithSingleCommandLineArg_whenPreferenceIsSet()
            throws Exception {
        when(preferences.shouldUseSingleCommandLineArgument()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        final String executablePath = projectProvider.getFile("executable_script.bat").getLocation().toOSString();
        robotConfig.setExecutableFilePath(executablePath);
        robotConfig.setExecutableFileArguments("-a -b -c");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(5).startsWith(executablePath, "-a", "-b", "-c");
        assertThat(commandLine.getCommandLine()[4]).startsWith("/path/to/executable -m robot.run --listener")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void coreExceptionIsThrown_whenExecutableFileDoesNotExist() throws Exception {
        final String executablePath = projectProvider.getFile("not_existing.bat").getLocation().toOSString();

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setExecutableFilePath(executablePath);

        assertThatExceptionOfType(CoreException.class).isThrownBy(() -> createCommandLine(robotProject, robotConfig))
                .withMessage("Executable file '%s' does not exist", executablePath)
                .withNoCause();
    }

    @Test
    public void coreExceptionIsThrown_whenExecutableFileDefinedWithVariableDoesNotExist() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setExecutableFilePath("${workspace_loc:/" + PROJECT_NAME + "/not_existing.bat}");

        assertThatExceptionOfType(CoreException.class).isThrownBy(() -> createCommandLine(robotProject, robotConfig))
                .withMessage("Variable references non-existent resource : ${workspace_loc:/" + PROJECT_NAME
                        + "/not_existing.bat}")
                .withNoCause();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenWholeProjectIsRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(6)
                .doesNotContain("-s", "-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenSingleSuiteIsRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(6)
                .doesNotContain("-s", "-t")
                .endsWith(projectProvider.getFile("001__suites_a/s1.robot").getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenSingleLinkedSuiteIsRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final File nonWorkspaceFile = tempFolder.newFile("non_workspace_suite.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile);
        resourceCreator.createLink(nonWorkspaceFile.toURI(), projectProvider.getFile("LinkedSuite.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("LinkedSuite.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(6)
                .doesNotContain("-s", "-t")
                .endsWith(nonWorkspaceFile.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenSingleFolderIsRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(6)
                .doesNotContain("-s", "-t")
                .endsWith(projectProvider.getFile("001__suites_a").getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenTestsFromSingleSuiteAreRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", asList("001__case1")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-t", "S1.001__case1")
                .doesNotContain("-s")
                .endsWith(projectProvider.getFile("001__suites_a/s1.robot").getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsUsed_whenTestsFromSingleLinkedSuiteAreRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final File nonWorkspaceFile = tempFolder.newFile("non_workspace_suite.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile);
        resourceCreator.createLink(nonWorkspaceFile.toURI(), projectProvider.getFile("LinkedSuite.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("LinkedSuite.robot", asList("case1", "case2")));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-t", "Non Workspace Suite.case1", "-t", "Non Workspace Suite.case2")
                .doesNotContain("-s")
                .endsWith(nonWorkspaceFile.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsNotUsed_whenSeveralResourcesAreRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(
                ImmutableMap.of("001__suites_a/s1.robot", emptyList(), "001__suites_a/s2.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(10)
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S1")
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S2")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsNotUsed_whenSingleFolderWithLinkedSuiteIsRunAndPreferenceIsSet() throws Exception {
        when(preferences.shouldUseSingleFileDataSource()).thenReturn(true);

        final File nonWorkspaceFile = tempFolder.newFile("non_workspace_suite.robot");
        Files.write("*** Test Cases ***".getBytes(), nonWorkspaceFile);
        resourceCreator.createLink(nonWorkspaceFile.toURI(),
                projectProvider.getFile("001__suites_a/LinkedSuite.robot"));

        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(11)
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Suite." + PROJECT_NAME + ".Suites A")
                .containsSequence("-s", PROJECT_NAME + " & Non Workspace Suite.Non Workspace Suite")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString(), nonWorkspaceFile.getPath());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void pathToSingleSuiteIsNotUsed_whenSingleSuiteIsRunAndPreferenceIsNotSet() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setSuitePaths(ImmutableMap.of("001__suites_a/s1.robot", emptyList()));

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(8)
                .containsSequence("-s", PROJECT_NAME + ".Suites A.S1")
                .doesNotContain("-t")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    @Test
    public void knownVariablesAreResolvedInAdditionalArguments() throws Exception {
        final RobotProject robotProject = createRobotProject(projectProvider.getProject(), SuiteExecutor.Python);
        final RobotLaunchConfiguration robotConfig = createRobotLaunchConfiguration(projectProvider.getProject());
        robotConfig.setRobotArguments("a ${a_var} ${a}");
        robotConfig.setInterpreterArguments("${b} b ${b_var}");
        final String executablePath = projectProvider.getFile("executable_script.bat").getLocation().toOSString();
        robotConfig.setExecutableFilePath(executablePath);
        robotConfig.setExecutableFileArguments("${c_var} ${c} c");

        final RunCommandLine commandLine = createCommandLine(robotProject, robotConfig);

        assertThat(commandLine.getCommandLine()).hasSize(16)
                .startsWith(executablePath, "c_value", "${c}", "c")
                .containsSequence("${b}", "b", "b_value", "-m", "robot.run")
                .containsSequence("a", "a_value", "${a}")
                .endsWith(projectProvider.getProject().getLocation().toOSString());
        assertThat(commandLine.getArgumentFile()).isNotPresent();
    }

    private RunCommandLine createCommandLine(final RobotProject robotProject,
            final RobotLaunchConfiguration robotConfig) throws CoreException, IOException {
        return new LocalProcessCommandLineBuilder(robotConfig, robotProject).createRunCommandLine(12345, preferences);
    }

    private RobotProject createRobotProject(final IProject project, final SuiteExecutor interpreter) {
        final RobotProject robotProject = spy(new RobotModel().createRobotProject(project));
        when(environment.getInterpreter()).thenReturn(interpreter);
        when(environment.getPythonExecutablePath()).thenReturn("/path/to/executable");
        when(robotProject.getRuntimeEnvironment()).thenReturn(environment);
        return robotProject;
    }

    private RobotLaunchConfiguration createRobotLaunchConfiguration(final IProject project) throws CoreException {
        final ILaunchConfiguration configuration = runConfigurationProvider.create("robot");
        final RobotLaunchConfiguration robotConfig = new RobotLaunchConfiguration(configuration);
        robotConfig.fillDefaults();
        robotConfig.setProjectName(project.getName());
        return robotConfig;
    }

    private void moveProject(final IProject project, final File destination) throws CoreException {
        final IProjectDescription description = ResourcesPlugin.getWorkspace().newProjectDescription(project.getName());
        description.setLocation(new Path(destination.getAbsolutePath()));
        project.move(description, true, null);
    }
}
