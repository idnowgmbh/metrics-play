properties([
        buildDiscarder(logRotator(artifactDaysToKeepStr: '180', artifactNumToKeepStr: '100', daysToKeepStr: '180', numToKeepStr: '100')),
        [$class: 'ScannerJobProperty', doNotScan: false],
        disableConcurrentBuilds(),
        [
                $class                       : 'ThrottleJobProperty',
                categories                   : [],
                limitOneJobWithMatchingParams: false,
                maxConcurrentPerNode         : 0,
                maxConcurrentTotal           : 0,
                paramsToUseForLimit          : '',
                throttleEnabled              : false,
                throttleOption               : 'project'
        ]
])

final ARTIFACTORY_HOST = 'docker.dev.idnow.de'
final SBT_CREDENTIALS_FILE = '.credentials'

final name = "metrics-play"
String tagged_label = "${ARTIFACTORY_HOST}/${name}"
String container_title = ""

String shortCommit = ""
String fullCommit = ""
String commitMessage = ""
String commitTime = ""

def finalTag = false
def SCA = []
def build_container_image = "docker.dev.idnow.de/focal.openjdk11-jre.sbt"
def application_title = "${name}"
currentBuild.displayName = "${currentBuild.displayName}: AI"

if (env.CHANGE_BRANCH) {
    currentBuild.displayName = "${currentBuild.displayName}: ${env.CHANGE_BRANCH}"
}

