def call(String reponame, String mvnaction) {
    git "${reponame}"
    if ("${mvnaction}" == "Build") {
        sh 'mvn clean package'
    }
}
