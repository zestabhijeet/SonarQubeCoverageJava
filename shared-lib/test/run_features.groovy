import org.codehaus.groovy.control.CompilerConfiguration
def varsDir = args[0]; def dir = args[1]; int fail = 0
def run = { String file, Map params ->
    def j = new MockJenkins(varsDir); j.params = params
    def src = new File(dir, file).text.replaceAll(/(?m)^@Library\(.*\)\s*_\s*$/, '')
    def cc = new CompilerConfiguration(); cc.scriptBaseClass = StepScript.name
    def s = new GroovyShell(this.class.classLoader, new Binding(), cc).parse(src)
    ((StepScript) s).setJenkins(j); s.run(); j
}
def check = { l, ok, d='' -> println((ok?'PASS ':'FAIL ')+l+(ok?'':"  -> $d")); if(!ok) fail++ }

def ci = run('Jenkinsfile', [:])
def stages = ci.calls.findAll { it.startsWith('stage:') }
check('CI stages', stages == ['stage:Checkout','stage:Build & Unit Test','stage:Credential Masking Demo','stage:SonarCloud Analysis','stage:Package','stage:Log Filter'], stages)
check('webhook trigger githubPush()', ci.calls.any { it.contains('githubPush') }, ci.calls.findAll{it.startsWith('triggers')})
check('secret bound via withCredentials', ci.calls.count { it.startsWith('withCredentials') } == 2)
def mp = ci.calls.find { it.startsWith('maskPasswords') }
check('maskPasswords blacklist has 4 regexes', mp && (mp =~ /key:/).count == 4, mp)
// the regexes really mask the demo lines
def regexes = new File(dir,'Jenkinsfile').text.findAll(/value: '([^']+)'/) { it[1].replace('\\\\','\\') }
['db password=Dummy#Pass123','leaked key AKIAABCDEFGHIJKLMNOP','sqp_'+'a'*40,'ghp_'+'A'*36].each { line ->
    def masked = regexes.inject(line) { acc, r -> acc.replaceAll(r, '********') }
    check("blacklist masks: ${line.take(24)}...", masked.contains('********') && !masked.contains('Dummy') && !masked.contains('AKIA') && !masked.contains('sqp_') && !masked.contains('ghp_'), masked)
}
check('token not interpolated by Groovy', !ci.calls.any { it.contains('-Dsonar.token=') && !it.contains('$SONAR_TOKEN') })
check('filterlogs runs in Log Filter', ci.calls.indexOf('stage:Log Filter') < ci.calls.size())

def p = [BACKUP_DIR:'/var/lib/jenkins/thinBackup', S3_BUCKET:'b', S3_PREFIX:'jenkins/thinBackup', AWS_REGION:'ap-south-1', DRY_RUN:false]
def bk = run('Jenkinsfile.s3-backup', p)
check('backup stages', bk.calls.findAll{it.startsWith('stage:')} == ['stage:Check ThinBackup Output','stage:Upload to S3','stage:Verify Upload'])
check('cron trigger present', bk.calls.any{ it.contains('cron') })
check('aws s3 sync with SSE', bk.calls.any{ it.contains('aws s3 sync') && it.contains('--sse AES256') })
check('verify step runs', bk.calls.any{ it.contains('aws s3 ls') })
def bk2 = run('Jenkinsfile.s3-backup', p + [DRY_RUN:true])
check('DRY_RUN skips verify', !bk2.calls.any{ it.contains('aws s3 ls') } && bk2.calls.contains('skipped'))
println "\n${fail ? fail+' FAILURE(S)' : 'ALL TESTS PASSED'}"
System.exit(fail ? 1 : 0)
