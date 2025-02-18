// Licensed to Elasticsearch B.V. under one or more contributor
// license agreements. See the NOTICE file distributed with
// this work for additional information regarding copyright
// ownership. Elasticsearch B.V. licenses this file to you under
// the Apache License, Version 2.0 (the "License"); you may
// not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import com.cloudbees.groovy.cps.NonCPS
import groovy.transform.Field

@Field def buildReasons = []

/**
* Given the YAML definition and the changeset global macros
* then it verifies if the project or stage should be enabled.
*/
Boolean call(Map args = [:]){
  def project = args.containsKey('project') ? args.project : error('beatsWhen: project parameter is required')
  def content = args.containsKey('content') ? args.content : error('beatsWhen: content parameter is required')
  def description = args.get('description', '')
  def ret = false

  markdownReason(project: project, reason: "## Build reasons for `${project} ${description}`")
  markdownReason(project: project, reason: "<details><summary>Expand to view the reasons</summary><p>\n")
  if (whenEnabled(args) || !isSkipCiBuildLabel(args)) {
    if (whenBranches(args)) { ret = true }
    if (whenChangeset(args)) { ret = true }
    if (whenComments(args)) { ret = true }
    if (whenLabels(args)) { ret = true }
    if (whenNotChangeset(args)) { ret = true }
    if (whenNotChangesetFullMatch(args)) { ret = true }
    if (whenParameters(args)) { ret = true }
    if (whenTags(args)) { ret = true }
  } else {
    markdownReason(project: args.project, reason: '* ❕Project is `disabled`.')
  }
  markdownReason(project: project, reason: "</p></details>")
  markdownReason(project: project, reason: "#### Stages for `${project} ${description}` have been ${ret ? '✅ enabled' : '❕disabled'}\n")
  flushBuildReason()
  return ret
}

private Boolean whenBranches(Map args = [:]) {
  if (isBranch() && args.content?.get('branches')) {
    markdownReason(project: args.project, reason: '* ✅ Branch is enabled .')
    return true
  }
  markdownReason(project: args.project, reason: '* ❗Branch is `disabled`.')
  return false
}

private Boolean whenChangeset(Map args = [:]) {
  def name = 'Changeset'
  if (args.content?.get('changeset')) {
    def arguments = args
    arguments.name = name
    return changeset(arguments)
  } else {
    markdownReason(project: args.project, reason: "* ${name} is `disabled`.")
  }
  return false
}

private Boolean changeset(Map args = [:]) {
  def name = args.get('name', 'Changeset')
  def partialMatch = args.get('partialMatch', true)
  // Gather macro changeset entries
  def macro = [:]
  args?.changeset?.each { k,v ->
    macro[k] = v
  }

  // Create list of changeset patterns to be searched.
  def patterns = []
  args.content.changeset.each {
    if (it.startsWith('@')){
      def search = it.replaceAll('@', '')
      macro[search].each { macroEntry ->
        patterns << macroEntry
      }
    } else {
      patterns << it
    }
  }

  // If function then calculate the project dependencies on the fly.
  if (args.get('changesetFunction')) {
    def changesetFunction = args.changesetFunction
    calculatedPatterns = changesetFunction.run(args)
    patterns.addAll(calculatedPatterns)

    // Search for some other project dependencies that are explicitly
    // sett with the pattern #<project-folder>
    args.content.changeset.findAll { it.startsWith('#') }.each {
      Map newArgs = args
      newArgs.project = it.replaceAll('#', '')
      calculatedPatterns = changesetFunction.run(args)
      patterns.addAll(calculatedPatterns)
    }
  }

  // TODO: to be refactored  with isGitRegionMatch.isPartialPatternMatch()

  // Gather the diff between the target branch and the current commit.
  def gitDiffFile = 'git-diff.txt'
  // On branches with a very first build then GIT_PREVIOUS_COMMIT is empty, let's fallback to the GIT_BASE_COMMIT
  def from = env.CHANGE_TARGET?.trim() ? "origin/${env.CHANGE_TARGET}" : "${env.GIT_PREVIOUS_COMMIT?.trim() ? env.GIT_PREVIOUS_COMMIT : env.GIT_BASE_COMMIT}"
  sh(script: "git diff --name-only ${from}...${env.GIT_BASE_COMMIT} > ${gitDiffFile}", returnStdout: true)

  // Search for any pattern that matches that particular if partialMatch or fullMatch
  def fileContent = readFile(gitDiffFile)

  def match = false
  if (partialMatch) {
    match = patterns?.find { pattern ->
      fileContent?.split('\n').any { isMatch(it, pattern) }
    }
  } else {
    match = patterns?.every { pattern ->
      fileContent?.split('\n').every { isMatch(it, pattern) }
    }
  }
  if (match) {
    markdownReason(project: args.project, reason: "* ✅ ${name} is `enabled` and matches with the pattern `${match}`.")
    return true
  } else {
    markdownReason(project: args.project, reason: "* ${name} is `enabled` and does **NOT** match with the pattern `${fileContent}`.")
  }
  return false
}

