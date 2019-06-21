import groovy.json.JsonOutput
/**
 * Hosts the common utility functions for jenkins pipelines
 */

/**
 * Send slack message to channel given in the config file
 * @param title Title of slack message
 * @param msg   Message
 * @param color Colour code
 */
void sendSlackMessage(String title, String msg, String color) {
  def config = getConfig()
  if(config == null || config.SLACK_WEBHOOK_URL == null) {
    println "No SLACK_WEBHOOK_URL mentioned"
    return
  }
  echo "Sending Slack Message"
  def header = env.JOB_NAME.replace('/', ': ')
  def payload = JsonOutput.toJson([
    attachments: [[
      fallback: header,
      text: header,
      color: color,
      fields:[[
        title: "#${env.BUILD_NUMBER} - ${title}",
        value: msg
      ]]
    ]]
  ])
  sh "curl -X POST --data-urlencode \'payload=${payload}\' ${config.SLACK_WEBHOOK_URL}"
  echo "Slack Message Sent"
}

/**
 * Find if the current commit is from jenkins
 * @return true when current commit is from jenkins
 */
Boolean isJenkinsCommit() {
  def commitEmail = sh(script: "git log -1 --pretty=format:'%ae'", returnStdout: true)?.trim()
  def scmVars = steps.checkout(globals.scm)
  return commitEmail == scmVars.GIT_AUTHOR_EMAIL
}

/**
 * Find if current branch is master
 * @return true when current branch is master
 */
Boolean isTrunk() {
  def branchName = scm.branches[0].name.tokenize('/').last()
  println ("Currnet branch: $branchName")
  return branchName == getConfig().get("TRUNK_NAME")
}

/**
 * Get the stack name
 * @param  env [environment name]
 * @return     [CFN stack name wrt environment name]
 */
String getStackName(String env) {
  return "${env}-${getRepoName()}"
}

/**
 * Read json file and return as String required by aws cli
 * @param  filename          [Name of the file]
 * @param  ['ParameterKey'   [Key ]
 * @param  'ParameterValue'] [Value]
 * @return                   [String of key values pairs]
 */
def paramsFromFile(String filename, keyPair = ['ParameterKey', 'ParameterValue']) {
  assert keyPair.size() == 2

  def paramsJson = readJSON(file: filename)

  paramsJson.collect { item ->
    keyPair.collect { key ->
      item.get(key)
    }.join('=')
  }.join(' ')

}

/**
 * get the name of the repository
 * @return [Name of the repository]
 */
String getRepoName() {
    return scm.getUserRemoteConfigs()[0].getUrl().tokenize('/').last().split("\\.")[0]
}


/**
 * Read project build and project configs
 * @return Map object with all configs
 */
Map getConfig() {
  def config = readProperties(file: 'jenkins/config.env')
  //assuming gradle project
  config = readProperties(defaults: config, file:'settings.gradle')
  config = readProperties(defaults: config, file:'gradle.properties')
  if(config.group != null) {
    config.groupLocation = config.group.replace(".", "/")
    // location in artifactory
    config.location = "${config.groupLocation}/${config.name}/${config.version}/${config.name}-${config.version}.jar"
  }
  return config
}

/**
 * Get artifact from jfrog which are published to libs-release-local
 * @param location location in jfrog
 * @param target target folder
 */
void copyArtifactFromJfrog(String location, String target) {
  rtDownload (
    serverId: "jfrog-artifactory",
    spec:
      """{
        "files": [{
            "pattern": "libs-release-local/${location}",
            "target": "${target}/"
          }]
      }"""
  )
}

/**
 * Find if a release candidate can be created for current state
 * @return true when releasable
 */
Boolean isReleasable(Map config) {
  return !isJenkinsCommit() && isTrunk() && (config.initialCommitHash == getCommitHash())
}

/**
 * Get the current commit hash
 * @return [description]
 */
String getCommitHash() {
  return sh(returnStdout: true, script: "git log -n 1 --pretty=format:'%h'").trim()
}

return this
