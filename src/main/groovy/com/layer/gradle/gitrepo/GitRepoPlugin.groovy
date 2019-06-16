package com.layer.gradle.gitrepo

import org.ajoberstar.grgit.Grgit
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task

/**
 * Use a (possibly private) github repo as a maven dependency.
 * Created by drapp on 7/16/14.
 */
class GitRepoPlugin implements Plugin<Project> {
    void apply(Project project) {

        project.extensions.create("gitPublishConfig", GitPublishConfig)

        // allow declaring special repositories
        if (!project.repositories.metaClass.respondsTo(project.repositories, 'github', String, String, String, String, Object)) {
            project.repositories.metaClass.github = { String org, String repo, String branch = "master", String type = "releases", def closure = null ->
                String gitUrl = githubCloneUrl(org, repo)
                def orgDir = repositoryDir(project, org)
                addLocalRepo(project, ensureLocalRepo(project, orgDir, repo, gitUrl, branch), type)
            }
        }
        if (!project.repositories.metaClass.respondsTo(project.repositories, 'git', String, String, String, String, Object)) {
            project.repositories.metaClass.git = { String gitUrl, String name, String branch = "master", String type = "releases", def closure = null ->
                def orgDir = repositoryDir(project, name)
                addLocalRepo(project, ensureLocalRepo(project, orgDir, name, gitUrl, branch), type)
            }
        }

        project.afterEvaluate {
            if (hasPublishTask(project)) {
                // add a publishToGithub task
                Task cloneRepo = project.tasks.create("cloneRepo")
                cloneRepo.doFirst {
                    GitPublishConfig cfg = project.gitPublishConfig
                    ensureLocalRepo(
                            project,
                            repositoryDir(project, cfg.org),
                            cfg.repo,
                            gitCloneUrl(project),
                            cfg.branch)
                }
                publishTask(project).dependsOn(cloneRepo)

                // push to remote repo after published
                publishTask(project).doLast {
                    GitPublishConfig cfg = project.gitPublishConfig
                    def gitDir = repositoryDir(project, "${cfg.org}/${cfg.repo}")
                    def gitRepo = Grgit.open(dir: gitDir)

                    gitRepo.add(patterns: ['.'])
                    gitRepo.commit(message: "published artifacts for ${project.group} ${project.version}")
                    gitRepo.push()
                }
            }
        }
    }

    private static boolean hasPublishTask(Project project) {
        project.tasks.any {
            it.name == project.gitPublishConfig.publishTask
        }
    }

    private static Task publishTask(Project project) {
        project.tasks.findByName(project.gitPublishConfig.publishTask)
    }

    private static File repositoryDir(Project project, String name) {
        def dir = project.hasProperty("gitRepoHome") ?
                project.property("gitRepoHome") : project.gitPublishConfig.home
        project.file("$dir/$name")
    }

    private static String githubCloneUrl(String org, String repo) {
        return "git@github.com:$org/${repo}.git"
    }

    private static String gitCloneUrl(Project project) {
        if (project.gitPublishConfig.gitUrl != "") {
            return project.gitPublishConfig.gitUrl
        } else {
            return "git@${project.gitPublishConfig.provider}:${project.gitPublishConfig.org}/${project.gitPublishConfig.repo}.git"
        }
    }

    private static File ensureLocalRepo(Project project, File directory, String name, String gitUrl, String branch) {
        def repoDir = new File(directory, name)
        def gitRepo
        if (repoDir.directory || project.hasProperty("offline")) {
            println("use local git-repo: $repoDir")
            gitRepo = Grgit.open(dir: repoDir)
        } else {
            println("clone git-repo $gitUrl to $repoDir")
            gitRepo = Grgit.clone(dir: repoDir, uri: gitUrl)
        }
        if (!project.hasProperty("offline")) {
            gitRepo.checkout(branch: branch)
            gitRepo.pull()
        }

        return repoDir
    }

    private static void addLocalRepo(Project project, File repoDir, String type) {
        project.repositories.maven {
            url repoDir.getAbsolutePath() + "/" + type
        }
    }

}

class GitPublishConfig {
    String org = ""
    String repo = ""
    String provider = "github.com" // github.com, gitlab or others
    String gitUrl = "" // used to replace git@${provider}:${org}/${repo}.git
    String branch = "master"
    String home = "${System.properties['user.home']}/.gitRepos"
//    String publishAndPushTask = "publishToGithub"
    String publishTask = "publish" // default publish tasks added by maven-publish plugin
}
