def sendBuildEmail() {
    emailext attachLog: true, attachmentsPattern: 'target/surefire-reports/*.xml',
        body: '''$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS:
Check console output at $BUILD_URL to view the results.''',
        compressLog: true, recipientProviders: [buildUser(), requestor()], subject: '$PROJECT_NAME - Build # $BUILD_NUMBER - $BUILD_STATUS!', to: 'zestabhijeet@gmail.com'
}
 
pipeline{
    tools{
        jdk 'myjava'
        maven 'mymaven'
    }
	agent any
      stages{
           stage('Checkout'){
	    
               steps{
		 echo 'cloning..'
                 git 'https://github.com/zestabhijeet/SonarQubeCoverageJava.git'
              }
          }
          stage('Compile'){
             
              steps{
                  echo 'compiling..'
                  sh 'mvn compile'
	      }
          }
          stage('CodeReview'){
		  
              steps{
		    
		  echo 'codeReview'
                  sh 'mvn pmd:pmd'
              }
          }
           stage('UnitTest'){
		  
              steps{
	         echo 'Testing'
                  sh 'mvn test'
              }
               post {
               success {
                   junit 'target/surefire-reports/*.xml'
               }
           }	
          }
           stage('Coverage'){
              
              steps{
                  echo 'generating coverage report'
                  sh 'mvn org.jacoco:jacoco-maven-plugin:prepare-agent test org.jacoco:jacoco-maven-plugin:report'
              }
              
          }
          stage('SonarCloud Analysis'){
 
              steps{
                  echo 'running sonar analysis'
                  withCredentials([string(credentialsId: 'jenkins-token', variable: 'SONAR_TOKEN')]) {
                      withSonarQubeEnv('SonarCloud') {
                          sh 'mvn org.sonarsource.scanner.maven:sonar-maven-plugin:sonar -Dsonar.organization=zestabhijeet -Dsonar.projectKey=com.java:SonarQubeCoverageJava -Dsonar.host.url=https://sonarcloud.io -Dsonar.token=$SONAR_TOKEN -Dsonar.sources=src/main/java -Dsonar.tests=src/test/java -Dsonar.java.binaries=target/classes -Dsonar.junit.reportPaths=target/surefire-reports'
                      }
                  }
              }
          }
          stage('Quality Gate'){
              steps{
                  timeout(time: 5, unit: 'MINUTES') {
                      waitForQualityGate abortPipeline: true
                  }
              }
          }
          stage('Package'){
		  
              steps{
		  
                  sh 'mvn package'
              }
          }
	     
          
      }
 
      post {
          always {
              script {
                  sendBuildEmail(currentBuild.currentResult)
              }
          }
      }
}
 
