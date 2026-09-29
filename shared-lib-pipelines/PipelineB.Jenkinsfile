// Application Pipeline B — repo(<url>) + build('Test')
@Library('shared-lib') _

pipeline {
    agent any
    tools {
        jdk 'myjava'
        maven 'mymaven'
    }
    stages {
        stage('Checkout') {
            steps {
                repo('https://github.com/zestabhijeet/SonarQubeCoverageJava.git')
            }
        }
        stage('Test') {
            steps {
                build('Test')
            }
        }
    }
}
