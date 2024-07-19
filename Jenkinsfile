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
currentBuild.displayName = "${currentBuild.displayName}: metrics"

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
            sh "sbt ';clean ;compile'"
			                    }
                        }
                    }
                }
            }
        }

        stage('Test') {
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
                                sh "sbt coverage test coverageReport"
                            }
                        }
                    }
                }
            }
        }

        stage('Publish to artifactory') {
            steps {
                script {
                    withDockerContainer(image: build_container_image, toolName: 'docker') {
                      sh """
                        echo 'publishTo := Some("Artifactory Realm" at "https://${ARTIFACTORY_HOST}/artifactory/sbt-local")' >> build.sbt;
                      """
                        sh "sbt -v publish"
                    }
                    archiveArtifacts artifacts: 'target/scala-2.13/*.jar', fingerprint: true
                }
            }
            post {
                failure {
                    script {
                        currentBuild.displayName = "${currentBuild.displayName} : Failed in publish to Artifactory."
                    }
                }
            }
        }

    }

    post {
        always {
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