private Boolean whenComments(Map args = [:]) {
  if (args.content?.get('comments') && env.GITHUB_COMMENT?.trim()) {
    def match = args.content.get('comments').find { env.GITHUB_COMMENT?.toLowerCase()?.contains(it?.toLowerCase()) }
    if (match) {
      markdownReason(project: args.project, reason: "* ✅ Comment is `enabled` and matches with the pattern `${match}`.")
      return true
    }
    markdownReason(project: args.project, reason: "* ❕Comment is `enabled` and does **NOT** match with the pattern `${args.content.get('comments').toString()}`.")
  } else {
    markdownReason(project: args.project, reason: '* ❕Comment is `disabled`.')
  }
  return false
}

private boolean whenEnabled(Map args = [:]) {
  return !args.content?.get('disabled', false)
}

private Boolean whenLabels(Map args = [:]) {
  if (args.content?.get('labels')) {
    def match = args.content.get('labels').find { matchesPrLabel(label: it) }
    if (match) {
      markdownReason(project: args.project, reason: "* ✅ Label is `enabled` and matches with the pattern `${match}`.")
      return true
    }
    markdownReason(project: args.project, reason: "* ❕Label is `enabled` and does **NOT** match with the pattern `${args.content.get('labels').toString()}`.")
  } else {
    markdownReason(project: args.project, reason: '* ❕Label is `disabled`.')
  }
  return false
}

private Boolean whenNotChangeset(Map args = [:]) {
  def name = 'NotChangeset'
  if (args.content?.get('not_changeset')) {
    def arguments = args
    arguments.content.changeset = args.content.get('not_changeset')
    arguments.name = name
    arguments.content.remove('changesetFunction')
    return !whenChangeset(arguments)
  } else {
    markdownReason(project: args.project, reason: "* ❕${name} is `disabled`.")
  }
  return false
}

private Boolean whenNotChangesetFullMatch(Map args = [:]) {
  def name = 'NotChangesetFullMatch'
  if (args.content?.get('not_changeset_full_match')) {
    def arguments = args
    arguments.content.changeset = [ args.content.get('not_changeset_full_match') ]
    arguments.partialMatch = false
    arguments.name = name
    arguments.content.remove('changesetFunction')
    return !whenChangeset(arguments)
  } else {
    markdownReason(project: args.project, reason: "* ❕${name} is `disabled`.")
  }
  return false
}

private Boolean whenParameters(Map args = [:]) {
  if (args.content?.get('parameters')) {
    def match = args.content.get('parameters').find { params[it] }
    if (match) {
      markdownReason(project: args.project, reason: "* ✅ Parameter is `enabled` and matches with the pattern `${match}`.")
      return true
    } else {
      markdownReason(project: args.project, reason: "* ❕Parameter is `enabled` and does **NOT** match with the pattern `${args.content.get('parameters').toString()}`.")
    }
  } else {
    markdownReason(project: args.project, reason: '* ❕Parameter is `disabled`.')
  }
  return false
}

private Boolean whenTags(Map args = [:]) {
  if (env.TAG_NAME?.trim() && args.content?.get('tags')) {
    markdownReason(project: args.project, reason: '* ✅ Tag is `enabled`.')
    return true
  }
  markdownReason(project: args.project, reason: '* ❕Tag is `disabled`.')
  return false
}

private boolean isSkipCiBuildLabel(Map args = [:]) {
  def gitHubLabel = 'skip-ci-build'
  if (args.content?.get('skip-ci-build-label', false)) {
    if (matchesPrLabel(label: gitHubLabel) ) {
      markdownReason(project: args.project, reason: "* ✅ skip-ci-build-label is `enabled` and matches with the pattern `${gitHubLabel}`.")
      return true
    }
    markdownReason(project: args.project, reason: "* skip-ci-build-label is `enabled` and does **NOT** match with the pattern `${gitHubLabel}`.")
  } else {
    markdownReason(project: args.project, reason: '* ❕skip-ci-build-label label is `disabled`.')
  }
  return false
}

private void markdownReason(Map args = [:]) {
  buildReasons << args.reason
}

private void flushBuildReason() {
  dir('build-reasons') {
    def fileName = 'build.md'
    def data = ''
    if(fileExists(fileName)) {
      data = readFile(file: "${fileName}")
    }
    def content = "${data}\r\n${buildReasons.join('\n')}"
    writeFile(file: fileName, text: "${content}")
  }
}

@NonCPS
def isMatch(line, pattern) {
  def value = (line ==~ pattern)
  return value
}
