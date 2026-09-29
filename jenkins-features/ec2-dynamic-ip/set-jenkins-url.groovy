// Jenkins runs this at every startup (from $JENKINS_HOME/init.groovy.d/).
// Sets Manage Jenkins > System > Jenkins URL to the instance's current public IP.
import jenkins.model.JenkinsLocationConfiguration

def imds = 'http://169.254.169.254/latest'
try {
    def tokenConn = new URL("${imds}/api/token").openConnection()
    tokenConn.requestMethod = 'PUT'
    tokenConn.setRequestProperty('X-aws-ec2-metadata-token-ttl-seconds', '60')
    tokenConn.doOutput = true
    tokenConn.outputStream.close()
    def imdsToken = tokenConn.inputStream.text

    def ipConn = new URL("${imds}/meta-data/public-ipv4").openConnection()
    ipConn.setRequestProperty('X-aws-ec2-metadata-token', imdsToken)
    def ip = ipConn.inputStream.text.trim()

    def location = JenkinsLocationConfiguration.get()
    location.url = "http://${ip}:8080/"
    location.save()
    println "set-jenkins-url: Jenkins URL is now ${location.url}"
} catch (e) {
    println "set-jenkins-url: could not update Jenkins URL - ${e.message}"
}
