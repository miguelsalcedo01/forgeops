/*
 * Copyright 2019 ForgeRock AS. All Rights Reserved
 *
 * Use of this code requires a commercial software license with ForgeRock AS.
 * or with one of its affiliates. All use shall be exclusively subject
 * to such license between the licensee and ForgeRock AS.
 */

import com.forgerock.pipeline.reporting.PipelineRun

void runStage(PipelineRun pipelineRun) {

    def stageName = 'PIT1'
    def normalizedStageName = dashboard_utils.normalizedStageName(stageName)

    pipelineRun.pushStageOutcome(normalizedStageName, stageDisplayName: stageName) {
        node('google-cloud') {
            stage(stageName) {
                pipelineRun.updateStageStatusAsInProgress()

                dir('forgeops') {
                    unstash 'workspace'
                }

                def gitBranch = isPR() ? "origin/pr/${env.CHANGE_ID}" : 'master'
                def gitImageTag = isPR() ? '7.0.0-pr' : '6.5.1'

                dir('lodestar') {
                    def stagesCloud = [:]

                    def cfg_common = [
                        TESTS_SCOPE                     : 'tests/platform_deployment',
                        DEPLOYMENT_NAME                 : 'platform-deployment',
                        COMPONENTS_FRCONFIG_GIT_REPO    : "https://stash.forgerock.org/scm/cloud/forgeops.git",
                        COMPONENTS_FRCONFIG_GIT_BRANCH  : gitBranch,
                        COMPONENTS_AMSTER_GITIMAGE_TAG  : gitImageTag,
                        COMPONENTS_AM_GITIMAGE_TAG      : gitImageTag,
                        COMPONENTS_IDM_GITIMAGE_TAG     : gitImageTag,
                        COMPONENTS_IG_GITIMAGE_TAG      : gitImageTag,
                        STASH_LODESTAR_BRANCH           : commonModule.LODESTAR_GIT_COMMIT,
                        SKIP_FORGEOPS                   : 'True',
                        EXT_FORGEOPS_PATH               : "${env.WORKSPACE}/forgeops",
                    ]

                    // Helm tests
                    def subStageName = 'helm'
                    def reportName = "latest-${subStageName}.html"
                    stagesCloud[subStageName] = dashboard_utils.stageCloud(reportName)

                    dashboard_utils.determineUnitOutcome(stagesCloud[subStageName]) {
                        def cfg = cfg_common.clone()
                        cfg += [
                            USE_SKAFFOLD: false,
                            REPORT_NAME : reportName
                        ]

                        withGKEPitNoStages(cfg)
                    }

                    // Skaffold tests
                    subStageName = 'skaffold'
                    reportName = "latest-${subStageName}.html"
                    stagesCloud[subStageName] = dashboard_utils.stageCloud(reportName)

                    dashboard_utils.determineUnitOutcome(stagesCloud[subStageName]) {
                        def cfg = cfg_common.clone()
                        cfg += [
                            USE_SKAFFOLD: true,
                            REPORT_NAME : reportName
                        ]

                        withGKEPitNoStages(cfg)
                    }

                    summaryReportGen.createAndPublishSummaryReport(stagesCloud, stageName, "build&&linux", false, normalizedStageName, "${normalizedStageName}.html")
                    return dashboard_utils.determinePitOutcome(stagesCloud, "${env.BUILD_URL}/${normalizedStageName}/")
                }
            }
        }
    }
}

return this