pipeline {
    parameters {
      string(name: 'AgentLabel', defaultValue: 'ec2slave', description: 'Label of Jenkins agent where the pipeline will be run')
    }

    agent { label params.AgentLabel }

    options {
        ansiColor('xterm')
        disableConcurrentBuilds()
    }
    environment {
      PMD_SCRIPT= "${HOME}/bin/pmd/bin/run.sh"
      SBT_CREDENTIALS="${SBT_CREDENTIALS_FILE}"
    }
    triggers {
        cron('')
    }
    stages {


        stage('Compile') {
            steps {
                withDockerRegistry([credentialsId:'jenkins-artifactory', url: "https://${ARTIFACTORY_HOST}"]) {
                    script {
                        withDockerContainer(image: build_container_image, args: '-v de.idnow.ai-coursier:/root/.cache/coursier') {
			    configFileProvider([configFile(fileId: 'sbt_credentials', targetLocation: "${SBT_CREDENTIALS_FILE}")]) {
                            	sh "./sbt ';clean ;compile'"
			    }
                        }
                    }
                }
            }
        }

        stage('Test') {
            when {
                not {
                    anyOf {
                        buildingTag()
                        // changeRequest()
                        expression { return !params.runTests }
                    }
                }
            }

            steps {
                script {
                   docker_group = sh (
                         script: 'getent group docker | cut -d: -f3',
                         returnStdout: true
                         ).trim()
                }
                withDockerRegistry([credentialsId:'jenkins-artifactory', url: "https://${ARTIFACTORY_HOST}"]) {
                    script {
                        withDockerContainer(image: build_container_image, args: "-v de.idnow.ai-coursier:/root/.cache/coursier -u jenkins:${docker_group} -v /var/run/docker.sock:/var/run/docker.sock") {
                            withEnv(['DOCKER_HOST=unix:///var/run/docker.sock']) {
                                sh "./sbt jacoco"
                                def scala = scanForIssues tool: scala()
                                def java = scanForIssues tool: java()
                                def javaDoc = scanForIssues tool: javaDoc()
                                SCA.push(scala)
                                SCA.push(java)
                                SCA.push(javaDoc)
                            }
                        }
                    }
                }
            }
            post {
               always {
                 step([$class: 'JacocoPublisher'])
                 junit allowEmptyResults: true, testDataPublishers: [[$class: 'StabilityTestDataPublisher']], testResults: 'target/test-reports/**.xml'
                 publishHTML([allowMissing: true, alwaysLinkToLastBuild: true, keepAll: false, reportDir: 'target/scala-2.13/jacoco/report/html', reportFiles: 'index.html', reportName: "HTML ${application_title} Report", reportTitles: ''])
               }
            }
        }

        stage('Code Analysis') {
            when {
                not {
                    buildingTag()
                }
            }
            steps {
                script {
                    sh "mkdir -p target"
                    parallel(
                        'pmd-java': {
                            def pmd_java = sh returnStdout: true, script: "sudo ${PMD_SCRIPT} pmd -d app -f xml --failOnViolation false -threads 2 -l java -R conf/jenkins/pmd.java.rule.xml -cache target/pmd.java.cache"
                            writeFile file: 'target/pmd.java.xml', text: pmd_java
                        },
                        'cpd-java': {
                            def cpd_java = sh returnStdout: true, script: "sudo ${PMD_SCRIPT} cpd --files app --minimum-tokens 50 --format xml --failOnViolation false --language java --exclude app/util/documents/* --exclude app/util/DateFormats.java"
                            writeFile file: 'target/cpd.java.xml', text: cpd_java
                        }
                    )
                    def pmd = scanForIssues tool: pmdParser(pattern: '**/target/pmd.java.xml')
                    def cpd = scanForIssues tool: cpd(pattern: '**/target/cpd.java.xml')
                    SCA.push(pmd)
                    SCA.push(cpd)
                }
            }
        }

        stage('Package') {
            steps {
                withDockerRegistry([credentialsId:'jenkins-artifactory', url: "https://${ARTIFACTORY_HOST}"]) {
                    script {
                        def version = env.TAG_NAME?.trim() ? env.TAG_NAME :env.BRANCH_NAME
                        sh """
                        echo "version=${version}" | tee conf/version.properties;
                        echo "commit=${fullCommit}" | tee -a conf/version.properties;
                        """
                        withDockerContainer(image: build_container_image, args: '-v de.idnow.ai-coursier:/root/.cache/coursier') {
                            sh "./sbt dist"
                        }
                    }
                }
            }
        }
        stage('SonarQube') {
            when {
                not {
                    anyOf {
                        buildingTag()
                        expression { return !params.runTests }
                    }
                }
            }
            steps {
                withSonarQubeEnv('SonarQube') {
                    script {
                        def scannerHome = tool 'SonarQube Scanner';
                        sh "${scannerHome}/bin/sonar-scanner -Dsonar.projectKey=${name}"
                    }
                }
                timeout(time: 5, unit: 'MINUTES') {
                    script {
                        def qg = waitForQualityGate()
                        if (qg.status != 'OK') {
                            // error "Pipeline aborted due to quality gate failure: ${qg.status}"
                        }
                    }
                }
            }
        }

        stage("Build docker image") {
            when {
                expression { return params.uploadToArtifactory || env.TAG_NAME || params.deployEnv != 'NONE'}
            }
            options {
                throttle(['Docker'])
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'jenkins-artifactory', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    script {
                        def baseImageName = "${ARTIFACTORY_HOST}/idnow-base".toLowerCase().toString()

                        try {
                            sh "docker login -u ${env.DOCKER_USER} -p ${env.DOCKER_PASS} ${ARTIFACTORY_HOST} && docker pull ${baseImageName}:latest"
                        } catch (err) {
                            echo "Failed to pull ${baseImageName}:latest from artifactory: ${err}"
                        }

                        def version = env.TAG_NAME?.trim() ? env.TAG_NAME :env.BRANCH_NAME

                        def formatedCommitedMsg = commitMessage.replaceAll('"','-');

                        sh "unzip -q -d target/universal target/universal/ai-2.0-SNAPSHOT.zip"
                        sh """docker build \
                                --label "commit.short=${shortCommit}" \
                                --label "commit.full=${fullCommit}" \
                                --label "commit.message=${formatedCommitedMsg}" \
                                --label "commit.time=${commitTime}" \
                                --label "version=${version}" \
                                -t '${tagged_label}' ."""

                        if (finalTag) {
                            sh "docker tag ${tagged_label} ${ARTIFACTORY_HOST}/${name}:latest"
                        }
                    }
                }
            }
        }

        stage("Upload docker image") {
            when {
                expression { return params.uploadToArtifactory || env.TAG_NAME || params.deployEnv != 'NONE'}
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'jenkins-artifactory', passwordVariable: 'DOCKER_PASS', usernameVariable: 'DOCKER_USER')]) {
                    script {
                        try {
                            def server = Artifactory.server 'artifactory'
                            def rtDocker = Artifactory.docker server: server
                            def buildInfo = Artifactory.newBuildInfo()

                            // Attach custom properties to the published artifacts:
                            rtDocker.addProperty("project-name", env.JOB_NAME)
                                    .addProperty("branch", env.BRANCH_NAME)
                                    .addProperty("git-commit-short", shortCommit)
                                    .addProperty("git-commit-full", fullCommit)
                                    .addProperty("git-commit-message", commitMessage)
                                    .addProperty("git-commit-time", commitTime)
                            // Push a docker image to Artifactory (here we're pushing de.idnow.ai:latest). The push method also expects
                            // Artifactory repository name:
                            echo "before build-info push"
                            def taggedBuildInfo = rtDocker.push(tagged_label, 'docker')
                            buildInfo.append(taggedBuildInfo)
                            if (finalTag) {
                                def latestBuildInfo = rtDocker.push("${ARTIFACTORY_HOST}/${name}:latest", 'docker')
                                buildInfo.append(latestBuildInfo)
                            }
                            echo "after build-info push"
                            // Publish the build-info to Artifactory:
                            buildInfo.retention maxBuilds: 90, maxDays: 180, deleteBuildArtifacts: true, async: true
                            server.publishBuildInfo buildInfo
                        } catch (e) {
                            echo "Error pusing to artifactory: ${e}, will try without Properties"
                            def server = Artifactory.server 'artifactory'
                            def rtDocker = Artifactory.docker server: server
                            def buildInfo = Artifactory.newBuildInfo()

                            // Artifactory repository name:
                            def taggedBuildInfo = rtDocker.push(tagged_label, 'docker')
                            buildInfo.append(taggedBuildInfo)
                            if (finalTag) {
                                def latestBuildInfo = rtDocker.push("${ARTIFACTORY_HOST}/${name}:latest", 'docker')
                                buildInfo.append(latestBuildInfo)
                            }
                            // Publish the build-info to Artifactory:
                            buildInfo.retention maxBuilds: 90, maxDays: 180, deleteBuildArtifacts: true, async: true
                            server.publishBuildInfo buildInfo
                        }
                        currentBuild.description = "${currentBuild.description} | tag ${tagged_label}"
                    }
                }
            }
        }

        stage("Deploy Docker image") {
            when {
                expression { return params.deployEnv != 'NONE' }
            }
            steps {
                script {
                    def imageVersion = ""
                    def imageBranch = ""
                    if (env.TAG_NAME?.trim()) {
                        imageVersion = env.TAG_NAME.trim()
                        imageBranch = ""
                    } else {
                        imageVersion = env.BUILD_NUMBER
                        imageBranch = env.BRANCH_NAME
                    }
                    imageVersion = imageVersion.toLowerCase()
                    imageBranch = imageBranch.toLowerCase()

                    job = build job: "Deploy/DEPLOY_service",
                        parameters: [
                            string(name: 'container', value: 'ai'),
                            string(name: 'Environment', value: params.deployEnv),
                            string(name: 'Version', value: imageVersion),
                            string(name: 'Branch', value: imageBranch)
                        ]
                }
            }
        }
    }

    post {
        always {
            script {
                sh "docker rmi ${tagged_label} || true"
                sh """(docker images | grep "${container_title}"| awk '{print \$3}' | xargs docker rmi -f) || true"""
            }
            step([$class: 'CordellWalkerRecorder'])
            cleanWs()
            jiraSendBuildInfo site: 'idnowgmbh.atlassian.net'
            script {
                if (SCA.size() > 0) {
                    publishIssues healthy: 35, issues: SCA, publishAllIssues: true, unhealthy: 90
                }
            }
        }
        failure {
            slackSend (
                    channel: 'jenkins',
                    color: '#FF0000',
                    message: "Build ${env.JOB_NAME} Failed : (<${env.BUILD_URL}|Open>) ${currentBuild.durationString} ",
                    tokenCredentialId: 'slack_token'
            )
        }

        fixed {
            slackSend (
                    channel: 'jenkins',
                    color: '#00FF00',
                    message: "Build ${env.JOB_NAME} Fixed : (<${env.BUILD_URL}|Open>) ${currentBuild.durationString} ",
                    tokenCredentialId: 'slack_token'
            )
        }
    }

}
