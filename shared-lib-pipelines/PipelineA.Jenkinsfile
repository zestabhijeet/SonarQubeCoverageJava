// Application Pipeline A — build('Install') + filterlogs('WARNING', 5)
// Job type: Pipeline script from SCM (this repo), Script Path: shared-lib-pipelines/PipelineA.Jenkinsfile
@Library('shared-lib') _

pipeline {
    agent any
    tools {
        jdk 'myjava'
        maven 'mymaven'
    }
    stages {
        stage('Build') {
            steps {
                build('Install')
            }
        }
        stage('Filter Logs') {
            steps {
                filterlogs('WARNING', 5)
            }
        }
    }
}
